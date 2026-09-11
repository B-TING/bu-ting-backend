# 칭호 정의/발급 현황, 운영 통계, 감사 이력 조회 API (이슈 #245) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 페이지의 "구역 칭호" 화면(정의 CRUD·보유자 조회), "운영 리포트" 화면(회차·슬롯별 운영 통계), 감사 이력 조회를 지원하는 admin API를 추가하고, #236~#244에서 빠진 운영자 액션의 감사 로그 기록을 보완한다.

**Architecture:** 칭호 정의는 기존 `ZoneTitleDef`/`UserZoneTitle`(`domain.zonetitle`)에 UK 제약과 소급 발급 로직을 얹어 `AdminZoneTitleController`/`Service`로 노출한다. 운영 통계는 `ZoneEventRound`→`ZoneEventRoundSlot`(슬롯)→`ZoneEvent` 순으로 순회하며 참여·제출·신고·지급·칭호 리포지토리에 새 count 쿼리를 추가해 `AdminZoneEventStatsController`/`Service`(zoneevent 패키지)로 합산 응답한다. 감사 이력은 기존 `ZoneEventAuditLog`/`ZoneEventAuditLogRepository`에 페이징 검색 쿼리를 추가해 `AdminZoneEventAuditController`/`Service`로 노출하고, 같은 리포지토리에 대한 `save()` 호출이 빠져 있던 6개 기존 운영자 액션(`AdminReviewService.revoke/unhide`, `AdminZoneEventReviewService.approve/reject`, `AdminRewardCatalogService.create/update`)에 호출을 보강한다.

**Tech Stack:** Java 21 / Spring Boot 4 (Jackson 3 — `tools.jackson.*`) / Spring Data JPA / PostgreSQL + Flyway / JUnit 5 + Mockito + Testcontainers(`AbstractContainerTest`).

## Global Constraints

