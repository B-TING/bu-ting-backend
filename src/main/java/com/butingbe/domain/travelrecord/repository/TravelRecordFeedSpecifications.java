package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 여행기 피드 조회 조건을 한 곳에 모은다.
 *
 * <p>예전에는 정렬 기준 3가지 × 커서 유무 2가지 = 같은 JPQL 6개가 복제돼 있었다. 55줄짜리 where 본문이 글자 하나까지 같아서, 조건 하나를 고치려면 6곳을
 * 같이 고쳐야 했다. 실제로 다른 것은 order by 컬럼과 커서 비교 대상뿐이다.
 */
public final class TravelRecordFeedSpecifications {

  private TravelRecordFeedSpecifications() {}

  /** 피드 검색 조건. 빈 값은 그 조건을 적용하지 않는다는 뜻이다. */
  public record FeedSearch(
      String keywordPattern,
      String placeId,
      String regionPattern,
      String cityPattern,
      LocalDate travelStartDate,
      LocalDate travelEndDate) {}

  /** 키셋 커서. {@code sortCount} 는 정렬이 좋아요·조회수일 때만 쓰인다. */
  public record FeedCursor(long sortCount, LocalDateTime publishedAt, LocalDateTime createdAt) {}

  /** 정렬 기준과, 그 기준이 쓰는 엔티티 속성 이름. */
  public enum Sorting {
    LATEST(null),
    MOST_LIKED("likeCount"),
    MOST_VIEWED("viewCount");

    private final String countAttribute;

    Sorting(String countAttribute) {
      this.countAttribute = countAttribute;
    }

    boolean usesCount() {
      return countAttribute != null;
    }
  }

  public static Specification<TravelRecord> publishedFeed(
      FeedSearch search, Sorting sorting, FeedCursor cursor) {
    return (root, query, cb) -> {
      // count 쿼리에는 fetch 를 걸 수 없다. 목록 조회에서만 작성자를 함께 가져와 N+1 을 막는다.
      if (query != null && !Long.class.equals(query.getResultType())) {
        root.fetch("author", JoinType.INNER);
      }
      Predicate predicate = cb.equal(root.get("status"), TravelRecordStatus.PUBLISHED);
      predicate = cb.and(predicate, searchPredicate(root, query, cb, search));
      predicate = cb.and(predicate, keysetPredicate(root, cb, sorting, cursor));
      return predicate;
    };
  }

  public static Sort order(Sorting sorting) {
    if (sorting.usesCount()) {
      return Sort.by(
          Sort.Order.desc(sorting.countAttribute),
          Sort.Order.desc("publishedAt"),
          Sort.Order.desc("createdAt"));
    }
    return Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("createdAt"));
  }

  private static Predicate searchPredicate(
      Root<TravelRecord> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      FeedSearch search) {
    Predicate predicate = cb.conjunction();

    if (hasText(search.keywordPattern())) {
      predicate =
          cb.and(
              predicate,
              cb.or(
                  like(cb, root.get("title"), search.keywordPattern()),
                  like(cb, coalesce(cb, root.get("content")), search.keywordPattern()),
                  placeExists(
                      root,
                      query,
                      cb,
                      (placeRoot, placeCb) ->
                          like(placeCb, placeRoot.get("placeName"), search.keywordPattern()))));
    }
    if (hasText(search.placeId())) {
      predicate =
          cb.and(
              predicate,
              placeExists(
                  root,
                  query,
                  cb,
                  (placeRoot, placeCb) ->
                      placeCb.equal(placeRoot.get("providerPlaceId"), search.placeId())));
    }
    if (hasText(search.regionPattern())) {
      predicate = cb.and(predicate, placeTextMatches(root, query, cb, search.regionPattern()));
    }
    if (hasText(search.cityPattern())) {
      predicate = cb.and(predicate, placeTextMatches(root, query, cb, search.cityPattern()));
    }
    if (search.travelStartDate() != null) {
      predicate =
          cb.and(
              predicate,
              cb.greaterThanOrEqualTo(root.get("travelEndDate"), search.travelStartDate()));
    }
    if (search.travelEndDate() != null) {
      predicate =
          cb.and(
              predicate, cb.lessThanOrEqualTo(root.get("travelStartDate"), search.travelEndDate()));
    }
    return predicate;
  }

  /** 장소명 또는 주소가 걸리면 참. 지역·도시 조건이 같은 모양이라 함께 쓴다. */
  private static Predicate placeTextMatches(
      Root<TravelRecord> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      String pattern) {
    return placeExists(
        root,
        query,
        cb,
        (placeRoot, placeCb) ->
            placeCb.or(
                like(placeCb, placeRoot.get("placeName"), pattern),
                like(placeCb, coalesce(placeCb, placeRoot.get("address")), pattern)));
  }

  private interface PlaceCondition {
    Predicate build(Root<TravelRecordPlace> placeRoot, CriteriaBuilder cb);
  }

  /** 이 여행기에 속한 장소 중 조건을 만족하는 것이 있으면 참. JPQL 의 exists 서브쿼리와 같다. */
  private static Predicate placeExists(
      Root<TravelRecord> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      PlaceCondition condition) {
    Subquery<Integer> subquery = query.subquery(Integer.class);
    Root<TravelRecordPlace> placeRoot = subquery.from(TravelRecordPlace.class);
    jakarta.persistence.criteria.Join<TravelRecordPlace, TravelRecordDay> day =
        placeRoot.join("travelRecordDay");
    return cb.exists(
        subquery
            .select(cb.literal(1))
            .where(
                cb.and(cb.equal(day.get("travelRecord"), root), condition.build(placeRoot, cb))));
  }

  private static Predicate keysetPredicate(
      Root<TravelRecord> root, CriteriaBuilder cb, Sorting sorting, FeedCursor cursor) {
    if (cursor == null) {
      return cb.conjunction();
    }
    Predicate tieBreak = publishedAtTieBreak(root, cb, cursor);
    if (!sorting.usesCount()) {
      return tieBreak;
    }
    Expression<Long> countAttribute = root.get(sorting.countAttribute);
    return cb.or(
        cb.lessThan(countAttribute, cursor.sortCount()),
        cb.and(cb.equal(countAttribute, cursor.sortCount()), tieBreak));
  }

  private static Predicate publishedAtTieBreak(
      Root<TravelRecord> root, CriteriaBuilder cb, FeedCursor cursor) {
    Expression<LocalDateTime> publishedAt = root.get("publishedAt");
    return cb.or(
        cb.lessThan(publishedAt, cursor.publishedAt()),
        cb.and(
            cb.equal(publishedAt, cursor.publishedAt()),
            cb.lessThan(root.get("createdAt"), cursor.createdAt())));
  }

  private static Predicate like(CriteriaBuilder cb, Expression<String> value, String pattern) {
    return cb.like(cb.lower(value), pattern);
  }

  /** 본문과 주소는 null 일 수 있다. JPQL 이 coalesce(..., '') 를 쓰던 것과 같게 맞춘다. */
  private static Expression<String> coalesce(CriteriaBuilder cb, Expression<String> value) {
    return cb.coalesce(value, "");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
