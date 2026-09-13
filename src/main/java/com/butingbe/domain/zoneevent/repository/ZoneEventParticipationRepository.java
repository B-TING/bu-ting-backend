package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventParticipationRepository
    extends JpaRepository<ZoneEventParticipation, UUID>,
        JpaSpecificationExecutor<ZoneEventParticipation> {

  List<ZoneEventParticipation> findByEvent_IdAndUserIdOrderByJoinedAtDesc(
      UUID eventId, UUID userId);

  /** 이 이벤트에 대한 내 최근 참여 1건. 전체 목록을 읽어 첫 원소만 쓰는 대신 DB에서 1건만 가져온다. */
  Optional<ZoneEventParticipation> findFirstByEvent_IdAndUserIdOrderByJoinedAtDesc(
      UUID eventId, UUID userId);

  Optional<ZoneEventParticipation> findByEvent_IdAndUserIdAndStatusIn(
      UUID eventId, UUID userId, Collection<ParticipationStatus> statuses);

  long countByEvent_IdAndUserIdAndStatus(UUID eventId, UUID userId, ParticipationStatus status);

  long countByEvent_Id(UUID eventId);

  long countByEvent_IdAndStatus(UUID eventId, ParticipationStatus status);

  List<ZoneEventParticipation> findByEvent_IdAndStatusIn(
      UUID eventId, java.util.Collection<ParticipationStatus> statuses);

  @Query(
      "SELECT COUNT(p) FROM ZoneEventParticipation p "
          + "WHERE p.userId = :userId AND p.event.zoneId = :zoneId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS")
  long countSuccessByUserAndZone(@Param("userId") UUID userId, @Param("zoneId") String zoneId);

  @Query(
      "SELECT p FROM ZoneEventParticipation p WHERE p.event.id = :eventId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS "
          + "AND p.visibility = com.butingbe.domain.zoneevent.entity.ParticipationVisibility.PUBLIC "
          + "AND p.hidden = false ORDER BY p.likeCount DESC, p.completedAt ASC")
  List<ZoneEventParticipation> findTopPublicSuccessByEvent(
      @Param("eventId") UUID eventId, org.springframework.data.domain.Pageable pageable);

  /**
   * 마감 순위 스냅샷 대상 전체 — 신고 누적으로 자동 숨김된 참여({@code hidden = true})도 포함한다.
   *
   * <p>숨김 참여를 제외하면 마감 시점에 스냅샷 행 자체가 생기지 않아, 나중에 신고가 기각(숨김 해제)되어도 수상자로 확정할 수 없고 컷오프도 조용히 밀린다. 보류 여부는
   * 스냅샷이 아니라 신고 상태(OPEN/REVIEWING)로 조회·확정 시점에 판단한다.
   */
  @Query(
      "SELECT p FROM ZoneEventParticipation p WHERE p.event.id = :eventId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS "
          + "AND p.visibility = com.butingbe.domain.zoneevent.entity.ParticipationVisibility.PUBLIC "
          + "ORDER BY p.likeCount DESC, p.completedAt ASC")
  List<ZoneEventParticipation> findRankedPublicSuccessByEvent(@Param("eventId") UUID eventId);

  @Query(
      "SELECT DISTINCT p.userId FROM ZoneEventParticipation p WHERE p.event.zoneId = :zoneId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS")
  List<UUID> findDistinctSuccessUserIdsByZone(@Param("zoneId") String zoneId);

  long countByEvent_IdAndCurrentSubmissionIdIsNotNull(UUID eventId);
}