- **Branch:** `feature/245-zone-title-admin-stats-audit`, `origin/dev`(`6345934`, PR #256 머지 후)에서 분기해 `C:\dev\bu-ting-backend-245`(ASCII 경로 워크트리)에 이미 만들어져 있다. 이 워크트리에서 계속 작업한다.
- **로컬 테스트는 ASCII 경로에서만 정상 동작한다**(한글 경로 Gradle 워커 JVM 문제). 이 워크트리 자체가 이미 ASCII 경로이므로 `cd /c/dev/bu-ting-backend-245` 후 `./gradlew test --no-daemon -g "C:\gradle-home" --tests "..."`로 실행한다. **매 태스크마다 `spotlessApply` 후 최소 `test`, 마지막 태스크에서 `check`**(Spotless + Jacoco 100% 라인 커버리지 강제)까지 확인한다.
- **모든 신규 admin 엔드포인트는 `OperatorAuthorization.requireOperator(user)`를 서비스 진입점에서 즉시 호출한다**(401/403은 기존 컴포넌트가 처리).
- **Idempotency-Key는 이번 이슈의 신규 엔드포인트에 적용하지 않는다.** 칭호 정의 CRUD는 `AdminZoneEventTargetService`/`AdminZoneEventService`(설정성 admin 변경)와 같은 카테고리이고, 그 두 서비스도 Idempotency-Key를 쓰지 않는다 — 결제성 지급(`AdminRewardPayoutService`)과는 다른 카테고리로 분류한다.
- **`expectedRevision` 이중 방어**: PATCH 진입 직후 `!entity.getRevision().equals(expectedRevision)`이면 즉시 `ConflictException`. 이후 엔티티를 변경하고 `repository.saveAndFlush(entity)`를 `try/catch(ObjectOptimisticLockingFailureException)`로 감싸 같은 예외를 던진다(기존 report/payout 패턴과 동일).
- **감사 로그 저장은 `ZoneEventAuditLogRepository.save(ZoneEventAuditLog.builder().actorId(user.id()).action("...").targetType("...").targetId(id).detail(Map.of(...)).build())` 헬퍼 메서드(`private void audit(...)`)로 각 서비스에 둔다**(`AdminZoneEventTargetService.audit(...)` 패턴을 그대로 따른다). 상태를 바꾸는 액션은 가능하면 `detail`에 `before`/`after` 스냅샷 맵을 넣는다.
- **감사 통합 점검 범위 결정(스코프 제한)**: #236~#244 액션 중 실제로 `ZoneEventAuditLogRepository.save(...)`를 호출하지 않는 곳은 6곳뿐이다(Task 6·7·8에서 수정). 이미 호출하고 있는 12곳 이상(`AdminZoneEventReportService`/`AdminRewardPayoutService`/`AdminZoneEventService`/`AdminZoneEventTargetService`/`AdminRoundConsoleService`/`AdminZoneEventWinnerService`/`RewardPayoutService`)은 이번 이슈에서 손대지 않는다 — 그중 일부(예: `PATCH_TARGET`만)만 진짜 `before`/`after`를 남기고 나머지는 새 값 조각만 남기지만, 기존 병합 PR의 동작을 바꾸는 건 이슈 범위 밖이다. 새로 추가하는 6곳은 전부 `before`/`after`(또는 최소한 상태 전이가 뻔한 경우 하드코딩된 이전/이후 상태)를 남긴다.
- **`AdminReviewService.revoke`/`unhide`, `AdminZoneEventReviewService.approve`는 현재 API 계약에 "사유(reason)" 파라미터가 없다.** 이번 이슈에서 그 두 엔드포인트의 요청 바디를 바꾸는 것은 범위 밖이므로, 감사 로그에는 actor + before/after 상태만 남기고 `reason`은 없는 채로 둔다(`reject`는 이미 `request.reason()`이 있으므로 그대로 기록).
- 칭호 정의 `titleCode`는 기존 시드 컨벤션(`{zoneId}_T{tier}`, 예: `SUYEONG_NAMGU_T1`)을 그대로 따른다.
- **"칭호 발급은 승인 성공 누적 기준으로만 트리거되고 기본/특별 보상 지급과 독립적으로 동작"(이슈 요구사항)은 이미 기존 코드로 충족되어 있다** — `ZoneTitleService.awardTitles`/`backfillGrants` 모두 `RewardPayout`/`BaseRewardPayout`을 전혀 참조하지 않고 오직 `ZoneEventParticipationRepository.countSuccessByUserAndZone`(성공 참여 누적)만 본다. 별도 태스크 없음 — 확인만 하고 넘어간다.
- Jackson 3: 새 코드가 `ObjectMapper`/Jackson 예외를 다루면 `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException`만 사용한다(이번 계획엔 JSON 직렬화가 필요한 태스크가 없으므로 해당 없음).
- Korean 사용자 문구는 기존 `ZoneTitleService`/`AdminZoneEventReportService`의 어조를 그대로 따른다.
- 메시지 키는 4개 로케일 파일(`messages.properties`, `messages_en.properties`, `messages_ja.properties`, `messages_zh.properties`, 현재 모두 정확히 72줄)에 **같은 순서로** 73번째 줄부터 이어 붙인다.
- 다음 Flyway 마이그레이션 번호는 **`V48`**(`V47__reward_payout_memo.sql`이 최신).
- `TimestampEntity.getCreatedAt()`/`getUpdatedAt()`은 `LocalDateTime`(다른 필드들과 타입이 다름 — DTO에 그대로 반영). `BaseEntity`(라운드/이벤트가 상속)도 `OffsetDateTime`이 아니라 `LocalDateTime` 계열이면 그에 맞춘다 — 이번 계획에서 라운드/이벤트의 createdAt/updatedAt을 DTO로 내보내지 않으므로 직접 영향은 없다.
- 매 태스크 끝에서 새/수정 파일만 `git add`, 커밋 메시지는 `feat(zonetitle): ...` 또는 `feat(zoneevent): ...` 형식(도메인에 맞춰).
- **테스트 프로파일은 Flyway를 쓰지 않는다**(`ddl-auto=create-drop`, `AbstractContainerTest`). 즉 Task 1의 UK 제약·인덱스는 마이그레이션 SQL과 별개로 **엔티티의 `@UniqueConstraint`/JPA 매핑에도 반영해야** 테스트에서 실제로 검증된다.

---

## File Structure

**New files:**
- `src/main/resources/db/migration/V48__zone_title_def_uk_and_audit_log_indexes.sql`
- `src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleCreateReqDto.java`
- `src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleUpdateReqDto.java`
- `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleDefResDto.java`
- `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderItemResDto.java`
- `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderPageResDto.java`
- `src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java`
- `src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsItemResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsService.java`
- `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsController.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditItemResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditPageResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventAuditService.java`
- `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditController.java`
- Tests mirroring each task under `src/test/java/...` (see per-task lists).

**Modified files:**
- `src/main/java/com/butingbe/domain/zonetitle/entity/ZoneTitleDef.java` — UK 제약 + `applyEditable(titleName, requiredSuccessCount)`.
- `src/main/java/com/butingbe/domain/zonetitle/repository/ZoneTitleDefRepository.java` — UK 검증용 `existsBy...` 4종.
- `src/main/java/com/butingbe/domain/zonetitle/repository/UserZoneTitleRepository.java` — `countByTitleDef_Id`, `findByTitleDef_Id`(paged), `countByZoneIdAndEarnedAtBetween`.
- `src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java` — `backfillGrants(ZoneTitleDef)`.
- `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java` — `countByEvent_IdAndCurrentSubmissionIdIsNotNull`, `findDistinctSuccessUserIdsByZone`.
- `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventSubmissionRepository.java` — `countByParticipation_Event_Id`.
- `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventReportRepository.java` — `countByEventIdAndStatusIn` + default `countUnresolvedByEventId`.
- `src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java` — `countByEventIdAndStatus`(JPQL join).
- `src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java` — `countByEventIdAndStatus`(derived).
- `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuditLogRepository.java` — `searchForAdmin(...)`.
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java` — `revoke`/`unhide`에 감사 로그.
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java` — `approve`/`reject`에 감사 로그.
- `src/main/java/com/butingbe/domain/reward/service/AdminRewardCatalogService.java` — `create`/`update`에 감사 로그.
- `src/main/resources/messages*.properties`(4개) — 신규 키 7개.
- `src/main/resources/static/docs/openapi3.yaml` — 6개 엔드포인트 + 스키마(Task 11).
- 기존 테스트: `AdminReviewServiceTest`, `AdminZoneEventReviewServiceTest`, `AdminRewardCatalogServiceTest`(감사 로그 단언 추가).

---

## Task 1: 칭호 정의 UK 제약 + 리포지토리·서비스 기반 정비

**Files:**
- Create: `src/main/resources/db/migration/V48__zone_title_def_uk_and_audit_log_indexes.sql`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/entity/ZoneTitleDef.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/repository/ZoneTitleDefRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/repository/UserZoneTitleRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java`
- Test: `src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java`(확장)

**Interfaces:**
- Produces: `ZoneTitleDef.applyEditable(String titleName, Integer requiredSuccessCount)`(null은 미변경), `ZoneTitleDefRepository.existsByZoneIdAndTier/AndIdNot`, `existsByZoneIdAndRequiredSuccessCount/AndIdNot`, `UserZoneTitleRepository.countByTitleDef_Id`, `findByTitleDef_Id(UUID, Pageable)`, `countByZoneIdAndEarnedAtBetween`, `ZoneTitleService.backfillGrants(ZoneTitleDef def) -> int`(신규 소급 발급 건수), `ZoneEventParticipationRepository.findDistinctSuccessUserIdsByZone(String) -> List<UUID>`.
- Consumes: 없음(기반 태스크).

- [ ] **Step 1: 마이그레이션 작성**

`V48__zone_title_def_uk_and_audit_log_indexes.sql`:
```sql
ALTER TABLE zone_title_def ADD CONSTRAINT uk_zone_title_def_zone_tier UNIQUE (zone_id, tier);
ALTER TABLE zone_title_def ADD CONSTRAINT uk_zone_title_def_zone_required_count UNIQUE (zone_id, required_success_count);
CREATE INDEX idx_zone_event_audit_log_actor ON zone_event_audit_log (actor_id, created_at);
CREATE INDEX idx_zone_event_audit_log_action ON zone_event_audit_log (action, created_at);
```

- [ ] **Step 2: `ZoneTitleDef`에 UK 제약과 `applyEditable` 추가**

`@Table` 애노테이션을 다음으로 교체(파일 21-70번째 줄 근처):
```java
@Entity
@Table(
    name = "zone_title_def",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_title_def_zone_tier",
          columnNames = {"zone_id", "tier"}),
      @UniqueConstraint(
          name = "uk_zone_title_def_zone_required_count",
          columnNames = {"zone_id", "required_success_count"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneTitleDef extends TimestampEntity {
```
`import jakarta.persistence.UniqueConstraint;`을 import 블록에 추가(`jakarta.persistence.Table` 다음 줄).

클래스 하단(생성자 다음)에 메서드 추가:
```java

  /** 이름·달성 기준을 수정한다. null은 건너뛴다. */
  public void applyEditable(String titleName, Integer requiredSuccessCount) {
    if (titleName != null) {
      this.titleName = titleName;
    }
    if (requiredSuccessCount != null) {
      this.requiredSuccessCount = requiredSuccessCount;
    }
  }
```

- [ ] **Step 3: `ZoneTitleDefRepository`에 UK 검증 메서드 추가**

전체 파일을 다음으로 교체:
```java
package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneTitleDefRepository extends JpaRepository<ZoneTitleDef, UUID> {

  List<ZoneTitleDef> findByZoneIdOrderByTierAsc(String zoneId);

  List<ZoneTitleDef> findAllByOrderByZoneIdAscTierAsc();

  boolean existsByZoneIdAndTier(String zoneId, Integer tier);

  boolean existsByZoneIdAndTierAndIdNot(String zoneId, Integer tier, UUID id);

  boolean existsByZoneIdAndRequiredSuccessCount(String zoneId, Integer requiredSuccessCount);

  boolean existsByZoneIdAndRequiredSuccessCountAndIdNot(
      String zoneId, Integer requiredSuccessCount, UUID id);
}
```

- [ ] **Step 4: `UserZoneTitleRepository`에 보유자·소급 조회 메서드 추가**

전체 파일을 다음으로 교체:
```java
package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserZoneTitleRepository extends JpaRepository<UserZoneTitle, UUID> {

  boolean existsByUserIdAndTitleDef_Id(UUID userId, UUID titleDefId);

  List<UserZoneTitle> findByUserIdAndRevokedAtIsNull(UUID userId);

  Optional<UserZoneTitle> findByUserIdAndEquippedIsTrue(UUID userId);

  List<UserZoneTitle> findByUserIdInAndEquippedIsTrue(Collection<UUID> userIds);

  long countByUserIdAndEquippedIsTrue(UUID userId);

  long countByTitleDef_Id(UUID titleDefId);

  Page<UserZoneTitle> findByTitleDef_Id(UUID titleDefId, Pageable pageable);

  long countByZoneIdAndEarnedAtBetween(String zoneId, OffsetDateTime from, OffsetDateTime to);
}
```

- [ ] **Step 5: `ZoneEventParticipationRepository`에 소급 발급 대상 조회 메서드 추가**

`ZoneEventParticipationRepository.java` 마지막 메서드(`findRankedPublicSuccessByEvent`) 다음, 닫는 중괄호 앞에 추가:
```java

  @Query(
      "SELECT DISTINCT p.userId FROM ZoneEventParticipation p WHERE p.event.zoneId = :zoneId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS")
  List<UUID> findDistinctSuccessUserIdsByZone(@Param("zoneId") String zoneId);
```
(이미 `@Query`/`@Param`/`List`/`UUID` import는 있음.)

- [ ] **Step 6: `ZoneTitleService`에 `backfillGrants` 추가**

`ZoneTitleService.java`의 `awardTitles(userId, zoneId, autoEquip)` 메서드 다음에 추가:
```java

  /**
   * 요건(달성 기준)이 낮아진 뒤 이미 조건을 충족한 유저에게 소급 발급한다. 자동 장착은 하지 않는다(운영자 액션이 대표 칭호를 바꾸면 안 된다). 새로 발급된 건수를
   * 돌려준다.
   */
  @Transactional
  public int backfillGrants(ZoneTitleDef def) {
    int granted = 0;
    for (UUID userId : participationRepository.findDistinctSuccessUserIdsByZone(def.getZoneId())) {
      if (userZoneTitleRepository.existsByUserIdAndTitleDef_Id(userId, def.getId())) {
        continue;
      }
      long successCount = participationRepository.countSuccessByUserAndZone(userId, def.getZoneId());
      if (successCount >= def.getRequiredSuccessCount()) {
        userZoneTitleRepository.save(
            UserZoneTitle.builder()
                .userId(userId)
                .titleDef(def)
                .zoneId(def.getZoneId())
                .equipped(false)
                .build());
        granted++;
      }
    }
    if (granted > 0) {
      // 소급 발급도 등급(도시 등급) 재계산 대상이다 — 개별 유저 단위 recordIfRisen 호출은
      // 대량 처리 시 비용이 크므로 이번 이슈 범위에서는 건너뛴다(운영 정책 확정 후 별도 이슈에서 처리).
    }
    return granted;
  }
```

- [ ] **Step 7: 테스트 실행해 컴파일·기존 테스트 통과 확인**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*ZoneTitleServiceTest" --tests "*ZoneTitleControllerTest"
```
Expected: BUILD SUCCESSFUL, 기존 테스트 전부 PASS(새 메서드는 아직 테스트가 없어도 컴파일만 되면 됨).

- [ ] **Step 8: `backfillGrants` 테스트 추가**

`ZoneTitleServiceTest.java`에 다음 테스트 추가(기존 테스트 구조·픽스처 헬퍼를 그대로 활용 — 해당 파일의 `savedUser(...)`/`successfulParticipation(...)`류 헬퍼가 있으면 재사용하고, 없으면 `ZoneEventParticipationRepository`/`ZoneEventRepository`/`ZoneEventTypeRepository`로 직접 SUCCESS 참여를 만든다):
```java
  @Test
  @DisplayName("backfillGrants: 요건을 낮춘 뒤 이미 충족한 유저에게 소급 발급하고 자동 장착하지 않는다")
  void backfillGrantsAwardsQualifyingUsersWithoutAutoEquip() {
    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode("SUYEONG_NAMGU_T1")
                .zoneId("SUYEONG_NAMGU")
                .tier(1)
                .requiredSuccessCount(1)
                .titleName("탐방가")
                .style("chip")
                .color("#000000")
                .build());
    var user = savedUser("유저");
    successfulParticipation(user.getId(), "SUYEONG_NAMGU");

    int granted = zoneTitleService.backfillGrants(def);

    assertThat(granted).isEqualTo(1);
    var title = userZoneTitleRepository.findByUserIdAndRevokedAtIsNull(user.getId()).get(0);
    assertThat(title.getEquipped()).isFalse();
  }

  @Test
  @DisplayName("backfillGrants: 이미 보유한 유저는 다시 발급하지 않는다(멱등)")
  void backfillGrantsIsIdempotent() {
    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode("SUYEONG_NAMGU_T1")
                .zoneId("SUYEONG_NAMGU")
                .tier(1)
                .requiredSuccessCount(1)
                .titleName("탐방가")
                .style("chip")
                .color("#000000")
                .build());
    var user = savedUser("유저");
    successfulParticipation(user.getId(), "SUYEONG_NAMGU");
    zoneTitleService.backfillGrants(def);

    int secondRun = zoneTitleService.backfillGrants(def);

    assertThat(secondRun).isZero();
    assertThat(userZoneTitleRepository.countByTitleDef_Id(def.getId())).isEqualTo(1);
  }
```
`savedUser(String nickname)`/`successfulParticipation(UUID userId, String zoneId)` 헬퍼가 파일에 이미 있는지 먼저 `grep -n "private.*savedUser\|private.*successfulParticipation" src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java`로 확인한다. 없으면 다음 헬퍼를 클래스 하단에 추가(기존 `@Autowired` 필드로 `ZoneEventParticipationRepository participationRepository`, `ZoneEventRepository zoneEventRepository`, `ZoneEventTypeRepository zoneEventTypeRepository`, `UserRepository userRepository`가 이미 있는지도 같은 grep으로 확인하고 없으면 추가):
```java
  private User savedUser(String nickname) {
    return userRepository.save(
        User.builder()
            .email(nickname + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", nickname))
            .nickname(nickname)
            .role(UserRole.USER)
            .build());
  }

  private void successfulParticipation(UUID userId, String zoneId) {
    ZoneEventType type =
        zoneEventTypeRepository
            .findAll()
            .stream()
            .findFirst()
            .orElseGet(
                () ->
                    zoneEventTypeRepository.save(
                        ZoneEventType.builder()
                            .typeCode("PLACE_AUTH")
                            .name("장소 인증")
                            .requiresUpload(true)
                            .build()));
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(userId)
                .status(ParticipationStatus.SUCCESS)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .build());
  }
```
필요한 import(파일 상단에 없으면 추가): `com.butingbe.domain.user.entity.Name`, `com.butingbe.domain.user.entity.User`, `com.butingbe.domain.user.entity.UserRole`, `com.butingbe.domain.user.repository.UserRepository`, `com.butingbe.domain.zoneevent.entity.ParticipationStatus`, `com.butingbe.domain.zoneevent.entity.ZoneEvent`, `com.butingbe.domain.zoneevent.entity.ZoneEventParticipation`, `com.butingbe.domain.zoneevent.entity.ZoneEventStatus`, `com.butingbe.domain.zoneevent.entity.ZoneEventType`, `com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository`, `com.butingbe.domain.zoneevent.repository.ZoneEventRepository`, `com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository`, `java.time.OffsetDateTime`, `java.util.UUID`.

- [ ] **Step 9: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*ZoneTitleServiceTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: 커밋**

```bash
git add src/main/resources/db/migration/V48__zone_title_def_uk_and_audit_log_indexes.sql src/main/java/com/butingbe/domain/zonetitle/entity/ZoneTitleDef.java src/main/java/com/butingbe/domain/zonetitle/repository/ZoneTitleDefRepository.java src/main/java/com/butingbe/domain/zonetitle/repository/UserZoneTitleRepository.java src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java
git commit -m "feat(zonetitle): zone title def UK constraints and retroactive backfill"
```

---

## Task 2: 메시지 키 + `GET/POST /admin/zone-titles`(목록·생성)

**Files:**
- Modify: `src/main/resources/messages.properties`, `messages_en.properties`, `messages_ja.properties`, `messages_zh.properties`
- Create: `src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleCreateReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleDefResDto.java`
- Create: `src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java`
- Create: `src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java`
- Test: `src/test/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleServiceTest.java`(신규)
- Test: `src/test/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleControllerTest.java`(신규)

**Interfaces:**
- Produces: `AdminZoneTitleService.list(AuthenticatedUser) -> List<AdminZoneTitleDefResDto>`, `AdminZoneTitleService.create(AuthenticatedUser, AdminZoneTitleCreateReqDto) -> AdminZoneTitleDefResDto`, `AdminZoneTitleDefResDto.of(ZoneTitleDef, long holderCount)`.
- Consumes: Task 1의 `ZoneTitleDefRepository.existsByZoneIdAndTier/existsByZoneIdAndRequiredSuccessCount`, `UserZoneTitleRepository.countByTitleDef_Id`, `ChatZone.fromString(String) -> ChatZone`(기존).

- [ ] **Step 1: 메시지 키 7개를 4개 파일 73번째 줄부터 이어 붙인다**

`messages.properties`:
```properties
error.zone_title.def_not_found=칭호 정의를 찾을 수 없습니다.
error.zone_title.duplicate_tier=이미 같은 구역·단계의 칭호 정의가 있습니다.
error.zone_title.duplicate_required_success_count=이미 같은 구역·달성 기준의 칭호 정의가 있습니다.
error.zone_title.invalid_required_success_count=달성 기준은 양의 정수이며, 같은 구역 내 단계 순서를 따라 증가해야 합니다.
error.zone_title.stale_revision=다른 처리로 칭호 정의가 변경되었습니다. 다시 시도해 주세요.
error.zone_title.has_holders=보유자가 있는 칭호 정의는 삭제할 수 없습니다.
error.zone_event.stats.round_or_range_required=roundId 또는 from/to 중 하나는 반드시 지정해야 합니다.
```

`messages_en.properties`:
```properties
error.zone_title.def_not_found=The title definition could not be found.
error.zone_title.duplicate_tier=A title definition for this zone and tier already exists.
error.zone_title.duplicate_required_success_count=A title definition for this zone with the same required success count already exists.
error.zone_title.invalid_required_success_count=The required success count must be a positive integer that increases with tier within the same zone.
error.zone_title.stale_revision=The title definition was changed by another operation. Please try again.
error.zone_title.has_holders=A title definition with existing holders can't be deleted.
error.zone_event.stats.round_or_range_required=Either roundId or both from and to must be specified.
```

`messages_ja.properties`:
```properties
error.zone_title.def_not_found=称号定義が見つかりません。
error.zone_title.duplicate_tier=同じ区域・段階の称号定義がすでに存在します。
error.zone_title.duplicate_required_success_count=同じ区域・達成基準の称号定義がすでに存在します。
error.zone_title.invalid_required_success_count=達成基準は正の整数であり、同じ区域内で段階順に増加している必要があります。
error.zone_title.stale_revision=他の処理で称号定義が変更されました。もう一度お試しください。
error.zone_title.has_holders=保有者がいる称号定義は削除できません。
error.zone_event.stats.round_or_range_required=roundIdまたはfrom/toのいずれかを必ず指定してください。
```

`messages_zh.properties`:
```properties
error.zone_title.def_not_found=未找到称号定义。
error.zone_title.duplicate_tier=该区域和等级的称号定义已存在。
error.zone_title.duplicate_required_success_count=该区域中已存在相同达成次数的称号定义。
error.zone_title.invalid_required_success_count=达成次数必须是正整数,且在同一区域内随等级递增。
error.zone_title.stale_revision=称号定义已被其他操作更改,请重试。
error.zone_title.has_holders=已有持有者的称号定义无法删除。
error.zone_event.stats.round_or_range_required=必须指定roundId,或同时指定from和to。
```

- [ ] **Step 2: 요청/응답 DTO 작성**

`AdminZoneTitleCreateReqDto.java`:
```java
package com.butingbe.domain.zonetitle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminZoneTitleCreateReqDto(
    @NotBlank String zoneId,
    @NotNull @Positive Integer tier,
    @NotNull @Positive Integer requiredSuccessCount,
    @NotBlank String titleName,
    @NotBlank String style,
    @NotBlank String color) {}
```

`AdminZoneTitleDefResDto.java`:
```java
package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import java.time.LocalDateTime;

public record AdminZoneTitleDefResDto(
    String titleDefId,
    String titleCode,
    String zoneId,
    Integer tier,
    Integer requiredSuccessCount,
    String titleName,
    String style,
    String color,
    long holderCount,
    Long revision,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminZoneTitleDefResDto of(ZoneTitleDef def, long holderCount) {
    return new AdminZoneTitleDefResDto(
        def.getId().toString(),
        def.getTitleCode(),
        def.getZoneId(),
        def.getTier(),
        def.getRequiredSuccessCount(),
        def.getTitleName(),
        def.getStyle(),
        def.getColor(),
        holderCount,
        def.getRevision(),
        def.getCreatedAt(),
        def.getUpdatedAt());
  }
}
```

- [ ] **Step 3: `AdminZoneTitleService` 작성(list + create)**

```java
package com.butingbe.domain.zonetitle.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.global.error.exception.ConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구역 칭호 정의 관리(CRUD)와 보유자 조회. ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneTitleService {

  private final ZoneTitleDefRepository titleDefRepository;
  private final UserZoneTitleRepository userZoneTitleRepository;
  private final ZoneTitleService zoneTitleService;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public List<AdminZoneTitleDefResDto> list(AuthenticatedUser user) {
    operatorAuthorization.requireOperator(user);
    return titleDefRepository.findAllByOrderByZoneIdAscTierAsc().stream()
        .map(def -> AdminZoneTitleDefResDto.of(def, userZoneTitleRepository.countByTitleDef_Id(def.getId())))
        .toList();
  }

  @Transactional
  public AdminZoneTitleDefResDto create(AuthenticatedUser user, AdminZoneTitleCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    String zoneId = ChatZone.fromString(request.zoneId()).name();
    if (titleDefRepository.existsByZoneIdAndTier(zoneId, request.tier())) {
      throw new ConflictException("error.zone_title.duplicate_tier");
    }
    if (titleDefRepository.existsByZoneIdAndRequiredSuccessCount(zoneId, request.requiredSuccessCount())) {
      throw new ConflictException("error.zone_title.duplicate_required_success_count");
    }
    requireMonotonic(zoneId, null, request.tier(), request.requiredSuccessCount());

    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode(zoneId + "_T" + request.tier())
                .zoneId(zoneId)
                .tier(request.tier())
                .requiredSuccessCount(request.requiredSuccessCount())
                .titleName(request.titleName())
                .style(request.style())
                .color(request.color())
                .build());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("zoneId", zoneId);
    detail.put("tier", request.tier());
    detail.put("requiredSuccessCount", request.requiredSuccessCount());
    audit(user, "CREATE_TITLE_DEF", def.getId(), detail);
    return AdminZoneTitleDefResDto.of(def, 0L);
  }

  /**
   * 같은 구역 안에서 단계(tier)가 높을수록 달성 기준(requiredSuccessCount)도 커야 한다. excludeId는 수정 시 자기 자신을
   * 비교 대상에서 빼기 위한 것(생성 시에는 null).
   */
  void requireMonotonic(String zoneId, UUID excludeId, Integer tier, Integer requiredSuccessCount) {
    if (requiredSuccessCount <= 0) {
      throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
    }
    for (ZoneTitleDef sibling : titleDefRepository.findByZoneIdOrderByTierAsc(zoneId)) {
      if (excludeId != null && sibling.getId().equals(excludeId)) {
        continue;
      }
      if (sibling.getTier() < tier && sibling.getRequiredSuccessCount() >= requiredSuccessCount) {
        throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
      }
      if (sibling.getTier() > tier && sibling.getRequiredSuccessCount() <= requiredSuccessCount) {
        throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
      }
    }
  }

  void audit(AuthenticatedUser user, String action, UUID targetId, Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType("ZONE_TITLE_DEF")
            .targetId(targetId)
            .detail(detail)
            .build());
  }
}
```

- [ ] **Step 4: 컨트롤러 작성(list + create만)**

```java
package com.butingbe.domain.zonetitle.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.service.AdminZoneTitleService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구역 칭호 정의 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-titles")
@RequiredArgsConstructor
public class AdminZoneTitleController {

  private final AdminZoneTitleService adminZoneTitleService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminZoneTitleDefResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("칭호 정의 목록", adminZoneTitleService.list(user)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminZoneTitleDefResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid AdminZoneTitleCreateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("칭호 정의 생성", adminZoneTitleService.create(user, request)));
  }
}
```

- [ ] **Step 5: 서비스 테스트 작성(신규 파일)**

`src/test/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleServiceTest.java`:
```java
package com.butingbe.domain.zonetitle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ConflictException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import com.butingbe.support.AbstractContainerTest;

@Transactional
class AdminZoneTitleServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneTitleService service;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    operator =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("op-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("op")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("정의를 생성하면 holderCount는 0이고 감사 로그가 남는다")
  void createsDefAndAudits() {
    var result =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThat(result.holderCount()).isZero();
    assertThat(result.titleCode()).isEqualTo("SUYEONG_NAMGU_T1");
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("ZONE_TITLE_DEF", UUID.fromString(result.titleDefId())))
        .hasSize(1);
  }

  @Test
  @DisplayName("같은 구역·단계로 중복 생성하면 409")
  void rejectsDuplicateTier() {
    service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "다른이름", "chip", "#111111")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.duplicate_tier");
  }

  @Test
  @DisplayName("같은 구역·달성 기준으로 중복 생성하면 409")
  void rejectsDuplicateRequiredSuccessCount() {
    service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 2, 1, "다른이름", "chip", "#111111")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.duplicate_required_success_count");
  }

  @Test
  @DisplayName("높은 단계인데 달성 기준이 더 낮으면 400")
  void rejectsNonMonotonicRequiredSuccessCount() {
    service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 2, 3, "다른이름", "chip", "#111111")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_title.invalid_required_success_count");
  }

  @Test
  @DisplayName("list는 zoneId·tier 순으로 정렬되고 holderCount를 채운다")
  void listOrdersByZoneAndTierWithHolderCount() {
    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode("SUYEONG_NAMGU_T1")
                .zoneId("SUYEONG_NAMGU")
                .tier(1)
                .requiredSuccessCount(1)
                .titleName("탐방가")
                .style("chip")
                .color("#000000")
                .build());
    var holder = userRepository.save(
        User.builder()
            .email("h-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Holder"))
            .nickname("holder")
            .role(UserRole.USER)
            .build());
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());

    var result = service.list(operator);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).holderCount()).isEqualTo(1);
  }
}
```

- [ ] **Step 6: 컨트롤러 테스트 작성(신규 파일)**

`src/test/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleControllerTest.java`:
```java
package com.butingbe.domain.zonetitle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.service.AdminZoneTitleService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JacksonJsonHttpMessageConverter;

@ExtendWith(MockitoExtension.class)
class AdminZoneTitleControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private AdminZoneTitleService adminZoneTitleService;
  @InjectMocks private AdminZoneTitleController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(new LocalValidatorFactoryBean())
            .setControllerAdvice(
                new GlobalExceptionHandler(new StaticMessageSource(), fixedLocaleResolver()))
            .build();
  }

  @Test
  @DisplayName("칭호 정의 목록 200")
  void list() throws Exception {
    when(adminZoneTitleService.list(any())).thenReturn(List.of());

    mockMvc.perform(get("/admin/zone-titles")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("칭호 정의 생성 200")
  void create() throws Exception {
    when(adminZoneTitleService.create(any(), any()))
        .thenReturn(
            new AdminZoneTitleDefResDto(
                UUID.randomUUID().toString(), "SUYEONG_NAMGU_T1", "SUYEONG_NAMGU", 1, 1,
                "탐방가", "chip", "#000000", 0L, 0L, null, null));

    mockMvc
        .perform(
            post("/admin/zone-titles")
                .contentType("application/json")
                .content(
                    "{\"zoneId\":\"SUYEONG_NAMGU\",\"tier\":1,\"requiredSuccessCount\":1,"
                        + "\"titleName\":\"탐방가\",\"style\":\"chip\",\"color\":\"#000000\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.titleCode").value("SUYEONG_NAMGU_T1"));
  }

  @Test
  @DisplayName("필수값 누락이면 400")
  void createValidation() throws Exception {
    mockMvc
        .perform(post("/admin/zone-titles").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  private HandlerMethodArgumentResolver authenticatedUserResolver() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          WebDataBinderFactory binderFactory) {
        return new AuthenticatedUser(USER_ID, "op@example.com", "op", List.of());
      }
    };
  }

  private LocaleResolver fixedLocaleResolver() {
    return new LocaleResolver() {
      @Override
      public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
        return Locale.KOREAN;
      }

      @Override
      public void setLocale(
          jakarta.servlet.http.HttpServletRequest request,
          jakarta.servlet.http.HttpServletResponse response,
          Locale locale) {}
    };
  }
}
```
이 표준 MockMvc 셋업(익명 `HandlerMethodArgumentResolver`/`LocaleResolver`)은 이후 태스크의 모든 컨트롤러 테스트에서 그대로 재사용한다 — 각 태스크에서 반복 설명하지 않고 "Task 2의 표준 셋업을 그대로 쓴다"로 축약한다. **실제 작성 전에 `AdminRewardPayoutControllerTest.java`를 열어 헬퍼 메서드의 정확한 시그니처(특히 `JacksonJsonHttpMessageConverter` 생성자 인자 유무, `LocaleResolver` 구현이 별도 클래스인지 익명 클래스인지)를 그대로 베껴서 편차를 없앤다** — 이 초안은 구조를 보여주기 위한 것이고, 실제 리포지토리에 있는 기존 컨트롤러 테스트 파일이 항상 우선한다.

- [ ] **Step 7: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneTitleServiceTest" --tests "*AdminZoneTitleControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleCreateReqDto.java src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleDefResDto.java src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java src/test/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleServiceTest.java src/test/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleControllerTest.java
git commit -m "feat(zonetitle): admin zone title def list/create API"
```

---

## Task 3: `PATCH`/`DELETE /admin/zone-titles/{titleDefId}`(수정·소급 발급·삭제)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleUpdateReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java`
- Test: `AdminZoneTitleServiceTest.java`, `AdminZoneTitleControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminZoneTitleService.update(user, titleDefId, AdminZoneTitleUpdateReqDto) -> AdminZoneTitleDefResDto`, `AdminZoneTitleService.delete(user, titleDefId) -> void`.
- Consumes: Task 1의 `ZoneTitleDef.applyEditable`, `ZoneTitleService.backfillGrants`, `ZoneTitleDefRepository.existsByZoneIdAndRequiredSuccessCountAndIdNot`, `UserZoneTitleRepository.countByTitleDef_Id`. Task 2의 `requireMonotonic`/`audit` 헬퍼.

- [ ] **Step 1: 요청 DTO 작성**

```java
package com.butingbe.domain.zonetitle.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminZoneTitleUpdateReqDto(
    String titleName, Integer requiredSuccessCount, @NotNull Boolean retroactive, @NotNull Long expectedRevision) {}
```
(`titleName`/`requiredSuccessCount`는 null이면 미변경. `retroactive`는 `requiredSuccessCount`를 바꿀 때만 의미가 있지만, 호출부가 "소급 여부를 명시"하도록 항상 필수로 둔다 — 이슈 요구사항 "소급 여부 명시"를 그대로 반영.)

- [ ] **Step 2: 서비스에 `update`/`delete` 추가**

`AdminZoneTitleService.java` import에 추가:
```java
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
```

메서드 추가(`create` 다음):
```java

  @Transactional
  public AdminZoneTitleDefResDto update(
      AuthenticatedUser user, UUID titleDefId, AdminZoneTitleUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneTitleDef def =
        titleDefRepository
            .findById(titleDefId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_title.def_not_found"));
    if (!def.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_title.stale_revision");
    }

    Map<String, Object> before = snapshot(def);
    if (request.requiredSuccessCount() != null) {
      if (!request.requiredSuccessCount().equals(def.getRequiredSuccessCount())
          && titleDefRepository.existsByZoneIdAndRequiredSuccessCountAndIdNot(
              def.getZoneId(), request.requiredSuccessCount(), titleDefId)) {
        throw new ConflictException("error.zone_title.duplicate_required_success_count");
      }
      requireMonotonic(def.getZoneId(), titleDefId, def.getTier(), request.requiredSuccessCount());
    }
    def.applyEditable(request.titleName(), request.requiredSuccessCount());
    try {
      titleDefRepository.saveAndFlush(def);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.zone_title.stale_revision");
    }

    int backfilled = 0;
    if (Boolean.TRUE.equals(request.retroactive()) && request.requiredSuccessCount() != null) {
      backfilled = zoneTitleService.backfillGrants(def);
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("before", before);
    detail.put("after", snapshot(def));
    detail.put("retroactive", request.retroactive());
    detail.put("backfilledCount", backfilled);
    audit(user, "PATCH_TITLE_DEF", def.getId(), detail);
    return AdminZoneTitleDefResDto.of(def, userZoneTitleRepository.countByTitleDef_Id(def.getId()));
  }

  @Transactional
  public void delete(AuthenticatedUser user, UUID titleDefId) {
    operatorAuthorization.requireOperator(user);
    ZoneTitleDef def =
        titleDefRepository
            .findById(titleDefId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_title.def_not_found"));
    if (userZoneTitleRepository.countByTitleDef_Id(titleDefId) > 0) {
      throw new ConflictException("error.zone_title.has_holders");
    }
    titleDefRepository.delete(def);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("zoneId", def.getZoneId());
    detail.put("tier", def.getTier());
    audit(user, "DELETE_TITLE_DEF", titleDefId, detail);
  }

  private Map<String, Object> snapshot(ZoneTitleDef def) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("titleName", def.getTitleName());
    map.put("requiredSuccessCount", def.getRequiredSuccessCount());
    return map;
  }
```
`UserZoneTitle` import는 이 태스크에서 직접 타입으로 쓰이지 않으면 추가하지 않는다(미사용 import는 Spotless 대상은 아니지만 깔끔하게 유지 — 실제로 위 코드에서 안 쓰이므로 import 목록에서 제외한다).

- [ ] **Step 3: 컨트롤러에 엔드포인트 추가**

`AdminZoneTitleController.java` import에 추가:
```java
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
```

메서드 추가:
```java

  @PatchMapping("/{titleDefId}")
  public ResponseEntity<ApiResponse<AdminZoneTitleDefResDto>> update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID titleDefId,
      @RequestBody @Valid AdminZoneTitleUpdateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("칭호 정의 수정", adminZoneTitleService.update(user, titleDefId, request)));
  }

  @DeleteMapping("/{titleDefId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID titleDefId) {
    adminZoneTitleService.delete(user, titleDefId);
    return ResponseEntity.noContent().build();
  }
```

- [ ] **Step 4: 서비스 테스트 추가**

`AdminZoneTitleServiceTest.java`에 추가:
```java
  @Test
  @DisplayName("update: retroactive=true면 이미 충족한 유저에게 소급 발급한다")
  void updateWithRetroactiveBackfills() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("소급대상");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");

    var result =
        service.update(
            operator,
            titleDefId,
            new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                null, 1, true, created.revision()));

    assertThat(result.requiredSuccessCount()).isEqualTo(1);
    assertThat(result.holderCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("update: retroactive=false면 소급 발급하지 않는다")
  void updateWithoutRetroactiveDoesNotBackfill() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("소급대상2");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");

    var result =
        service.update(
            operator,
            titleDefId,
            new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                null, 1, false, created.revision()));

    assertThat(result.holderCount()).isZero();
  }

  @Test
  @DisplayName("update: expectedRevision이 다르면 409")
  void updateRejectsStaleRevision() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.update(
                    operator,
                    UUID.fromString(created.titleDefId()),
                    new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                        "새이름", null, false, created.revision() + 1)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.stale_revision");
  }

  @Test
  @DisplayName("delete: 보유자가 없으면 삭제된다")
  void deletesDefWithoutHolders() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    service.delete(operator, UUID.fromString(created.titleDefId()));

    assertThat(titleDefRepository.findById(UUID.fromString(created.titleDefId()))).isEmpty();
  }

  @Test
  @DisplayName("delete: 보유자가 있으면 409")
  void deleteRejectsWhenHoldersExist() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("보유자");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");
    var def = titleDefRepository.findById(titleDefId).orElseThrow();
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());

    assertThatThrownBy(() -> service.delete(operator, titleDefId))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.has_holders");
  }
```
이 테스트들은 Task 1 Step 8에서 추가한 `savedUser(...)`/`successfulParticipation(...)` 헬퍼를 재사용한다 — 만약 Task 1을 이 파일(`AdminZoneTitleServiceTest`)이 아니라 `ZoneTitleServiceTest`에만 추가했다면, 같은 헬퍼 2개를 이 파일에도 복사해 넣는다(다른 `@Autowired` 필드 이름·픽스처 구조가 같은 패키지 관례를 따르므로 그대로 복사 가능).

- [ ] **Step 5: 컨트롤러 테스트 추가**

`AdminZoneTitleControllerTest.java`에 import 추가:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto;
```

테스트 추가:
```java
  @Test
  @DisplayName("칭호 정의 수정 200")
  void update() throws Exception {
    UUID titleDefId = UUID.randomUUID();
    when(adminZoneTitleService.update(any(), eq(titleDefId), any()))
        .thenReturn(
            new AdminZoneTitleDefResDto(
                titleDefId.toString(), "SUYEONG_NAMGU_T1", "SUYEONG_NAMGU", 1, 3,
                "탐방가", "chip", "#000000", 2L, 1L, null, null));

    mockMvc
        .perform(
            patch("/admin/zone-titles/{id}", titleDefId)
                .contentType("application/json")
                .content("{\"requiredSuccessCount\":3,\"retroactive\":true,\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requiredSuccessCount").value(3));
  }

  @Test
  @DisplayName("칭호 정의 삭제 204")
  void delete() throws Exception {
    UUID titleDefId = UUID.randomUUID();

    mockMvc.perform(delete("/admin/zone-titles/{id}", titleDefId)).andExpect(status().isNoContent());
  }
```
(`import static org.mockito.ArgumentMatchers.eq;`이 이미 없으면 추가.)

- [ ] **Step 6: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneTitleServiceTest" --tests "*AdminZoneTitleControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zonetitle/dto/request/AdminZoneTitleUpdateReqDto.java src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java src/test/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleServiceTest.java src/test/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleControllerTest.java
git commit -m "feat(zonetitle): admin zone title def update/delete API with retroactive backfill"
```

---

## Task 4: `GET /admin/zone-titles/{titleDefId}/holders`(보유자 조회)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderPageResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java`
- Modify: `src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java`
- Test: `AdminZoneTitleServiceTest.java`, `AdminZoneTitleControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminZoneTitleService.holders(user, titleDefId, page, size) -> AdminZoneTitleHolderPageResDto`.
- Consumes: Task 1의 `UserZoneTitleRepository.findByTitleDef_Id(UUID, Pageable)`, `UserRepository.findAllById`(기존).

- [ ] **Step 1: 응답 DTO 작성**

`AdminZoneTitleHolderItemResDto.java`:
```java
package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import java.time.OffsetDateTime;

public record AdminZoneTitleHolderItemResDto(
    String userTitleId,
    String userId,
    String nickname,
    String email,
    OffsetDateTime earnedAt,
    boolean equipped,
    OffsetDateTime revokedAt) {

  public static AdminZoneTitleHolderItemResDto of(UserZoneTitle title, User user) {
    return new AdminZoneTitleHolderItemResDto(
        title.getId().toString(),
        title.getUserId().toString(),
        user == null ? null : user.getNickname(),
        user == null ? null : user.getEmail(),
        title.getEarnedAt(),
        Boolean.TRUE.equals(title.getEquipped()),
        title.getRevokedAt());
  }
}
```

`AdminZoneTitleHolderPageResDto.java`:
```java
package com.butingbe.domain.zonetitle.dto.response;

import java.util.List;

public record AdminZoneTitleHolderPageResDto(
    List<AdminZoneTitleHolderItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
```

- [ ] **Step 2: 서비스에 `holders` 추가**

`AdminZoneTitleService.java` import에 추가:
```java
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
```
필드 추가:
```java
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final UserRepository userRepository;
```
(`DEFAULT_SIZE`/`MAX_SIZE`는 클래스 상단 필드 선언부 바로 위, 기존 admin 서비스 관례와 동일한 위치에 둔다.)

메서드 추가(`delete` 다음):
```java

  @Transactional(readOnly = true)
  public AdminZoneTitleHolderPageResDto holders(
      AuthenticatedUser user, UUID titleDefId, Integer page, Integer size) {
    operatorAuthorization.requireOperator(user);
    if (!titleDefRepository.existsById(titleDefId)) {
      throw new ResourceNotFoundException("error.zone_title.def_not_found");
    }
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    Page<UserZoneTitle> result =
        userZoneTitleRepository.findByTitleDef_Id(
            titleDefId, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.desc("earnedAt"))));

    Map<UUID, User> usersById =
        userRepository
            .findAllById(result.getContent().stream().map(UserZoneTitle::getUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderItemResDto> items =
        result.getContent().stream()
            .map(
                t ->
                    com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderItemResDto.of(
                        t, usersById.get(t.getUserId())))
            .toList();
    return new AdminZoneTitleHolderPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }
```
(정식 import로 정리할 때 `com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderItemResDto`를 상단 import 블록으로 옮기고 본문에서는 짧은 이름 `AdminZoneTitleHolderItemResDto`만 쓴다 — 위는 diff 설명 편의상 완전 경로로 적었다.)

- [ ] **Step 3: 컨트롤러에 엔드포인트 추가**

`AdminZoneTitleController.java` import에 추가:
```java
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto;
import org.springframework.web.bind.annotation.RequestParam;
```

메서드 추가:
```java

  @GetMapping("/{titleDefId}/holders")
  public ResponseEntity<ApiResponse<AdminZoneTitleHolderPageResDto>> holders(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID titleDefId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "칭호 보유자 목록", adminZoneTitleService.holders(user, titleDefId, page, size)));
  }
```

- [ ] **Step 4: 서비스 테스트 추가**

`AdminZoneTitleServiceTest.java`에 추가:
```java
  @Test
  @DisplayName("holders: earnedAt 내림차순으로 페이징하고 닉네임을 채운다")
  void holdersReturnsPagedItemsWithNickname() {
    var created =
        service.create(operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var def = titleDefRepository.findById(titleDefId).orElseThrow();
    var holder = savedUser("보유자1");
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(true)
            .build());

    var result = service.holders(operator, titleDefId, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).nickname()).isEqualTo("보유자1");
    assertThat(result.items().get(0).equipped()).isTrue();
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("holders: 없는 정의면 404")
  void holdersNotFound() {
    assertThatThrownBy(() -> service.holders(operator, UUID.randomUUID(), 1, 20))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }
```

- [ ] **Step 5: 컨트롤러 테스트 추가**

`AdminZoneTitleControllerTest.java`에 추가:
```java
  @Test
  @DisplayName("칭호 보유자 목록 200")
  void holders() throws Exception {
    UUID titleDefId = UUID.randomUUID();
    when(adminZoneTitleService.holders(any(), eq(titleDefId), any(), any()))
        .thenReturn(
            new com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto(
                List.of(), 1, 20, 0, 1, false));

    mockMvc
        .perform(get("/admin/zone-titles/{id}/holders", titleDefId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }
```

- [ ] **Step 6: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneTitleServiceTest" --tests "*AdminZoneTitleControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderItemResDto.java src/main/java/com/butingbe/domain/zonetitle/dto/response/AdminZoneTitleHolderPageResDto.java src/main/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleService.java src/main/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleController.java src/test/java/com/butingbe/domain/zonetitle/service/AdminZoneTitleServiceTest.java src/test/java/com/butingbe/domain/zonetitle/controller/AdminZoneTitleControllerTest.java
git commit -m "feat(zonetitle): admin zone title holders API"
```

---

## Task 5: 감사 로그 보강 — `AdminReviewService.revoke`/`unhide`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java`(확장)

**Interfaces:**
- Produces: 없음(내부 부수효과 — 감사 로그 저장).
- Consumes: 기존 `ZoneEventAuditLogRepository`(신규 의존성 주입).

이슈 #245의 "#236~#244 액션의 감사 기록 통합 점검" 요구사항 중 실제 코드 수정이 필요한 첫 두 곳. `revoke`/`unhide`는 현재 API에 사유(reason) 파라미터가 없으므로(Global Constraints 참고), actor + before/after 상태만 기록한다.

- [ ] **Step 1: 기존 테스트 실행해 베이스라인 확인**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminReviewServiceTest"
```
Expected: 전부 PASS(현재 감사 로그 관련 단언은 없음).

- [ ] **Step 2: `AdminReviewService`에 감사 로그 저장 추가**

`AdminReviewService.java` import에 추가:
```java
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
```

필드 추가(`operatorAuthorization` 다음):
```java
  private final ZoneEventAuditLogRepository auditLogRepository;
```

`revoke` 메서드 마지막 줄(BASE 지급 실패 처리 다음)에 추가:
```java
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("before", Map.of("status", "SUCCESS"));
    detail.put("after", Map.of("status", "REVOKED"));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("REVOKE_PARTICIPATION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
  }
```
(이 블록을 `revoke` 메서드의 기존 마지막 `}`  바로 앞에 넣는다.)

`unhide` 메서드 마지막 줄(보류 해제 처리 다음)에 추가:
```java
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("before", Map.of("hidden", true));
    detail.put("after", Map.of("hidden", false));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("UNHIDE_PARTICIPATION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
  }
```

- [ ] **Step 3: 기존 테스트에 감사 로그 단언 추가**

`AdminReviewServiceTest.java` 상단 import에 추가:
```java
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
```
`@Autowired` 필드 블록에 추가:
```java
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
```

`revokeReversesReward` 테스트 마지막에 단언 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("PARTICIPATION", p.getId())).hasSize(1);
```

`unhideDismissesReports` 테스트 마지막에 단언 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("PARTICIPATION", p.getId())).hasSize(1);
```

- [ ] **Step 4: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminReviewServiceTest"
```
Expected: BUILD SUCCESSFUL, 전부 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java
git commit -m "feat(zoneevent): audit log for participation revoke/unhide"
```

---

## Task 6: 감사 로그 보강 — `AdminZoneEventReviewService.approve`/`reject`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`(확장)

**Interfaces:**
- Produces: 없음(내부 부수효과).
- Consumes: 기존 `ZoneEventAuditLogRepository`(신규 의존성 주입).

`approve`는 사유 파라미터가 없으므로 actor + before/after만 기록한다. `reject`는 `request.reason()`이 이미 있으므로 `detail`에 포함한다. 두 메서드 모두 Idempotency-Key 재생 경로(`replay.isPresent()`)에서는 실제 상태 변경이 없으므로 감사 로그를 남기지 않는다 — 실제 처리 분기에만 추가한다.

- [ ] **Step 1: 기존 테스트 실행해 베이스라인 확인**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"
```
Expected: 전부 PASS.

- [ ] **Step 2: `AdminZoneEventReviewService`에 감사 로그 저장 추가**

import에 추가:
```java
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
```

필드 추가(`reportRepository` 다음):
```java
  private final ZoneEventAuditLogRepository auditLogRepository;
```

`approve` 메서드에서 `idempotencyService.save(idempotencyKey, APPROVE_ENDPOINT, fingerprint, result);` 바로 앞에 삽입:
```java
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("submissionId", submission.getId().toString());
    detail.put("before", Map.of("status", "UNDER_REVIEW"));
    detail.put("after", Map.of("status", "SUCCESS"));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("APPROVE_SUBMISSION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
```

`reject` 메서드에서 `idempotencyService.save(idempotencyKey, REJECT_ENDPOINT, fingerprint, null);` 바로 앞에 삽입:
```java
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("submissionId", submission.getId().toString());
    detail.put("reason", request.reason());
    detail.put("before", Map.of("status", "UNDER_REVIEW"));
    detail.put("after", Map.of("status", "FAIL"));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("REJECT_SUBMISSION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
```

- [ ] **Step 3: 기존 테스트에 감사 로그 단언 추가**

`AdminZoneEventReviewServiceTest.java` import에 추가:
```java
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
```
`@Autowired` 필드 블록에 추가:
```java
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
```

`approveMarksSuccessAndCreatesBasePayout`(또는 저장소의 현재 이름 — `grep -n "void approve" src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`로 정확한 메서드명을 먼저 확인) 테스트 마지막에 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("PARTICIPATION", p.getId())).hasSize(1);
```

`rejectMarksFail` 테스트(463번째 줄 근처) 마지막에 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("PARTICIPATION", p.getId())).hasSize(1);
```

- [ ] **Step 4: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"
```
Expected: BUILD SUCCESSFUL, 전부 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java
git commit -m "feat(zoneevent): audit log for submission approve/reject"
```

---

## Task 7: 감사 로그 보강 — `AdminRewardCatalogService.create`/`update`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardCatalogService.java`
- Test: `src/test/java/com/butingbe/domain/reward/service/AdminRewardCatalogServiceTest.java`(확장)

**Interfaces:**
- Produces: 없음(내부 부수효과).
- Consumes: 기존 `ZoneEventAuditLogRepository`(reward 패키지에서 zoneevent 패키지 리포지토리를 참조 — `RewardPayoutService`가 이미 같은 방식으로 참조하고 있어 선례가 있다).

- [ ] **Step 1: 기존 테스트 실행해 베이스라인 확인**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardCatalogServiceTest"
```
Expected: 전부 PASS.

- [ ] **Step 2: `AdminRewardCatalogService`에 감사 로그 저장 추가**

import에 추가:
```java
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import java.util.LinkedHashMap;
```

필드 추가(`operatorAuthorization` 다음):
```java
  private final ZoneEventAuditLogRepository auditLogRepository;
```

`create` 메서드의 `return RewardCatalogResDto.from(catalog);` 바로 앞에 삽입:
```java
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("code", catalog.getCode());
    detail.put("rewardType", catalog.getRewardType().name());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("CREATE_REWARD_CATALOG")
            .targetType("REWARD_CATALOG")
            .targetId(catalog.getId())
            .detail(detail)
            .build());
```

`update` 메서드를 다음으로 교체(`before`/`after` 스냅샷 추가):
```java
  @Transactional
  public RewardCatalogResDto update(
      AuthenticatedUser user, UUID rewardId, AdminRewardCatalogUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    RewardCatalog catalog =
        rewardCatalogRepository
            .findById(rewardId)
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.catalog_not_found"));
    Map<String, Object> before = snapshot(catalog);
    catalog.update(request.name(), request.stock(), request.monthlyCap(), request.active());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("before", before);
    detail.put("after", snapshot(catalog));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("PATCH_REWARD_CATALOG")
            .targetType("REWARD_CATALOG")
            .targetId(catalog.getId())
            .detail(detail)
            .build());
    return RewardCatalogResDto.from(catalog);
  }

  private Map<String, Object> snapshot(RewardCatalog catalog) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", catalog.getName());
    map.put("stock", catalog.getStock());
    map.put("monthlyCap", catalog.getMonthlyCap());
    map.put("active", catalog.getActive());
    return map;
  }
```
(`java.util.Map` import는 이미 있다 — 파일 상단 `import java.util.List;` 근처 확인.)

- [ ] **Step 3: 기존 테스트에 감사 로그 단언 추가**

`AdminRewardCatalogServiceTest.java` import에 추가:
```java
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
```
`@Autowired` 필드 블록에 추가:
```java
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
```

`createAndDuplicate` 테스트에서 `assertThat(created.stock()).isEqualTo(100);` 다음 줄에 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("REWARD_CATALOG", UUID.fromString(created.rewardId())))
        .hasSize(1);
```

`updateCatalog` 테스트 마지막에 추가:
```java
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("REWARD_CATALOG", rewardId)).hasSize(1);
```

- [ ] **Step 4: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardCatalogServiceTest"
```
Expected: BUILD SUCCESSFUL, 전부 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/service/AdminRewardCatalogService.java src/test/java/com/butingbe/domain/reward/service/AdminRewardCatalogServiceTest.java
git commit -m "feat(reward): audit log for reward catalog create/update"
```

---

## Task 8: 운영 통계 집계용 리포지토리 메서드

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventSubmissionRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventReportRepository.java`
- Modify: `src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java`
- Modify: `src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/repository/ZoneEventReportRepositoryTest.java` 등 기존 리포지토리 테스트가 있으면 확장(없으면 Task 9의 서비스 테스트가 간접 검증하므로 이 태스크에서는 별도 리포지토리 단위 테스트를 만들지 않는다 — 이 리포지토리들의 다른 메서드도 전용 테스트 파일 없이 서비스 테스트로만 검증되는 기존 관례를 따른다).

**Interfaces:**
- Produces: `ZoneEventParticipationRepository.countByEvent_IdAndCurrentSubmissionIdIsNotNull(UUID)`, `ZoneEventSubmissionRepository.countByParticipation_Event_Id(UUID)`, `ZoneEventReportRepository.countByEventIdAndStatusIn(UUID, Collection<ReportStatus>)` + `default countUnresolvedByEventId(UUID)`, `BaseRewardPayoutRepository.countByEventIdAndStatus(UUID, BaseRewardPayoutStatus)`, `RewardPayoutRepository.countByEventIdAndStatus(UUID, RewardPayoutStatus)`.
- Consumes: 없음(기반 태스크, Task 9가 소비).

- [ ] **Step 1: `ZoneEventParticipationRepository`에 제출 여부 카운트 추가**

`findRankedPublicSuccessByEvent` 다음(Task 1에서 이미 `findDistinctSuccessUserIdsByZone`을 추가했다면 그다음)에 추가:
```java

  long countByEvent_IdAndCurrentSubmissionIdIsNotNull(UUID eventId);
```

- [ ] **Step 2: `ZoneEventSubmissionRepository`에 이벤트 단위 제출 시도 수 추가**

`countByParticipation_Id` 다음에 추가:
```java

  long countByParticipation_Event_Id(UUID eventId);
```

- [ ] **Step 3: `ZoneEventReportRepository`에 이벤트 단위 미해결 신고 수 추가**

`hasUnresolvedReports` default 메서드 다음, `@Query searchForAdmin` 앞에 추가:
```java

  @Query(
      "SELECT COUNT(r) FROM ZoneEventReport r, ZoneEventParticipation p "
          + "WHERE r.participationId = p.id AND p.event.id = :eventId AND r.status IN :statuses")
  long countByEventIdAndStatusIn(
      @Param("eventId") UUID eventId, @Param("statuses") Collection<ReportStatus> statuses);

  /** {@link #countByEventIdAndStatusIn}을 {@link #UNRESOLVED_STATUSES}로 고정한 편의 메서드. */
  default long countUnresolvedByEventId(UUID eventId) {
    return countByEventIdAndStatusIn(eventId, UNRESOLVED_STATUSES);
  }
```

- [ ] **Step 4: `BaseRewardPayoutRepository`에 이벤트·상태별 카운트 추가(JPQL 조인)**

`BaseRewardPayoutRepository.java`의 `searchForAdmin` 메서드 다음에 추가:
```java

  @Query(
      "SELECT COUNT(b) FROM BaseRewardPayout b, ZoneEventParticipation p "
          + "WHERE b.participationId = p.id AND p.event.id = :eventId AND b.status = :status")
  long countByEventIdAndStatus(
      @Param("eventId") UUID eventId, @Param("status") BaseRewardPayoutStatus status);
```
(`ZoneEventParticipation` import가 이미 없으면 `import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;` 추가 — JPQL의 `FROM` 절에 쓰는 엔티티는 import가 없어도 동작하지만 이 리포지토리 파일의 기존 관례(`ZoneEventReportRepository`도 `ZoneEventParticipation`을 import 없이 JPQL 문자열로만 참조)를 그대로 따르면 import는 불필요하다 — 실제로 `ZoneEventReportRepository.java`를 열어 import 목록을 확인하고 그대로 따라한다.)

- [ ] **Step 5: `RewardPayoutRepository`에 이벤트·상태별 카운트 추가(파생 쿼리)**

`RewardPayoutRepository.java`의 `searchForAdmin` 메서드 다음에 추가:
```java

  long countByEventIdAndStatus(UUID eventId, RewardPayoutStatus status);
```

- [ ] **Step 6: 컴파일 확인**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew compileJava compileTestJava --no-daemon -g "C:\gradle-home"
```
Expected: BUILD SUCCESSFUL(이 태스크는 새 메서드를 아직 아무도 호출하지 않으므로 컴파일만 확인하면 충분 — Task 9에서 실제 동작을 검증한다).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventSubmissionRepository.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventReportRepository.java src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java
git commit -m "feat(zoneevent): repository counts for operational stats aggregation"
```

---

## Task 9: `GET /admin/zone-event-stats`(회차·슬롯별 운영 통계)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsService.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsController.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsServiceTest.java`(신규)
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsControllerTest.java`(신규)

**Interfaces:**
- Produces: `AdminZoneEventStatsService.stats(user, roundId, from, to) -> AdminZoneEventStatsResDto`.
- Consumes: Task 8의 리포지토리 카운트 메서드 전부, 기존 `ZoneEventRoundRepository.findByStartsAtBetweenOrderByStartsAtAsc`, `ZoneEventRoundSlotRepository.findByRound_Id`, `ZoneEventParticipationRepository.findTopPublicSuccessByEvent`, `UserZoneTitleRepository.countByZoneIdAndEarnedAtBetween`(Task 1).

**successRate 정의(이슈 명시 사항)**: 분모는 `submittedCount`(참여 건 기준)다. `submittedCount == 0`이면 `0.0`.

- [ ] **Step 1: 응답 DTO 작성**

`AdminZoneEventStatsItemResDto.java`:
```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.UUID;

public record AdminZoneEventStatsItemResDto(
    String roundId,
    String slotId,
    String zoneId,
    String eventId,
    long joinedCount,
    long submittedCount,
    long submissionAttemptCount,
    long successCount,
    long failCount,
    long pendingReviewCount,
    double successRate,
    long openReportCount,
    long basePaidCount,
    long specialSentCount,
    TopContent topContent,
    long newTitleGrantCount) {

  public record TopContent(String participationId, long likeCount) {}

  /** 아직 이벤트가 배정되지 않은 슬롯(0값). */
  public static AdminZoneEventStatsItemResDto empty(UUID roundId, UUID slotId, String zoneId) {
    return new AdminZoneEventStatsItemResDto(
        roundId.toString(), slotId.toString(), zoneId, null,
        0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, null, 0);
  }
}
```

`AdminZoneEventStatsResDto.java`:
```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

public record AdminZoneEventStatsResDto(List<AdminZoneEventStatsItemResDto> slots) {}
```

- [ ] **Step 2: 서비스 작성**

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회차·슬롯별 운영 통계(참여·검수·신고·지급·칭호). ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventStatsService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final ZoneEventReportRepository reportRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final UserZoneTitleRepository userZoneTitleRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public AdminZoneEventStatsResDto stats(
      AuthenticatedUser user, UUID roundId, OffsetDateTime from, OffsetDateTime to) {
    operatorAuthorization.requireOperator(user);
    if (roundId == null && (from == null || to == null)) {
      throw new IllegalArgumentException("error.zone_event.stats.round_or_range_required");
    }

    List<ZoneEventRound> rounds =
        roundId != null
            ? List.of(
                roundRepository
                    .findById(roundId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found")))
            : roundRepository.findByStartsAtBetweenOrderByStartsAtAsc(from, to);

    List<AdminZoneEventStatsItemResDto> items = new ArrayList<>();
    for (ZoneEventRound round : rounds) {
      for (ZoneEventRoundSlot slot : slotRepository.findByRound_Id(round.getId())) {
        items.add(buildSlotStats(round, slot));
      }
    }
    return new AdminZoneEventStatsResDto(items);
  }

  private AdminZoneEventStatsItemResDto buildSlotStats(ZoneEventRound round, ZoneEventRoundSlot slot) {
    UUID eventId = slot.getEventId();
    if (eventId == null) {
      return AdminZoneEventStatsItemResDto.empty(round.getId(), slot.getId(), slot.getZoneId());
    }

    long joined = participationRepository.countByEvent_Id(eventId);
    long submitted = participationRepository.countByEvent_IdAndCurrentSubmissionIdIsNotNull(eventId);
    long attempts = submissionRepository.countByParticipation_Event_Id(eventId);
    long success = participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.SUCCESS);
    long fail = participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.FAIL);
    long pendingReview =
        participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.UNDER_REVIEW);
    double successRate = submitted == 0 ? 0.0 : (double) success / submitted;
    long openReports = reportRepository.countUnresolvedByEventId(eventId);
    long basePaid = baseRewardPayoutRepository.countByEventIdAndStatus(eventId, BaseRewardPayoutStatus.PAID);
    long specialSent = rewardPayoutRepository.countByEventIdAndStatus(eventId, RewardPayoutStatus.SENT);

    List<ZoneEventParticipation> top =
        participationRepository.findTopPublicSuccessByEvent(eventId, PageRequest.of(0, 1));
    AdminZoneEventStatsItemResDto.TopContent topContent =
        top.isEmpty()
            ? null
            : new AdminZoneEventStatsItemResDto.TopContent(top.get(0).getId().toString(), top.get(0).getLikeCount());

    long newTitleGrants =
        userZoneTitleRepository.countByZoneIdAndEarnedAtBetween(
            slot.getZoneId(), round.getStartsAt(), round.getEndsAt());

    return new AdminZoneEventStatsItemResDto(
        round.getId().toString(),
        slot.getId().toString(),
        slot.getZoneId(),
        eventId.toString(),
        joined,
        submitted,
        attempts,
        success,
        fail,
        pendingReview,
        successRate,
        openReports,
        basePaid,
        specialSent,
        topContent,
        newTitleGrants);
  }
}
```

- [ ] **Step 3: 컨트롤러 작성**

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventStatsService;
import com.butingbe.global.common.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회차·슬롯별 운영 통계. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-stats")
@RequiredArgsConstructor
public class AdminZoneEventStatsController {

  private final AdminZoneEventStatsService adminZoneEventStatsService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventStatsResDto>> stats(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {
    return ResponseEntity.ok(
        ApiResponse.success("운영 통계", adminZoneEventStatsService.stats(user, roundId, from, to)));
  }
}
```

- [ ] **Step 4: 서비스 테스트 작성**

`src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsServiceTest.java` — 라운드·슬롯·이벤트·참여를 직접 저장해 집계를 검증한다. `ZoneEventRoundRepository`/`ZoneEventRoundSlotRepository`/`ZoneEventRepository`/`ZoneEventTypeRepository`/`ZoneEventParticipationRepository`/`ZoneEventSubmissionRepository`/`BaseRewardPayoutRepository`/`RewardPayoutRepository`/`UserRepository`를 `@Autowired`한다:
```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventStatsServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventStatsService service;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private BaseRewardPayoutRepository baseRewardPayoutRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    operator =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("op-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("op")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("roundId도 from/to도 없으면 400")
  void requiresRoundOrRange() {
    assertThatThrownBy(() -> service.stats(operator, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.stats.round_or_range_required");
  }

  @Test
  @DisplayName("이벤트가 배정된 슬롯은 참여·성공·기본지급 건수를 집계한다")
  void aggregatesSlotStats() {
    OffsetDateTime now = OffsetDateTime.now();
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(1)
                .startsAt(now.minusHours(2))
                .endsAt(now.plusHours(22))
                .status(RoundStatus.ACTIVE)
                .build());
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .roundId(round.getId())
                .title("이벤트")
                .startsAt(now.minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    ZoneEventRoundSlot slot =
        slotRepository.save(
            ZoneEventRoundSlot.builder()
                .round(round)
                .slotKind(SlotKind.AUTH)
                .zoneId("SUYEONG_NAMGU")
                .eventId(event.getId())
                .build());

    ZoneEventParticipation success =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(UUID.randomUUID())
                .status(ParticipationStatus.SUCCESS)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(now)
                .build());
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(success.getId())
            .reward(new com.butingbe.domain.zoneevent.entity.RewardSnapshot(50, null, null, null))
            .build());
    var paid = baseRewardPayoutRepository.findByParticipationId(success.getId()).orElseThrow();
    paid.confirm(operator.id());
    paid.markSent(now, null);
    baseRewardPayoutRepository.saveAndFlush(paid);

    var result = service.stats(operator, round.getId(), null, null);

    assertThat(result.slots()).hasSize(1);
    var item = result.slots().get(0);
    assertThat(item.eventId()).isEqualTo(event.getId().toString());
    assertThat(item.joinedCount()).isEqualTo(1);
    assertThat(item.successCount()).isEqualTo(1);
    assertThat(item.basePaidCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("이벤트가 배정되지 않은 슬롯은 0값 항목을 돌려준다")
  void returnsEmptyItemForUnassignedSlot() {
    OffsetDateTime now = OffsetDateTime.now();
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(2)
                .startsAt(now.minusHours(2))
                .endsAt(now.plusHours(22))
                .status(RoundStatus.DRAFT)
                .build());
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("HAEUNDAE_GIJANG")
            .build());

    var result = service.stats(operator, round.getId(), null, null);

    assertThat(result.slots()).hasSize(1);
    assertThat(result.slots().get(0).eventId()).isNull();
    assertThat(result.slots().get(0).joinedCount()).isZero();
  }
}
```
`ZoneEventRound.builder()`가 `roundType`/`timezone`을 필수로 요구하지 않는지(둘 다 null 기본값이 있는지) 미리 `ZoneEventRound.java` 생성자를 확인한다(Task 1 조사에서 이미 확인함 — `roundType`은 null이면 `REGULAR`, `timezone`은 null이면 `"Asia/Seoul"`로 기본값이 채워지므로 테스트에서 생략 가능).

- [ ] **Step 5: 컨트롤러 테스트 작성**

`src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsControllerTest.java` — Task 2 Step 6의 표준 MockMvc 셋업(익명 `HandlerMethodArgumentResolver`/`LocaleResolver`, `AdminZoneTitleControllerTest`에서 그대로 베낀 헬퍼)을 재사용해 다음 2개 테스트만 작성한다:
```java
  @Test
  @DisplayName("운영 통계 조회 200")
  void stats() throws Exception {
    when(adminZoneEventStatsService.stats(any(), any(), any(), any()))
        .thenReturn(new AdminZoneEventStatsResDto(List.of()));

    mockMvc.perform(get("/admin/zone-event-stats").param("roundId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slots").isArray());
  }

  @Test
  @DisplayName("roundId/from/to 파라미터를 그대로 서비스에 전달한다")
  void statsPassesQueryParams() throws Exception {
    UUID roundId = UUID.randomUUID();
    when(adminZoneEventStatsService.stats(any(), eq(roundId), any(), any()))
        .thenReturn(new AdminZoneEventStatsResDto(List.of()));

    mockMvc
        .perform(get("/admin/zone-event-stats").param("roundId", roundId.toString()))
        .andExpect(status().isOk());
  }
```
(서비스는 `@Mock`, 컨트롤러는 `@InjectMocks` — Task 2 패턴 그대로.)

- [ ] **Step 6: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventStatsServiceTest" --tests "*AdminZoneEventStatsControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsItemResDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventStatsResDto.java src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsService.java src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsController.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventStatsServiceTest.java src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventStatsControllerTest.java
git commit -m "feat(zoneevent): admin zone event operational stats API"
```

---

## Task 10: `GET /admin/zone-event-audits`(감사 이력 조회)

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuditLogRepository.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditPageResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventAuditService.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditController.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventAuditServiceTest.java`(신규)
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditControllerTest.java`(신규)

**Interfaces:**
- Produces: `ZoneEventAuditLogRepository.searchForAdmin(targetType, targetId, actorId, from, to, Pageable) -> Page<ZoneEventAuditLog>`, `AdminZoneEventAuditService.list(user, resourceType, resourceId, actorId, from, to, page, size) -> AdminZoneEventAuditPageResDto`.
- Consumes: 기존 `ZoneEventAuditLog` 엔티티. 쿼리 파라미터 이름은 이슈 원문("resourceType/id, actorId, from/to, page/size")을 그대로 따라 `resourceType`/`resourceId`로 받고, 내부적으로 엔티티 필드명(`targetType`/`targetId`)에 매핑한다.

- [ ] **Step 1: `ZoneEventAuditLogRepository`에 검색 쿼리 추가**

전체 파일을 다음으로 교체:
```java
package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventAuditLogRepository extends JpaRepository<ZoneEventAuditLog, UUID> {

  List<ZoneEventAuditLog> findByTargetTypeAndTargetId(String targetType, UUID targetId);

  @Query(
      value =
          "select a from ZoneEventAuditLog a where "
              + "(:targetType is null or a.targetType = :targetType) "
              + "and (:targetId is null or a.targetId = :targetId) "
              + "and (:actorId is null or a.actorId = :actorId) "
              + "and (:from is null or a.createdAt >= :from) "
              + "and (:to is null or a.createdAt <= :to) "
              + "order by a.createdAt desc",
      countQuery =
          "select count(a) from ZoneEventAuditLog a where "
              + "(:targetType is null or a.targetType = :targetType) "
              + "and (:targetId is null or a.targetId = :targetId) "
              + "and (:actorId is null or a.actorId = :actorId) "
              + "and (:from is null or a.createdAt >= :from) "
              + "and (:to is null or a.createdAt <= :to)")
  Page<ZoneEventAuditLog> searchForAdmin(
      @Param("targetType") String targetType,
      @Param("targetId") UUID targetId,
      @Param("actorId") UUID actorId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      Pageable pageable);
}
```

- [ ] **Step 2: 응답 DTO 작성**

`AdminZoneEventAuditItemResDto.java`:
```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import java.time.OffsetDateTime;
import java.util.Map;

public record AdminZoneEventAuditItemResDto(
    String auditId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> detail,
    OffsetDateTime createdAt) {

  public static AdminZoneEventAuditItemResDto from(ZoneEventAuditLog log) {
    return new AdminZoneEventAuditItemResDto(
        log.getId().toString(),
        log.getActorId().toString(),
        log.getAction(),
        log.getTargetType(),
        log.getTargetId() == null ? null : log.getTargetId().toString(),
        log.getDetail(),
        log.getCreatedAt());
  }
}
```

`AdminZoneEventAuditPageResDto.java`:
```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

public record AdminZoneEventAuditPageResDto(
    List<AdminZoneEventAuditItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
```

- [ ] **Step 3: 서비스 작성**

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditPageResDto;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 행위 감사 이력 조회. ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventAuditService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventAuditLogRepository auditLogRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public AdminZoneEventAuditPageResDto list(
      AuthenticatedUser user,
      String resourceType,
      UUID resourceId,
      UUID actorId,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String targetType = resourceType == null || resourceType.isBlank() ? null : resourceType;

    Page<com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog> result =
        auditLogRepository.searchForAdmin(
            targetType, resourceId, actorId, from, to, PageRequest.of(pageNumber - 1, pageSize));

    var items = result.getContent().stream().map(AdminZoneEventAuditItemResDto::from).toList();
    return new AdminZoneEventAuditPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }
}
```

- [ ] **Step 4: 컨트롤러 작성**

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditPageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventAuditService;
import com.butingbe.global.common.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 행위 감사 이력 조회. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-audits")
@RequiredArgsConstructor
public class AdminZoneEventAuditController {

  private final AdminZoneEventAuditService adminZoneEventAuditService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventAuditPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) UUID resourceId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "감사 이력 목록",
            adminZoneEventAuditService.list(
                user, resourceType, resourceId, actorId, from, to, page, size)));
  }
}
```

- [ ] **Step 5: 서비스 테스트 작성**

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventAuditServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventAuditService service;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private UUID actorId;

  @BeforeEach
  void setUp() {
    var savedOperator =
        userRepository.save(
            User.builder()
                .email("op-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .name(new Name("Kim", "Tester"))
                .nickname("op")
                .role(UserRole.USER)
                .build());
    actorId = savedOperator.getId();
    operator =
        new AuthenticatedUser(
            actorId, "op@example.com", "op", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("resourceType/resourceId로 필터링한다")
  void filtersByResourceTypeAndId() {
    UUID targetId = UUID.randomUUID();
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("PATCH_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(targetId)
            .detail(Map.of("k", "v"))
            .build());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("CREATE_EVENT")
            .targetType("EVENT")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());

    var result = service.list(operator, "ZONE_TITLE_DEF", targetId, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).action()).isEqualTo("PATCH_TITLE_DEF");
  }

  @Test
  @DisplayName("actorId로 필터링한다")
  void filtersByActor() {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("CREATE_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(UUID.randomUUID())
            .action("CREATE_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());

    var result = service.list(operator, null, null, actorId, null, null, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).actorId()).isEqualTo(actorId.toString());
  }

  @Test
  @DisplayName("아무 조건 없이 조회하면 최신순으로 페이징된다")
  void listsAllOrderedByCreatedAtDesc() {
    for (int i = 0; i < 3; i++) {
      auditLogRepository.save(
          ZoneEventAuditLog.builder()
              .actorId(actorId)
              .action("ACTION_" + i)
              .targetType("ZONE_TITLE_DEF")
              .targetId(UUID.randomUUID())
              .detail(Map.of())
              .build());
    }

    var result = service.list(operator, null, null, null, null, null, 1, 2);

    assertThat(result.items()).hasSize(2);
    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(result.hasNext()).isTrue();
  }
}
```

- [ ] **Step 6: 컨트롤러 테스트 작성**

`src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditControllerTest.java` — Task 2 Step 6의 표준 MockMvc 셋업을 재사용:
```java
  @Test
  @DisplayName("감사 이력 목록 200")
  void list() throws Exception {
    when(adminZoneEventAuditService.list(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminZoneEventAuditPageResDto(List.of(), 1, 20, 0, 1, false));

    mockMvc
        .perform(get("/admin/zone-event-audits").param("resourceType", "ZONE_TITLE_DEF"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }
```

- [ ] **Step 7: 실행 확인**

```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew spotlessCheck test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventAuditServiceTest" --tests "*AdminZoneEventAuditControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuditLogRepository.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditItemResDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventAuditPageResDto.java src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventAuditService.java src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditController.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventAuditServiceTest.java src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventAuditControllerTest.java
git commit -m "feat(zoneevent): admin zone event audit log query API"
```

---

## Task 11: OpenAPI 문서화

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml`

이 저장소는 새 엔드포인트마다 `openapi3.yaml` 반영을 1급 산출물로 취급한다(PR 템플릿의 "Swagger 확인" 체크박스, 과거 문서 전용 PR들). 6개 신규 엔드포인트(`GET/POST /admin/zone-titles`, `PATCH/DELETE /admin/zone-titles/{titleDefId}`, `GET /admin/zone-titles/{titleDefId}/holders`, `GET /admin/zone-event-stats`, `GET /admin/zone-event-audits`)와 각 스키마를 추가한다.

- [ ] **Step 1: 기존 admin 섹션 구조 확인**

```bash
cd /c/dev/bu-ting-backend-245
grep -n "admin/zone-event-reports:\|admin/reward-payouts:\|components:\|  schemas:" src/main/resources/static/docs/openapi3.yaml | head -20
```
`/api/v1/admin/zone-event-reports`(목록, GET+페이징) 경로 블록과 `/api/v1/admin/reward-payouts/{payoutId}`(PATCH, revision 포함) 경로 블록을 열어 `security`/`parameters`/`responses`/`tags` 구조를 그대로 복사할 템플릿으로 삼는다(Task 조사 단계에서 이미 확인: `tags: [Zone Event]`, `security: [{opaqueToken: []}]`, 에러 응답은 전부 `ApiErrorResponse` 참조, 성공 응답은 `<Name>Envelope` 참조).

- [ ] **Step 2: 새 경로 6개를 알파벳/도메인 인접 위치에 삽입**

`/api/v1/admin/zone-event-reports` 경로 블록 바로 앞(또는 바로 뒤, 기존 admin 경로들이 모여 있는 위치)에 다음을 삽입한다. `<TAG>`는 기존 admin 경로들이 실제로 쓰는 태그 값(`Zone Event`)을 그대로 쓴다:

```yaml
  /api/v1/admin/zone-titles:
    get:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 구역 칭호 정의 목록 조회(보유자 수 포함)"
      security:
        - opaqueToken: []
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneTitleDefListEnvelope"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
    post:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 구역 칭호 정의 생성"
      security:
        - opaqueToken: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminZoneTitleCreateRequest"
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneTitleDefEnvelope"
        "400":
          description: "Bad Request - 달성 기준이 양의 정수가 아니거나 구역 내 단계 순서를 위반"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "Conflict - 같은 구역·단계 또는 같은 구역·달성 기준의 정의가 이미 존재"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-titles/{titleDefId}:
    patch:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 구역 칭호 정의 수정(이름·달성 기준, 소급 발급 여부 명시)"
      security:
        - opaqueToken: []
      parameters:
        - name: titleDefId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminZoneTitleUpdateRequest"
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneTitleDefEnvelope"
        "400":
          description: Bad Request
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: Not Found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "Conflict - expectedRevision 불일치 또는 달성 기준 중복"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
    delete:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 구역 칭호 정의 삭제(보유자가 있으면 409)"
      security:
        - opaqueToken: []
      parameters:
        - name: titleDefId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "204":
          description: No Content
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: Not Found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "Conflict - 보유자가 있는 정의는 삭제 불가"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-titles/{titleDefId}/holders:
    get:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 구역 칭호 보유자 목록(페이징)"
      security:
        - opaqueToken: []
      parameters:
        - name: titleDefId
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: page
          in: query
          required: false
          schema:
            type: integer
            default: 1
        - name: size
          in: query
          required: false
          schema:
            type: integer
            default: 20
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneTitleHolderPageEnvelope"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: Not Found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-event-stats:
    get:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 회차·슬롯별 운영 통계(roundId 또는 from/to 중 하나 필수)"
      security:
        - opaqueToken: []
      parameters:
        - name: roundId
          in: query
          required: false
          schema:
            type: string
            format: uuid
        - name: from
          in: query
          required: false
          schema:
            type: string
            format: date-time
        - name: to
          in: query
          required: false
          schema:
            type: string
            format: date-time
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneEventStatsEnvelope"
        "400":
          description: "Bad Request - roundId와 from/to가 모두 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: "Not Found - roundId로 지정한 회차가 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-event-audits:
    get:
      tags:
        - Zone Event
      summary: "ROLE_ADMIN/MANAGER. 운영자 행위 감사 이력 조회(페이징)"
      security:
        - opaqueToken: []
      parameters:
        - name: resourceType
          in: query
          required: false
          schema:
            type: string
        - name: resourceId
          in: query
          required: false
          schema:
            type: string
            format: uuid
        - name: actorId
          in: query
          required: false
          schema:
            type: string
            format: uuid
        - name: from
          in: query
          required: false
          schema:
            type: string
            format: date-time
        - name: to
          in: query
          required: false
          schema:
            type: string
            format: date-time
        - name: page
          in: query
          required: false
          schema:
            type: integer
            default: 1
        - name: size
          in: query
          required: false
          schema:
            type: integer
            default: 20
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminZoneEventAuditPageEnvelope"
        "401":
          description: Unauthorized
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: Forbidden
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
```
**주의:** `Zone Event` 태그명·`opaqueToken` 시큐리티 스킴 이름은 반드시 Step 1에서 확인한 실제 값을 쓴다(추측하지 말고 기존 블록에서 그대로 복사). 다르면 스키마 검증은 통과해도 문서 렌더링이 어색해진다.

- [ ] **Step 3: `components.schemas`에 요청/응답 스키마 + Envelope 추가**

`components.schemas` 블록 안, 기존 `AdminRewardPayout*` 스키마 근처에 추가:

```yaml
    AdminZoneTitleCreateRequest:
      type: object
      required: [zoneId, tier, requiredSuccessCount, titleName, style, color]
      properties:
        zoneId: { type: string, example: "SUYEONG_NAMGU" }
        tier: { type: integer, example: 1 }
        requiredSuccessCount: { type: integer, example: 1 }
        titleName: { type: string, example: "탐방가" }
        style: { type: string, example: "chip" }
        color: { type: string, example: "#000000" }
    AdminZoneTitleUpdateRequest:
      type: object
      required: [retroactive, expectedRevision]
      properties:
        titleName: { type: string, nullable: true, example: "탐방왕" }
        requiredSuccessCount: { type: integer, nullable: true, example: 3 }
        retroactive: { type: boolean, example: true }
        expectedRevision: { type: integer, format: int64, example: 0 }
    AdminZoneTitleDef:
      type: object
      properties:
        titleDefId: { type: string, format: uuid }
        titleCode: { type: string, example: "SUYEONG_NAMGU_T1" }
        zoneId: { type: string }
        tier: { type: integer }
        requiredSuccessCount: { type: integer }
        titleName: { type: string }
        style: { type: string }
        color: { type: string }
        holderCount: { type: integer, format: int64 }
        revision: { type: integer, format: int64 }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    AdminZoneTitleDefEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "칭호 정의 생성" }
        data:
          $ref: "#/components/schemas/AdminZoneTitleDef"
    AdminZoneTitleDefListEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "칭호 정의 목록" }
        data:
          type: array
          items:
            $ref: "#/components/schemas/AdminZoneTitleDef"
    AdminZoneTitleHolderItem:
      type: object
      properties:
        userTitleId: { type: string, format: uuid }
        userId: { type: string, format: uuid }
        nickname: { type: string, nullable: true }
        email: { type: string, nullable: true }
        earnedAt: { type: string, format: date-time }
        equipped: { type: boolean }
        revokedAt: { type: string, format: date-time, nullable: true }
    AdminZoneTitleHolderPage:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: "#/components/schemas/AdminZoneTitleHolderItem"
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }
        hasNext: { type: boolean }
    AdminZoneTitleHolderPageEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "칭호 보유자 목록" }
        data:
          $ref: "#/components/schemas/AdminZoneTitleHolderPage"
    AdminZoneEventStatsTopContent:
      type: object
      nullable: true
      properties:
        participationId: { type: string, format: uuid }
        likeCount: { type: integer, format: int64 }
    AdminZoneEventStatsItem:
      type: object
      properties:
        roundId: { type: string, format: uuid }
        slotId: { type: string, format: uuid }
        zoneId: { type: string }
        eventId: { type: string, format: uuid, nullable: true }
        joinedCount: { type: integer, format: int64 }
        submittedCount: { type: integer, format: int64 }
        submissionAttemptCount: { type: integer, format: int64 }
        successCount: { type: integer, format: int64 }
        failCount: { type: integer, format: int64 }
        pendingReviewCount: { type: integer, format: int64 }
        successRate:
          type: number
          format: double
          description: "successCount / submittedCount (submittedCount가 0이면 0)"
        openReportCount: { type: integer, format: int64 }
        basePaidCount: { type: integer, format: int64 }
        specialSentCount: { type: integer, format: int64 }
        topContent:
          $ref: "#/components/schemas/AdminZoneEventStatsTopContent"
        newTitleGrantCount: { type: integer, format: int64 }
    AdminZoneEventStats:
      type: object
      properties:
        slots:
          type: array
          items:
            $ref: "#/components/schemas/AdminZoneEventStatsItem"
    AdminZoneEventStatsEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "운영 통계" }
        data:
          $ref: "#/components/schemas/AdminZoneEventStats"
    AdminZoneEventAuditItem:
      type: object
      properties:
        auditId: { type: string, format: uuid }
        actorId: { type: string, format: uuid }
        action: { type: string, example: "PATCH_TITLE_DEF" }
        targetType: { type: string, example: "ZONE_TITLE_DEF" }
        targetId: { type: string, format: uuid, nullable: true }
        detail:
          type: object
          additionalProperties: true
        createdAt: { type: string, format: date-time }
    AdminZoneEventAuditPage:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: "#/components/schemas/AdminZoneEventAuditItem"
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }
        hasNext: { type: boolean }
    AdminZoneEventAuditPageEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "감사 이력 목록" }
        data:
          $ref: "#/components/schemas/AdminZoneEventAuditPage"
```

- [ ] **Step 4: YAML 파싱 검증**

프로젝트 루트가 아니라 스크래치 디렉터리에서 검증(프로젝트에 `node_modules`를 만들지 않기 위해):
```bash
mkdir -p /tmp/yaml-check && cd /tmp/yaml-check
npm install js-yaml --no-save --silent
node -e "
const yaml = require('js-yaml');
const fs = require('fs');
const doc = yaml.load(fs.readFileSync('/c/dev/bu-ting-backend-245/src/main/resources/static/docs/openapi3.yaml', 'utf8'));
const paths = [
  '/api/v1/admin/zone-titles',
  '/api/v1/admin/zone-titles/{titleDefId}',
  '/api/v1/admin/zone-titles/{titleDefId}/holders',
  '/api/v1/admin/zone-event-stats',
  '/api/v1/admin/zone-event-audits',
];
for (const p of paths) {
  if (!doc.paths[p]) throw new Error('Missing path: ' + p);
}
const schemas = [
  'AdminZoneTitleCreateRequest','AdminZoneTitleUpdateRequest','AdminZoneTitleDef',
  'AdminZoneTitleDefEnvelope','AdminZoneTitleDefListEnvelope','AdminZoneTitleHolderItem',
  'AdminZoneTitleHolderPage','AdminZoneTitleHolderPageEnvelope','AdminZoneEventStatsTopContent',
  'AdminZoneEventStatsItem','AdminZoneEventStats','AdminZoneEventStatsEnvelope',
  'AdminZoneEventAuditItem','AdminZoneEventAuditPage','AdminZoneEventAuditPageEnvelope',
];
for (const s of schemas) {
  if (!doc.components.schemas[s]) throw new Error('Missing schema: ' + s);
}
console.log('OK: all paths and schemas present, YAML parses cleanly.');
"
```
Expected: `OK: all paths and schemas present, YAML parses cleanly.` 에러가 나면(들여쓰기, `$ref` 오타 등) 고치고 다시 실행한다.

- [ ] **Step 5: 애플리케이션이 정적 리소스로 정상 서빙하는지 간단 확인(선택)**

Flyway/DB가 필요 없는 정적 파일이므로 파싱 검증(Step 4)으로 충분하다. 별도 서버 기동은 하지 않는다.

- [ ] **Step 6: 커밋**

```bash
cd /c/dev/bu-ting-backend-245
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(zonetitle,zoneevent): openapi3.yaml에 칭호 정의·운영 통계·감사 이력 API 문서화"
```

---

## Task 12: 최종 검증 — `./gradlew check` 전체 통과

**Files:** 없음(코드 변경 없음 — 검증 및 커버리지 갭 보강만). 갭이 발견되면 해당 파일을 수정.

이슈 완료 조건의 "테스트가 추가되거나 기존 테스트가 통과한다"와 검증 방법(`./gradlew check`)을 최종 확인한다. `check`는 `test` + `spotlessJavaCheck`(Google Java Format) + `jacocoTestCoverageVerification`(라인 커버리지 100%)을 모두 강제한다 — 각 태스크에서 `spotlessApply`/`test`만 돌렸으므로 전체 브랜치를 합친 뒤에만 드러나는 커버리지 갭이 있을 수 있다(예: Task 3의 `requireMonotonic`에서 `excludeId`가 null인 분기와 non-null인 분기가 각각 다른 태스크의 테스트에서만 커버되는데 실제로는 문제없이 합쳐지는 경우, 또는 `AdminZoneEventStatsService.buildSlotStats`의 `topContent`가 있는/없는 두 분기 중 하나가 테스트로 덮이지 않은 경우).

- [ ] **Step 1: 전체 테스트 실행**

```bash
cd /c/dev/bu-ting-backend-245
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
./gradlew test --no-daemon -g "C:\gradle-home"
```
Expected: BUILD SUCCESSFUL, 전체 테스트 그린(기존 967+ 건 + 이번 이슈에서 추가한 건).

- [ ] **Step 2: `check` 실행(Spotless + Jacoco 100%)**

```bash
./gradlew check --no-daemon -g "C:\gradle-home"
```
Expected: BUILD SUCCESSFUL.

**만약 `jacocoTestCoverageVerification`이 실패하면:**

```bash
cd /c/dev/bu-ting-backend-245
# HTML 리포트에서 "not covered"(class="nc") 표시가 있는 라인을 찾는다
grep -rl 'class="nc"' build/reports/jacoco/test/html/com.butingbe.domain.zonetitle*/ build/reports/jacoco/test/html/com.butingbe.domain.zoneevent*/ build/reports/jacoco/test/html/com.butingbe.domain.reward*/ 2>/dev/null
```
리포트를 열어 정확히 어떤 라인·분기가 빠졌는지 확인한 뒤:
- 진짜 도달 가능한 분기인데 테스트가 없으면 → 해당 태스크의 테스트 파일에 케이스를 추가한다(예: `requireMonotonic`의 `sibling.getTier() > tier` 분기가 생성 테스트에만 있고 수정 테스트에는 없다면 Task 3의 서비스 테스트에 "높은 단계 정의가 이미 있는 상태에서 낮은 requiredSuccessCount로 수정하면 400" 케이스를 추가).
- 실제로 도달 불가능해진 코드(예: 어떤 이전 가드 때문에 특정 분기가 항상 스킵됨)라면 → 죽은 코드를 **삭제**한다(테스트를 늘려 억지로 커버하지 않는다).

**만약 `spotlessJavaCheck`가 실패하면:**
```bash
./gradlew spotlessApply -g "C:\gradle-home" --no-daemon
```
후 다시 `check`를 실행한다.

- [ ] **Step 3: 갭을 고쳤다면 재실행해 최종 그린 확인**

```bash
./gradlew check --no-daemon -g "C:\gradle-home"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 갭 보강 커밋(변경이 있었을 때만)**

```bash
git add -A
git status  # 의도한 파일만 스테이징됐는지 확인 후
git commit -m "test(zonetitle,zoneevent): close coverage gaps for issue #245"
```

- [ ] **Step 5: 브랜치를 원본 저장소(한글 경로)에 반영**

이 워크트리(`C:\dev\bu-ting-backend-245`)는 `C:\Users\조준연\Desktop\bu-ting-backend`와 같은 로컬 저장소를 공유하는 git worktree이므로, 커밋은 이미 그 저장소의 `feature/245-zone-title-admin-stats-audit` 브랜치에 반영되어 있다. 별도 push/PR은 사용자 지시에 따라 수행한다(이 계획 문서 자체에는 포함하지 않음 — PR 생성은 실행 시점에 별도로 확인받는다).

---
