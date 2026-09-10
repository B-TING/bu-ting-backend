# 보상·정산 관리 API (이슈 #244) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AdminRewardPayoutController`(`/admin/reward-payouts`, 이미 `release-hold` 하나만 있음)에 나머지 8개 관리자 엔드포인트(목록·상세·수정·일괄 확정·일괄 일정·발송 3단계 기록·재시도)를 추가하고, BASE 지급 건 자동 생성과 실제 포인트·배지 원장 반영을 연결한다.

**Architecture:** `RewardPayout`(TOP_LIKE)·`BaseRewardPayout`(BASE) 엔티티에 상태 전이 메서드를 추가하고, 기존 `AdminRewardPayoutService`(#243, try-RewardPayout-then-BaseRewardPayout 조회 관례)를 확장한다. 목록은 두 리포지토리를 각각 JPQL로 조회해 서비스 레이어에서 병합한다. `mark-sent`(BASE)에서만 같은 트랜잭션으로 `RewardService.grantBaseReward()`를 호출해 `reward_grant`에 원자적으로 반영한다. `AdminZoneEventReviewService.approve()`를 수정해 사진 승인 시 BASE 지급 건을 자동 생성한다.

**Tech Stack:** Java 21 / Spring Boot 4 (Jackson 3 — `tools.jackson.*`) / Spring Data JPA / PostgreSQL + Flyway / JUnit 5 + Mockito + Testcontainers(`AbstractContainerTest`).

## Global Constraints

- **Branch:** `feature/244-reward-payout-management`, 이미 `origin/dev`(`b13e326`, PR #255 머지 후)에서 분기해 `C:\dev\bu-ting-backend-244`(ASCII 경로 워크트리)에 만들어져 있다. 설계 스펙(`docs/superpowers/specs/2026-09-09-reward-payout-management-design.md`)도 이미 이 브랜치에 커밋됨(`075f31d`). 이 워크트리에서 계속 작업한다.
- **로컬 테스트는 ASCII 경로에서만 정상 동작한다**(한글 경로 Gradle 워커 JVM 문제). 이 워크트리 자체가 이미 ASCII 경로이므로 `cd /c/dev/bu-ting-backend-244` 후 `./gradlew test --no-daemon -g "C:\gradle-home" --tests "..."`로 실행한다. `check`는 100% Jacoco 커버리지 + Spotless(Google Java Format)를 강제하므로 최종 태스크에서 `./gradlew spotlessApply` 먼저 실행 후 `./gradlew check`.
- **`payoutId`는 프리픽스 없는 UUID.** 조회는 항상 `rewardPayoutRepository.findById(id)`를 먼저 시도하고 없으면 `baseRewardPayoutRepository.findById(id)`로 폴백한다(기존 `releaseHold`와 동일 관례. 신규 코드 전부 이 패턴을 따른다).
- **revision 검증은 이중 방어.** 서비스 메서드 진입 직후 `!entity.getRevision().equals(expectedRevision)`이면 즉시 `ConflictException("error.reward.payout.stale_revision")`. 이후 엔티티를 변경하고 `repository.saveAndFlush(entity)`를 `try/catch(ObjectOptimisticLockingFailureException)`로 감싸 같은 예외를 던진다(기존 `releaseTopLikeHold`/`releaseBaseHold`와 동일).
- **엔티티 변경 메서드는 상태를 세팅만 한다.** "지금 이 상태에서 호출 가능한가"는 항상 서비스 레이어가 먼저 검사해 `ConflictException`을 던진다(엔티티는 방어적 가드를 갖지 않는다 — 기존 `hold()`/`releaseHold()`와 동일 스타일).
- **Idempotency-Key는 선택 헤더.** 값이 있으면 `IdempotencyService.findReplay(key, endpoint, fingerprint)` → 있으면 그 JSON을 역직렬화해 반환, 없으면 처리 후 `IdempotencyService.save(key, endpoint, fingerprint, result)`. fingerprint는 `payoutId + ":" + 주요 필드...` 조합(기존 `releaseHold`의 `payoutId + ":" + request.note() + ":" + request.expectedRevision()` 참고).
- **감사 로그**: 모든 변경 액션마다 `ZoneEventAuditLogRepository.save(ZoneEventAuditLog.builder().actorId(user.id()).action("...").targetType("REWARD_PAYOUT").targetId(payoutId).detail(Map.of(...)).build())`. action 이름: `PATCH_PAYOUT`, `CONFIRM_PAYOUT`, `SCHEDULE_PAYOUT`, `MARK_MAIL_SENT`, `MARK_INFO_COLLECTED`, `MARK_SENT`, `RETRY_PAYOUT`. 일괄 처리는 대상 건마다 한 번씩 기록한다(건별 추적 용이성).
- **`AdminZoneEventReviewService.approve()`가 이미 BASE·TOP_LIKE 리포지토리를 참조하지 않으므로 새 의존성(`BaseRewardPayoutRepository`, `ZoneEventReportRepository`)을 생성자에 추가한다.** `ZoneEventReportRepository`는 이미 `hasUnresolvedReports(UUID participationId)` 기본 메서드를 갖고 있다(`existsByParticipationIdAndStatusIn`을 `UNRESOLVED_STATUSES`로 고정한 편의 메서드).
- **참여 숨김 해제(`AdminReviewService.unhide()`)를 BASE 보류 해제에 연결하는 것은 이번 이슈 범위 밖이다**(#243이 의도적으로 남겨 둔 레거시 갭 — 손대지 않는다).
- Jackson 3: 새 코드가 `ObjectMapper`/Jackson 예외를 다루면 `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException`만 사용한다(Jackson 2 금지, `@JsonProperty` 등 `com.fasterxml.jackson.annotation.*`만 예외).
- Korean 사용자 문구는 기존 `AdminRewardPayoutService`/`AdminZoneEventReportService`의 어조를 그대로 따른다.
- 메시지 키는 4개 로케일 파일(`messages.properties`, `messages_en.properties`, `messages_ja.properties`, `messages_zh.properties`, 현재 모두 정확히 66줄)에 **같은 순서로** 이어 붙인다.
- 매 태스크 끝에서 새/수정 파일만 `git add`, 커밋 메시지는 `feat(reward): ...` 형식.
- `TimestampEntity.getCreatedAt()`/`getUpdatedAt()`은 `LocalDateTime`이지 `OffsetDateTime`이 아니다(다른 필드들과 타입이 다름 — DTO에 그대로 반영).

---

## File Structure

**New files:**
- `src/main/resources/db/migration/V47__reward_payout_memo.sql`
- `src/main/java/com/butingbe/global/error/exception/BulkPayoutConflictException.java`
- `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutDetailResDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutListItemResDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutPageResDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutBulkResultResDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutUpdateReqDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkConfirmReqDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkScheduleReqDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkReqDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkSentReqDto.java`
- `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutRetryReqDto.java`
- Tests mirroring each task under `src/test/java/...` (see per-task lists).

**Modified files:**
- `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java` — `memo`/`reference` 컬럼 + `assignReward`/`updateMemo`/`updateSchedule`/`confirm`/`markMailSent`/`markInfoCollected`/`markSent`/`retry`.
- `src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java` — `memo` 컬럼 + `updateReward`/`updateMemo`/`updateSchedule`/`confirm`/`markSent`/`retry`.
- `src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java` — `searchForAdmin(...)`.
- `src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java` — `searchForAdmin(...)`.
- `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java` — 8개 메서드 추가.
- `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java` — 8개 엔드포인트 추가.
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java` — `approve()`에서 BASE 지급 건 자동 생성.
- `src/main/java/com/butingbe/global/error/GlobalExceptionHandler.java` — `BulkPayoutConflictException` 핸들러.
- `src/main/resources/messages*.properties`(4개) — 신규 키.
- `src/main/resources/static/docs/openapi3.yaml` — 8개 엔드포인트 + 스키마.
- 테스트: `AdminZoneEventReviewServiceTest`(`approveMarksSuccessWithoutReward` 수정), `AdminRewardPayoutServiceTest`(대폭 확장), `AdminRewardPayoutControllerTest`(대폭 확장).

---

## Task 1: 사진 승인 시 BASE 지급 건 자동 생성

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`(기존 파일 수정)

**Interfaces:**
- Produces: `approve()` 호출 시 `event.getBaseReward() != null`이고 해당 참여의 `BaseRewardPayout`이 아직 없으면 `PENDING_CONFIRM` 상태로 생성. 생성 시점에 `reportRepository.hasUnresolvedReports(participationId)`면 즉시 `hold()`.
- Consumes: `BaseRewardPayoutRepository`(신규 의존성), `ZoneEventReportRepository`(신규 의존성, 이미 `hasUnresolvedReports` 보유).

기존 테스트 `approveMarksSuccessWithoutReward`가 `baseRewardPayoutRepository.count()).isZero()`를 단언하는데, 이는 "아직 구현 안 됨"을 표시하던 자리다. 이번 태스크로 이 단언이 뒤집힌다 — 테스트명·단언을 함께 고친다.

- [ ] **Step 1: 기존 테스트를 새 기대값으로 고치고, 새 테스트 2개 추가**

`AdminZoneEventReviewServiceTest.java`의 203번째 줄 근처, `approveMarksSuccessWithoutReward` 테스트를 다음으로 교체:

```java
  @Test
  @DisplayName("승인하면 SUCCESS·앨범 공개가 되고 보상은 아직 지급하지 않는다(BASE 지급 건만 PENDING_CONFIRM으로 생성)")
  void approveMarksSuccessAndCreatesBasePayout() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    titleDefRepository.save(
        ZoneTitleDef.builder()
            .titleCode(event.getZoneId() + "_T1")
            .zoneId(event.getZoneId())
            .tier(1)
            .requiredSuccessCount(1)
            .titleName(event.getZoneId() + " T1")
            .style("chip")
            .color("#000000")
            .build());

    AdminReviewDecisionResDto result =
        reviewService.approve(
            operator,
            p.getId(),
            new ReviewApproveReqDto(submission.getId(), submission.getRevision()),
            null);

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.SUCCESS);
    assertThat(rewardPayoutRepository.count()).isZero();
    var basePayout = baseRewardPayoutRepository.findByParticipationId(p.getId()).orElseThrow();
    assertThat(basePayout.getStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.PENDING_CONFIRM);
    assertThat(basePayout.getHoldStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.PayoutHoldStatus.NONE);
    assertThat(basePayout.getReward()).isEqualTo(event.getBaseReward());
    assertThat(result.newlyAwardedTitles()).isNotEmpty();
    assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(p.getUserId())).isZero();
  }

  @Test
  @DisplayName("이벤트에 baseReward가 없으면 BASE 지급 건을 만들지 않는다")
  void approveWithoutBaseRewardCreatesNoPayout() {
    ZoneEvent noRewardEvent =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(zoneEventTypeRepository.findAll().get(0))
                .title("보상 없는 이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(noRewardEvent)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("장소")
                .latitude(35.1)
                .longitude(129.1)
                .radiusM(100)
                .build());
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(noRewardEvent)
                .userId(savedUser("참가자2").getId())
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ZoneEventSubmission submission =
        submissionRepository.save(
            ZoneEventSubmission.builder()
                .participation(p)
                .attemptNo(1)
                .target(target)
                .placeName(target.getPlaceName())
                .targetLatitude(target.getLatitude())
                .targetLongitude(target.getLongitude())
                .radiusM(target.getRadiusM())
                .mediaFileKey("uploads/images/photo2.jpg")
                .gpsLat(35.1)
                .gpsLng(129.1)
                .capturedAt(OffsetDateTime.now())
                .build());
    ReflectionTestUtils.setField(p, "currentSubmissionId", submission.getId());
    p = participationRepository.save(p);

    reviewService.approve(
        operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision()), null);

    assertThat(baseRewardPayoutRepository.findByParticipationId(p.getId())).isEmpty();
  }

  @Test
  @DisplayName("승인 시점에 미해결 신고가 있으면 새로 만든 BASE 지급 건도 바로 보류 상태로 생성한다")
  void approveCreatesHeldBasePayoutWhenUnresolvedReportExists() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    reportRepository.save(
        com.butingbe.domain.zoneevent.entity.ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(com.butingbe.domain.zoneevent.entity.ReportReasonCode.SPAM)
            .build());

    reviewService.approve(
        operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision()), null);

    var basePayout = baseRewardPayoutRepository.findByParticipationId(p.getId()).orElseThrow();
    assertThat(basePayout.getHoldStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.PayoutHoldStatus.HELD_REPORT);
  }
```

`@Autowired private ZoneEventReportRepository reportRepository;` 필드가 이 테스트 클래스에 아직 없으면 다른 `@Autowired` 필드들 옆에 추가하고 `import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;`도 추가한다(파일 상단 import 블록에 없으면 추가 — `grep -n "ZoneEventReportRepository" src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`로 먼저 확인).

- [ ] **Step 2: 테스트 실행해 실패 확인**

```bash
cd /c/dev/bu-ting-backend-244
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"
```
Expected: `approveMarksSuccessAndCreatesBasePayout`는 `baseRewardPayoutRepository.findByParticipationId(...)`가 비어 있어 `NoSuchElementException`으로 실패. 나머지 두 신규 테스트는 컴파일은 되지만(아직 아무것도 안 만드니) 자연히 통과할 수도 있음 — 상관없다, Step 3에서 실제 동작을 채운다.

- [ ] **Step 3: `AdminZoneEventReviewService.approve()` 수정**

`AdminZoneEventReviewService.java` 상단 import에 추가:
```java
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
```

필드에 추가:
```java
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final ZoneEventReportRepository reportRepository;
```

`approve()` 메서드에서 `participation.markSuccess();` 바로 다음 줄에 삽입:
```java
    participation.markSuccess();
    createBaseRewardPayoutIfNeeded(participation);
    submission.approve(user.id());
```

새 private 메서드 추가(클래스 하단, `flushSubmission` 근처):
```java
  /** 이벤트에 baseReward가 있고 아직 이 참여의 BASE 지급 건이 없으면 PENDING_CONFIRM으로 생성한다. 미해결 신고가 있으면 즉시 보류. */
  private void createBaseRewardPayoutIfNeeded(ZoneEventParticipation participation) {
    var baseReward = participation.getEvent().getBaseReward();
    if (baseReward == null) {
      return;
    }
    if (baseRewardPayoutRepository.findByParticipationId(participation.getId()).isPresent()) {
      return;
    }
    BaseRewardPayout payout =
        BaseRewardPayout.builder()
            .participationId(participation.getId())
            .reward(baseReward)
            .build();
    if (reportRepository.hasUnresolvedReports(participation.getId())) {
      payout.hold();
    }
    baseRewardPayoutRepository.save(payout);
  }
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"
```
Expected: 전부 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java
git commit -m "feat(reward): auto-create BASE reward payout on photo approval"
```

---

## Task 2: `GET /admin/reward-payouts/{payoutId}` — 상세 조회

**Files:**
- Create: `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutDetailResDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Test: `src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java`(확장)
- Test: `src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.detail(AuthenticatedUser user, UUID payoutId) -> AdminRewardPayoutDetailResDto`.
- Consumes: `RewardPayoutRepository.findById`, `BaseRewardPayoutRepository.findById`(둘 다 기존), `ZoneEventParticipationRepository`(BASE의 eventId를 참여를 통해 구하기 위해 신규 의존성으로 주입).

- [ ] **Step 1: DTO 작성**

```java
package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** payoutType은 TOP_LIKE 또는 BASE. 상대 타입에 없는 필드는 null(예: BASE는 rankN·likeCountAtClose·mailedAt 등이 null). */
public record AdminRewardPayoutDetailResDto(
    String payoutId,
    String payoutType,
    String eventId,
    String participationId,
    Integer rankN,
    Long likeCountAtClose,
    RewardSnapshot reward,
    String status,
    String holdStatus,
    OffsetDateTime scheduledAt,
    String confirmedBy,
    OffsetDateTime confirmedAt,
    OffsetDateTime mailedAt,
    OffsetDateTime informationCollectedAt,
    OffsetDateTime sentAt,
    OffsetDateTime paidAt,
    String reference,
    String memo,
    String failureCode,
    Long revision,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminRewardPayoutDetailResDto ofTopLike(RewardPayout p) {
    return new AdminRewardPayoutDetailResDto(
        p.getId().toString(),
        "TOP_LIKE",
        p.getEventId().toString(),
        p.getParticipationId().toString(),
        p.getRankN(),
        p.getLikeCountAtClose(),
        p.getReward(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getConfirmedBy() == null ? null : p.getConfirmedBy().toString(),
        p.getConfirmedAt(),
        p.getMailedAt(),
        p.getInformationCollectedAt(),
        p.getSentAt(),
        null,
        p.getReference(),
        p.getMemo(),
        p.getFailureCode(),
        p.getRevision(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  public static AdminRewardPayoutDetailResDto ofBase(BaseRewardPayout p, UUID eventId) {
    return new AdminRewardPayoutDetailResDto(
        p.getId().toString(),
        "BASE",
        eventId == null ? null : eventId.toString(),
        p.getParticipationId().toString(),
        null,
        null,
        p.getReward(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getConfirmedBy() == null ? null : p.getConfirmedBy().toString(),
        p.getConfirmedAt(),
        null,
        null,
        null,
        p.getPaidAt(),
        null,
        p.getMemo(),
        p.getFailureCode(),
        p.getRevision(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
```

이 태스크는 `RewardPayout.getMemo()`/`getReference()`, `BaseRewardPayout.getMemo()`를 참조하지만 그 필드는 Task 4에서 추가된다. 컴파일이 안 되므로, **이 태스크에서 `RewardPayout`/`BaseRewardPayout`에 `memo`(둘 다) / `reference`(RewardPayout만) 컬럼과 게터만 먼저 추가한다**(마이그레이션 포함, 상태 전이 메서드는 Task 4에서). 아래 Step 1.5로 삽입.

- [ ] **Step 1.5: 마이그레이션 + memo/reference 컬럼 선행 추가**

`src/main/resources/db/migration/V47__reward_payout_memo.sql`:
```sql
ALTER TABLE reward_payout ADD COLUMN memo TEXT;
ALTER TABLE reward_payout ADD COLUMN reference VARCHAR(255);
ALTER TABLE base_reward_payout ADD COLUMN memo TEXT;
```

`RewardPayout.java`에 필드 추가(다른 `@Column` 필드들 사이, `failureCode` 다음 `revision` 이전):
```java
  @Column(columnDefinition = "text")
  private String memo;

  @Column(length = 255)
  private String reference;
```

`BaseRewardPayout.java`에 필드 추가(`failureCode` 다음 `revision` 이전):
```java
  @Column(columnDefinition = "text")
  private String memo;
```

(`@Getter`가 클래스 레벨에 이미 있으므로 게터는 자동 생성됨. 빌더에는 넣지 않는다 — 생성 시점엔 항상 null.)

- [ ] **Step 2: 서비스에 `detail()` 추가**

`AdminRewardPayoutService.java` 상단 import에 추가:
```java
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
```

필드 추가:
```java
  private final ZoneEventParticipationRepository participationRepository;
```

메서드 추가:
```java
  @Transactional(readOnly = true)
  public AdminRewardPayoutDetailResDto detail(AuthenticatedUser user, UUID payoutId) {
    operatorAuthorization.requireOperator(user);
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      return AdminRewardPayoutDetailResDto.ofTopLike(topLike.get());
    }
    BaseRewardPayout base =
        baseRewardPayoutRepository
            .findById(payoutId)
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
    UUID eventId =
        participationRepository
            .findById(base.getParticipationId())
            .map(ZoneEventParticipation::getEvent)
            .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
            .orElse(null);
    return AdminRewardPayoutDetailResDto.ofBase(base, eventId);
  }
```

- [ ] **Step 3: 컨트롤러에 엔드포인트 추가**

`AdminRewardPayoutController.java`에 import 추가:
```java
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import org.springframework.web.bind.annotation.GetMapping;
```

메서드 추가:
```java
  @GetMapping("/{payoutId}")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID payoutId) {
    return ResponseEntity.ok(
        ApiResponse.success("지급 건 상세", adminRewardPayoutService.detail(user, payoutId)));
  }
```

- [ ] **Step 4: 서비스 테스트 추가**

`AdminRewardPayoutServiceTest.java`에 추가(클래스 하단, `participation()` 헬퍼 위):
```java
  @Test
  @DisplayName("TOP_LIKE 지급 상세를 조회한다")
  void detailReturnsTopLike() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    var result = payoutService.detail(operator, payout.getId());

    assertThat(result.payoutType()).isEqualTo("TOP_LIKE");
    assertThat(result.eventId()).isEqualTo(event.getId().toString());
    assertThat(result.rankN()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("PENDING_ASSIGN");
  }

  @Test
  @DisplayName("BASE 지급 상세는 참여를 통해 eventId를 채워 돌려준다")
  void detailReturnsBaseWithEventId() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    var result = payoutService.detail(operator, payout.getId());

    assertThat(result.payoutType()).isEqualTo("BASE");
    assertThat(result.eventId()).isEqualTo(event.getId().toString());
    assertThat(result.rankN()).isNull();
    assertThat(result.status()).isEqualTo("PENDING_CONFIRM");
  }

  @Test
  @DisplayName("없는 지급 건 상세 조회는 404다")
  void detailNotFound() {
    assertThatThrownBy(() -> payoutService.detail(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }
```

- [ ] **Step 5: 컨트롤러 테스트 추가**

`AdminRewardPayoutControllerTest.java`에 import 추가:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
```

테스트 추가:
```java
  @Test
  @DisplayName("지급 상세 조회 200")
  void detail() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.detail(any(), eq(payoutId)))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "PENDING_ASSIGN", "NONE",
                null, null, null, null, null, null, null, null, null, null, 0L, null, null));

    mockMvc
        .perform(get("/admin/reward-payouts/{id}", payoutId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.payoutType").value("TOP_LIKE"));
  }
```

- [ ] **Step 6: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration/V47__reward_payout_memo.sql src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutDetailResDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): GET admin reward payout detail (+ memo/reference columns)"
```

---

## Task 3: `GET /admin/reward-payouts` — 목록 조회

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java`
- Modify: `src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutListItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutPageResDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Modify: `src/main/resources/messages*.properties`(4개)
- Test: `src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java`(확장)
- Test: `src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.list(user, roundId, eventId, rewardReason, status, holdStatus, scheduledFrom, scheduledTo, page, size) -> AdminRewardPayoutPageResDto`.
- Consumes: `RewardPayoutRepository.searchForAdmin(...)`, `BaseRewardPayoutRepository.searchForAdmin(...)`(둘 다 신규).

`status`가 주어졌는데 `rewardReason`이 없으면(두 타입의 상태 enum이 다르므로 모호함) `IllegalArgumentException("error.reward.payout.status_requires_reward_reason")` — 400.

- [ ] **Step 1: 메시지 키 추가**

4개 파일 모두 67번째 줄(끝)에 이어서:
- `messages.properties`: `error.reward.payout.status_requires_reward_reason=status로 조회하려면 rewardReason(BASE 또는 TOP_LIKE)도 함께 지정해야 합니다.`
- `messages_en.properties`: `error.reward.payout.status_requires_reward_reason=Filtering by status requires rewardReason (BASE or TOP_LIKE) to also be set.`
- `messages_ja.properties`: `error.reward.payout.status_requires_reward_reason=statusで絞り込むにはrewardReason(BASEまたはTOP_LIKE)も指定してください。`
- `messages_zh.properties`: `error.reward.payout.status_requires_reward_reason=按status筛选时必须同时指定rewardReason(BASE或TOP_LIKE)。`

- [ ] **Step 2: 리포지토리 쿼리 추가**

`RewardPayoutRepository.java`를 다음으로 교체:
```java
package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardPayoutRepository extends JpaRepository<RewardPayout, UUID> {

  List<RewardPayout> findByEventId(UUID eventId);

  boolean existsByParticipationId(UUID participationId);

  Optional<RewardPayout> findByParticipationId(UUID participationId);

  @Query(
      value =
          "select r from RewardPayout r, ZoneEvent e "
              + "where r.eventId = e.id "
              + "and (:eventId is null or r.eventId = :eventId) "
              + "and (:roundId is null or e.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (:scheduledFrom is null or r.scheduledAt >= :scheduledFrom) "
              + "and (:scheduledTo is null or r.scheduledAt <= :scheduledTo) "
              + "order by r.createdAt desc",
      countQuery =
          "select count(r) from RewardPayout r, ZoneEvent e "
              + "where r.eventId = e.id "
              + "and (:eventId is null or r.eventId = :eventId) "
              + "and (:roundId is null or e.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (:scheduledFrom is null or r.scheduledAt >= :scheduledFrom) "
              + "and (:scheduledTo is null or r.scheduledAt <= :scheduledTo)")
  Page<RewardPayout> searchForAdmin(
      @Param("eventId") UUID eventId,
      @Param("roundId") UUID roundId,
      @Param("status") RewardPayoutStatus status,
      @Param("holdStatus") PayoutHoldStatus holdStatus,
      @Param("scheduledFrom") OffsetDateTime scheduledFrom,
      @Param("scheduledTo") OffsetDateTime scheduledTo,
      Pageable pageable);
}
```

`BaseRewardPayoutRepository.java`를 다음으로 교체:
```java
package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaseRewardPayoutRepository extends JpaRepository<BaseRewardPayout, UUID> {

  Optional<BaseRewardPayout> findByParticipationId(UUID participationId);

  @Query(
      value =
          "select r from BaseRewardPayout r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (:scheduledFrom is null or r.scheduledAt >= :scheduledFrom) "
              + "and (:scheduledTo is null or r.scheduledAt <= :scheduledTo) "
              + "order by r.createdAt desc",
      countQuery =
          "select count(r) from BaseRewardPayout r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (:scheduledFrom is null or r.scheduledAt >= :scheduledFrom) "
              + "and (:scheduledTo is null or r.scheduledAt <= :scheduledTo)")
  Page<BaseRewardPayout> searchForAdmin(
      @Param("eventId") UUID eventId,
      @Param("roundId") UUID roundId,
      @Param("status") BaseRewardPayoutStatus status,
      @Param("holdStatus") PayoutHoldStatus holdStatus,
      @Param("scheduledFrom") OffsetDateTime scheduledFrom,
      @Param("scheduledTo") OffsetDateTime scheduledTo,
      Pageable pageable);
}
```

- [ ] **Step 3: DTO 작성**

`AdminRewardPayoutListItemResDto.java`:
```java
package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutListItemResDto(
    String payoutId,
    String payoutType,
    String participationId,
    String eventId,
    String status,
    String holdStatus,
    OffsetDateTime scheduledAt,
    RewardSnapshot reward,
    Long revision) {

  public static AdminRewardPayoutListItemResDto ofTopLike(RewardPayout p) {
    return new AdminRewardPayoutListItemResDto(
        p.getId().toString(),
        "TOP_LIKE",
        p.getParticipationId().toString(),
        p.getEventId().toString(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getReward(),
        p.getRevision());
  }

  public static AdminRewardPayoutListItemResDto ofBase(BaseRewardPayout p, UUID eventId) {
    return new AdminRewardPayoutListItemResDto(
        p.getId().toString(),
        "BASE",
        p.getParticipationId().toString(),
        eventId == null ? null : eventId.toString(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getReward(),
        p.getRevision());
  }
}
```

`AdminRewardPayoutPageResDto.java`:
```java
package com.butingbe.domain.reward.dto.response;

import java.util.List;

public record AdminRewardPayoutPageResDto(
    List<AdminRewardPayoutListItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
```

- [ ] **Step 4: 서비스 `list()` 추가**

`AdminRewardPayoutService.java` import 추가:
```java
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutListItemResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutPageResDto;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```

메서드 추가:
```java
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  @Transactional(readOnly = true)
  public AdminRewardPayoutPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      UUID eventId,
      String rewardReason,
      String status,
      String holdStatus,
      OffsetDateTime scheduledFrom,
      OffsetDateTime scheduledTo,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    if (status != null && !status.isBlank() && (rewardReason == null || rewardReason.isBlank())) {
      throw new IllegalArgumentException("error.reward.payout.status_requires_reward_reason");
    }
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    PayoutHoldStatus holdFilter =
        holdStatus == null || holdStatus.isBlank() ? null : PayoutHoldStatus.valueOf(holdStatus);

    if ("BASE".equals(rewardReason)) {
      BaseRewardPayoutStatus statusFilter =
          status == null || status.isBlank() ? null : BaseRewardPayoutStatus.valueOf(status);
      Page<BaseRewardPayout> result =
          baseRewardPayoutRepository.searchForAdmin(
              eventId, roundId, statusFilter, holdFilter, scheduledFrom, scheduledTo,
              PageRequest.of(pageNumber - 1, pageSize));
      List<AdminRewardPayoutListItemResDto> items = mapBaseItems(result.getContent());
      return new AdminRewardPayoutPageResDto(
          items, pageNumber, pageSize, result.getTotalElements(), result.getTotalPages(),
          pageNumber < result.getTotalPages());
    }
    if ("TOP_LIKE".equals(rewardReason)) {
      RewardPayoutStatus statusFilter =
          status == null || status.isBlank() ? null : RewardPayoutStatus.valueOf(status);
      Page<RewardPayout> result =
          rewardPayoutRepository.searchForAdmin(
              eventId, roundId, statusFilter, holdFilter, scheduledFrom, scheduledTo,
              PageRequest.of(pageNumber - 1, pageSize));
      List<AdminRewardPayoutListItemResDto> items =
          result.getContent().stream().map(AdminRewardPayoutListItemResDto::ofTopLike).toList();
      return new AdminRewardPayoutPageResDto(
          items, pageNumber, pageSize, result.getTotalElements(), result.getTotalPages(),
          pageNumber < result.getTotalPages());
    }

    // rewardReason 미지정: 두 리포지토리를 각각 무페이징 조회 후 병합·정렬·인메모리 페이징.
    List<RewardPayout> topLikeAll =
        rewardPayoutRepository
            .searchForAdmin(
                eventId, roundId, null, holdFilter, scheduledFrom, scheduledTo,
                org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    List<BaseRewardPayout> baseAll =
        baseRewardPayoutRepository
            .searchForAdmin(
                eventId, roundId, null, holdFilter, scheduledFrom, scheduledTo,
                org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    List<AdminRewardPayoutListItemResDto> merged = new ArrayList<>();
    merged.addAll(topLikeAll.stream().map(AdminRewardPayoutListItemResDto::ofTopLike).toList());
    merged.addAll(mapBaseItems(baseAll));
    merged.sort(
        Comparator.comparing(
                AdminRewardPayoutListItemResDto::scheduledAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());

    int totalElements = merged.size();
    int totalPages = Math.max(1, (int) Math.ceil(totalElements / (double) pageSize));
    int fromIndex = Math.min((pageNumber - 1) * pageSize, totalElements);
    int toIndex = Math.min(fromIndex + pageSize, totalElements);
    List<AdminRewardPayoutListItemResDto> pageItems = merged.subList(fromIndex, toIndex);
    return new AdminRewardPayoutPageResDto(
        pageItems, pageNumber, pageSize, totalElements, totalPages, pageNumber < totalPages);
  }

  private List<AdminRewardPayoutListItemResDto> mapBaseItems(List<BaseRewardPayout> rows) {
    var participations =
        participationRepository
            .findAllById(rows.stream().map(BaseRewardPayout::getParticipationId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ZoneEventParticipation::getId, Function.identity()));
    return rows.stream()
        .map(
            p -> {
              ZoneEventParticipation participation = participations.get(p.getParticipationId());
              UUID eventId = participation == null ? null : participation.getEvent().getId();
              return AdminRewardPayoutListItemResDto.ofBase(p, eventId);
            })
        .toList();
  }
```

`ZoneEvent` import는 이 태스크에서 직접 쓰이지 않으면(참여의 `getEvent().getId()`만 쓰므로) 추가하지 않아도 된다 — 실제 컴파일 시 미사용 import는 Spotless/체크스타일 경고가 될 수 있으니 위 목록에서 `ZoneEvent` import는 제외하고 작성한다.

- [ ] **Step 5: 컨트롤러 엔드포인트 추가**

`AdminRewardPayoutController.java` import 추가:
```java
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutPageResDto;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.RequestParam;
```

메서드 추가:
```java
  @GetMapping
  public ResponseEntity<ApiResponse<AdminRewardPayoutPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) String rewardReason,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String holdStatus,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime scheduledFrom,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime scheduledTo,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 목록",
            adminRewardPayoutService.list(
                user, roundId, eventId, rewardReason, status, holdStatus, scheduledFrom,
                scheduledTo, page, size)));
  }
```

- [ ] **Step 6: 서비스 테스트 추가**

```java
  @Test
  @DisplayName("rewardReason=BASE면 BASE 지급만 페이징 조회한다")
  void listFiltersByBaseRewardReason() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder().participationId(p2.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    rewardPayoutRepository.save(
        RewardPayout.builder()
            .eventId(event.getId())
            .participationId(participation().getId())
            .rankN(1)
            .likeCountAtClose(1L)
            .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
            .build());

    var result = payoutService.list(operator, null, null, "BASE", null, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).allMatch(i -> i.payoutType().equals("BASE"));
  }

  @Test
  @DisplayName("rewardReason 없이 조회하면 두 타입을 병합해서 돌려준다")
  void listMergesBothTypesWhenRewardReasonOmitted() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    rewardPayoutRepository.save(
        RewardPayout.builder()
            .eventId(event.getId())
            .participationId(p2.getId())
            .rankN(1)
            .likeCountAtClose(1L)
            .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
            .build());

    var result = payoutService.list(operator, null, null, null, null, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(i -> i.payoutType()).distinct().count()).isEqualTo(2);
  }

  @Test
  @DisplayName("rewardReason 없이 status만 지정하면 400이다(타입별 상태 enum이 달라 모호함)")
  void listRejectsStatusWithoutRewardReason() {
    assertThatThrownBy(
            () -> payoutService.list(operator, null, null, null, "CONFIRMED", null, null, null, 1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.reward.payout.status_requires_reward_reason");
  }

  @Test
  @DisplayName("BASE 목록 항목은 참여를 통해 eventId를 채운다")
  void listBaseItemsIncludeEventId() {
    ZoneEventParticipation p = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());

    var result = payoutService.list(operator, null, null, "BASE", null, null, null, null, 1, 20);

    assertThat(result.items().get(0).eventId()).isEqualTo(event.getId().toString());
  }
```

- [ ] **Step 7: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("지급 목록 조회 200")
  void list() throws Exception {
    when(adminRewardPayoutService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminRewardPayoutPageResDto(List.of(), 1, 20, 0, 1, false));

    mockMvc
        .perform(get("/admin/reward-payouts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }
```
(`import com.butingbe.domain.reward.dto.response.AdminRewardPayoutPageResDto;` 추가.)

- [ ] **Step 8: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/repository/RewardPayoutRepository.java src/main/java/com/butingbe/domain/reward/repository/BaseRewardPayoutRepository.java src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutListItemResDto.java src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutPageResDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): GET admin reward payout list (merged BASE/TOP_LIKE, paginated)"
```

---

## Task 4: `PATCH /admin/reward-payouts/{payoutId}` — 보상 설정·메모·일정

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java`
- Modify: `src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutUpdateReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Modify: `src/main/resources/messages*.properties`(4개)
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.update(user, payoutId, AdminRewardPayoutUpdateReqDto, idempotencyKey) -> AdminRewardPayoutDetailResDto`.
- Consumes: `RewardPayout.assignReward(RewardSnapshot)`, `RewardPayout.updateMemo(String)`, `RewardPayout.updateSchedule(OffsetDateTime)`, `BaseRewardPayout.updateReward(RewardSnapshot)`, `BaseRewardPayout.updateMemo(String)`, `BaseRewardPayout.updateSchedule(OffsetDateTime)`(전부 신규).

- [ ] **Step 1: 메시지 키 추가**

4개 파일 68번째 줄(끝)에 이어서:
- `messages.properties`: `error.reward.payout.reward_locked=이미 확정된 지급은 보상 항목을 바꿀 수 없습니다.`
- `messages_en.properties`: `error.reward.payout.reward_locked=You can't change the reward item after the payout is confirmed.`
- `messages_ja.properties`: `error.reward.payout.reward_locked=すでに確定した支給は報酬項目を変更できません。`
- `messages_zh.properties`: `error.reward.payout.reward_locked=已确认的发放不能更改奖励项目。`

- [ ] **Step 2: 엔티티에 전이 메서드 추가**

`RewardPayout.java`에 추가(클래스 하단, `fail` 메서드 다음):
```java
  /** 보상 항목을 설정·변경한다. PENDING_ASSIGN이었는데 상품 코드까지 채워지면 PENDING_CONFIRM으로 전진한다. */
  public void assignReward(RewardSnapshot reward) {
    this.reward = reward;
    if (this.status == RewardPayoutStatus.PENDING_ASSIGN
        && reward != null
        && reward.prizeRewardCode() != null) {
      this.status = RewardPayoutStatus.PENDING_CONFIRM;
    }
  }

  public void updateMemo(String memo) {
    this.memo = memo;
  }

  public void updateSchedule(OffsetDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }
```

`BaseRewardPayout.java`에 추가(클래스 하단, `releaseHold` 다음):
```java
  public void updateReward(RewardSnapshot reward) {
    this.reward = reward;
  }

  public void updateMemo(String memo) {
    this.memo = memo;
  }

  public void updateSchedule(OffsetDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }
```

- [ ] **Step 3: 요청 DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record AdminRewardPayoutUpdateReqDto(
    RewardSnapshot reward, String memo, OffsetDateTime scheduledAt, @NotNull Long expectedRevision) {}
```

- [ ] **Step 4: 서비스 `update()` 추가**

`AdminRewardPayoutService.java`에 추가:
```java
  private static final String UPDATE_ENDPOINT = "reward-payout-update";

  @Transactional
  public AdminRewardPayoutDetailResDto update(
      AuthenticatedUser user,
      UUID payoutId,
      AdminRewardPayoutUpdateReqDto request,
      String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        payoutId + ":" + request.reward() + ":" + request.memo() + ":" + request.scheduledAt()
            + ":" + request.expectedRevision();
    Optional<String> replay = idempotencyService.findReplay(idempotencyKey, UPDATE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      result = updateTopLike(topLike.get(), request);
    } else {
      BaseRewardPayout base =
          baseRewardPayoutRepository
              .findById(payoutId)
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      result = updateBase(base, request);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("PATCH_PAYOUT")
            .targetType("REWARD_PAYOUT")
            .targetId(payoutId)
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, UPDATE_ENDPOINT, fingerprint, result);
    return result;
  }

  private AdminRewardPayoutDetailResDto updateTopLike(
      RewardPayout payout, AdminRewardPayoutUpdateReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (request.reward() != null
        && payout.getStatus() != RewardPayoutStatus.PENDING_ASSIGN
        && payout.getStatus() != RewardPayoutStatus.PENDING_CONFIRM) {
      throw new ConflictException("error.reward.payout.reward_locked");
    }
    if (request.reward() != null) {
      payout.assignReward(request.reward());
    }
    if (request.memo() != null) {
      payout.updateMemo(request.memo());
    }
    if (request.scheduledAt() != null) {
      payout.updateSchedule(request.scheduledAt());
    }
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofTopLike(payout);
  }

  private AdminRewardPayoutDetailResDto updateBase(
      BaseRewardPayout payout, AdminRewardPayoutUpdateReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (request.reward() != null && payout.getStatus() != BaseRewardPayoutStatus.PENDING_CONFIRM) {
      throw new ConflictException("error.reward.payout.reward_locked");
    }
    if (request.reward() != null) {
      payout.updateReward(request.reward());
    }
    if (request.memo() != null) {
      payout.updateMemo(request.memo());
    }
    if (request.scheduledAt() != null) {
      payout.updateSchedule(request.scheduledAt());
    }
    UUID eventId =
        participationRepository
            .findById(payout.getParticipationId())
            .map(ZoneEventParticipation::getEvent)
            .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
            .orElse(null);
    try {
      baseRewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofBase(payout, eventId);
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto`.

- [ ] **Step 5: 컨트롤러 엔드포인트 추가**

```java
  @PatchMapping("/{payoutId}")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID payoutId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutUpdateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 건 수정", adminRewardPayoutService.update(user, payoutId, request, idempotencyKey)));
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto`, `org.springframework.web.bind.annotation.PatchMapping`.

- [ ] **Step 6: 서비스 테스트 추가**

```java
  @Test
  @DisplayName("PENDING_ASSIGN인 TOP_LIKE에 prizeRewardCode를 채우면 PENDING_CONFIRM으로 자동 전진한다")
  void updateAssignsRewardAndAdvancesTopLikeToPendingConfirm() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                new RewardSnapshot(null, null, 1, "COUPON_TOP"), "1등 상품 확정", null, payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("PENDING_CONFIRM");
    assertThat(result.memo()).isEqualTo("1등 상품 확정");
  }

```
`BaseRewardPayout.confirm(UUID)`은 Task 5에서 추가되는 메서드라 이 태스크 시점엔 아직 없다. "확정 후 reward 변경은 409", "확정 후에도 memo/scheduledAt은 변경 가능"을 검증하는 테스트 2개는 `confirm()`이 필요하므로 **이 태스크에서는 추가하지 않는다** — Task 5의 Step 6에서 전체 코드와 함께 추가한다. Task 4에서는 위 테스트 1개만 추가한다.

- [ ] **Step 7: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("지급 수정 200")
  void update() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.update(any(), eq(payoutId), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "PENDING_CONFIRM", "NONE",
                null, null, null, null, null, null, null, null, "메모", null, 1L, null, null));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/admin/reward-payouts/{id}", payoutId)
                .contentType("application/json")
                .content("{\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memo").value("메모"));
  }

  @Test
  @DisplayName("expectedRevision이 없으면 400")
  void updateValidation() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/admin/reward-payouts/{id}", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
```

- [ ] **Step 8: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: 새로 추가한 것 중 `updateAssignsRewardAndAdvancesTopLikeToPendingConfirm`, 컨트롤러 테스트 2개는 PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutUpdateReqDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): PATCH admin reward payout (reward/memo/schedule)"
```

---

## Task 5: `POST /admin/reward-payouts/bulk-confirm` — 일괄 확정

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java`
- Modify: `src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java`
- Create: `src/main/java/com/butingbe/global/error/exception/BulkPayoutConflictException.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkConfirmReqDto.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutBulkResultResDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Modify: `src/main/java/com/butingbe/global/error/GlobalExceptionHandler.java`
- Modify: `src/main/resources/messages*.properties`(4개)
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.bulkConfirm(user, AdminRewardPayoutBulkConfirmReqDto, idempotencyKey) -> AdminRewardPayoutBulkResultResDto`. `RewardPayout.confirm(UUID operatorId)`, `BaseRewardPayout.confirm(UUID operatorId)`(신규).
- Consumes: `BulkPayoutConflictException(String message, List<String> problemPayoutIds)`(신규, `OpenParticipationExistsException` 패턴을 따름).

- [ ] **Step 1: 메시지 키 추가**

4개 파일 69번째 줄(끝)에 이어서:
- `messages.properties`:
```
error.reward.payout.reward_not_assigned=아직 보상 항목이 확정되지 않아 지급을 확정할 수 없습니다.
error.reward.payout.bulk_conflict=일부 지급 건이 조건을 충족하지 않아 아무 것도 반영되지 않았습니다.
```
- `messages_en.properties`:
```
error.reward.payout.reward_not_assigned=The reward item hasn't been assigned yet, so this payout can't be confirmed.
error.reward.payout.bulk_conflict=Some payouts didn't meet the conditions, so nothing was applied.
```
- `messages_ja.properties`:
```
error.reward.payout.reward_not_assigned=まだ報酬項目が確定していないため、支給を確定できません。
error.reward.payout.bulk_conflict=一部の支給が条件を満たしていないため、何も反映されませんでした。
```
- `messages_zh.properties`:
```
error.reward.payout.reward_not_assigned=尚未确定奖励项目,无法确认发放。
error.reward.payout.bulk_conflict=部分发放不满足条件,未做任何更改。
```

- [ ] **Step 2: 예외 클래스 작성**

```java
package com.butingbe.global.error.exception;

import java.util.List;
import lombok.Getter;

/** 일괄 처리 중 일부 대상이 조건을 충족하지 않아 아무 것도 반영하지 않았을 때(all-or-nothing) 던진다. */
@Getter
public class BulkPayoutConflictException extends RuntimeException {

  private final List<String> problemPayoutIds;

  public BulkPayoutConflictException(String message, List<String> problemPayoutIds) {
    super(message);
    this.problemPayoutIds = problemPayoutIds;
  }
}
```

`GlobalExceptionHandler.java`에 import + 핸들러 추가(`OpenParticipationExistsException` 핸들러 바로 아래):
```java
  @ExceptionHandler(BulkPayoutConflictException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handleBulkPayoutConflict(
      BulkPayoutConflictException e, HttpServletRequest request) {
    log.warn("Bulk payout conflict: problemPayoutIds={}", e.getProblemPayoutIds());

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.fail(
                message(e.getMessage(), request), Map.of("problemPayoutIds", e.getProblemPayoutIds())));
  }
```
import 추가: `com.butingbe.global.error.exception.BulkPayoutConflictException;`

- [ ] **Step 3: 엔티티에 `confirm()` 추가**

`RewardPayout.java`(`assignReward` 다음):
```java
  /** PENDING_CONFIRM에서만 호출 가능(서비스가 상태를 먼저 검사한다). */
  public void confirm(UUID operatorId) {
    this.status = RewardPayoutStatus.CONFIRMED;
    this.confirmedBy = operatorId;
    this.confirmedAt = OffsetDateTime.now();
  }
```

`BaseRewardPayout.java`(`updateSchedule` 다음):
```java
  public void confirm(UUID operatorId) {
    this.status = BaseRewardPayoutStatus.CONFIRMED;
    this.confirmedBy = operatorId;
    this.confirmedAt = OffsetDateTime.now();
  }
```

- [ ] **Step 4: DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AdminRewardPayoutBulkConfirmReqDto(
    @NotEmpty List<String> payoutIds, @NotNull Map<String, Long> expectedRevisions) {}
```

```java
package com.butingbe.domain.reward.dto.response;

import java.util.List;

public record AdminRewardPayoutBulkResultResDto(List<String> processedPayoutIds) {}
```

- [ ] **Step 5: 서비스 `bulkConfirm()` 추가**

```java
  @Transactional
  public AdminRewardPayoutBulkResultResDto bulkConfirm(
      AuthenticatedUser user, AdminRewardPayoutBulkConfirmReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    List<UUID> ids = request.payoutIds().stream().map(UUID::fromString).toList();
    String fingerprint = ids + ":" + request.expectedRevisions();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, BULK_CONFIRM_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutBulkResultResDto.class);
    }

    List<String> problems = new ArrayList<>();
    for (UUID id : ids) {
      Long expected = request.expectedRevisions().get(id.toString());
      Optional<RewardPayout> topLike = rewardPayoutRepository.findById(id);
      if (topLike.isPresent()) {
        RewardPayout p = topLike.get();
        if (expected == null || !p.getRevision().equals(expected)) {
          problems.add(id.toString());
        } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
          problems.add(id.toString());
        } else if (p.getStatus() != RewardPayoutStatus.PENDING_CONFIRM) {
          problems.add(id.toString());
        }
        continue;
      }
      Optional<BaseRewardPayout> base = baseRewardPayoutRepository.findById(id);
      if (base.isEmpty()) {
        problems.add(id.toString());
        continue;
      }
      BaseRewardPayout p = base.get();
      if (expected == null || !p.getRevision().equals(expected)) {
        problems.add(id.toString());
      } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
        problems.add(id.toString());
      } else if (p.getStatus() != BaseRewardPayoutStatus.PENDING_CONFIRM) {
        problems.add(id.toString());
      }
    }
    if (!problems.isEmpty()) {
      throw new BulkPayoutConflictException("error.reward.payout.bulk_conflict", problems);
    }

    for (UUID id : ids) {
      rewardPayoutRepository
          .findById(id)
          .ifPresentOrElse(
              p -> {
                p.confirm(user.id());
                auditLogRepository.save(
                    ZoneEventAuditLog.builder()
                        .actorId(user.id())
                        .action("CONFIRM_PAYOUT")
                        .targetType("REWARD_PAYOUT")
                        .targetId(id)
                        .detail(Map.of("payoutType", "TOP_LIKE"))
                        .build());
              },
              () ->
                  baseRewardPayoutRepository
                      .findById(id)
                      .ifPresent(
                          p -> {
                            p.confirm(user.id());
                            auditLogRepository.save(
                                ZoneEventAuditLog.builder()
                                    .actorId(user.id())
                                    .action("CONFIRM_PAYOUT")
                                    .targetType("REWARD_PAYOUT")
                                    .targetId(id)
                                    .detail(Map.of("payoutType", "BASE"))
                                    .build());
                          }));
    }

    AdminRewardPayoutBulkResultResDto result =
        new AdminRewardPayoutBulkResultResDto(ids.stream().map(UUID::toString).toList());
    idempotencyService.save(idempotencyKey, BULK_CONFIRM_ENDPOINT, fingerprint, result);
    return result;
  }
```
상수 추가: `private static final String BULK_CONFIRM_ENDPOINT = "reward-payout-bulk-confirm";`
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto`, `com.butingbe.domain.reward.dto.response.AdminRewardPayoutBulkResultResDto`, `com.butingbe.global.error.exception.BulkPayoutConflictException`, `java.util.ArrayList`.

- [ ] **Step 6: 컨트롤러 엔드포인트 추가 + Task 4에서 미룬 서비스 테스트 2개 추가**

```java
  @PostMapping("/bulk-confirm")
  public ResponseEntity<ApiResponse<AdminRewardPayoutBulkResultResDto>> bulkConfirm(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutBulkConfirmReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 일괄 확정", adminRewardPayoutService.bulkConfirm(user, request, idempotencyKey)));
  }
```

`AdminRewardPayoutServiceTest.java`에 Task 4에서 미뤄 둔 2개 테스트(`update()`가 이제 `confirm()`을 쓸 수 있게 됐으므로 여기서 실제로 추가한다)와 이 태스크의 신규 테스트를 모두 추가:

```java
  @Test
  @DisplayName("이미 CONFIRMED인 지급의 reward를 바꾸려 하면 409다")
  void updateRewardAfterConfirmConflicts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        new RewardSnapshot(100, null, null, null), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.reward_locked");
  }

  @Test
  @DisplayName("CONFIRMED 이후에도 memo·scheduledAt은 바꿀 수 있다")
  void updateMemoAndScheduleAfterConfirmStillAllowed() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(1);

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                null, "발송 예정", schedule, payout.getRevision()),
            null);

    assertThat(result.memo()).isEqualTo("발송 예정");
    assertThat(result.scheduledAt()).isEqualTo(schedule);
  }

  @Test
  @DisplayName("일괄 확정: PENDING_CONFIRM인 건들을 모두 CONFIRMED로 바꾼다")
  void bulkConfirmsAllPendingConfirmPayouts() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout base =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    RewardPayout topLike =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p2.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLike.assignReward(new RewardSnapshot(null, null, 1, "COUPON_TOP"));
    rewardPayoutRepository.saveAndFlush(topLike);

    var result =
        payoutService.bulkConfirm(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                List.of(base.getId().toString(), topLike.getId().toString()),
                Map.of(base.getId().toString(), base.getRevision(), topLike.getId().toString(), topLike.getRevision())),
            null);

    assertThat(result.processedPayoutIds()).hasSize(2);
    assertThat(baseRewardPayoutRepository.findById(base.getId()).orElseThrow().getStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.CONFIRMED);
    assertThat(rewardPayoutRepository.findById(topLike.getId()).orElseThrow().getStatus())
        .isEqualTo(RewardPayoutStatus.CONFIRMED);
  }

  @Test
  @DisplayName("일괄 확정: 하나라도 조건 미충족이면 전부 반영하지 않고 문제 id 목록과 함께 409다")
  void bulkConfirmAllOrNothingOnConflict() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout ok =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    BaseRewardPayout held =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p2.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    held.hold();
    baseRewardPayoutRepository.saveAndFlush(held);

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        List.of(ok.getId().toString(), held.getId().toString()),
                        Map.of(ok.getId().toString(), ok.getRevision(), held.getId().toString(), held.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class)
        .satisfies(
            e ->
                assertThat(((com.butingbe.global.error.exception.BulkPayoutConflictException) e).getProblemPayoutIds())
                    .containsExactly(held.getId().toString()));
    assertThat(baseRewardPayoutRepository.findById(ok.getId()).orElseThrow().getStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.PENDING_CONFIRM);
  }

  @Test
  @DisplayName("일괄 확정: 아직 상품 코드가 없는 TOP_LIKE(PENDING_ASSIGN)는 문제 목록에 포함된다")
  void bulkConfirmRejectsUnassignedTopLike() {
    ZoneEventParticipation p = participation();
    RewardPayout topLike =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        List.of(topLike.getId().toString()),
                        Map.of(topLike.getId().toString(), topLike.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class);
  }
```

- [ ] **Step 7: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("일괄 확정 200")
  void bulkConfirm() throws Exception {
    when(adminRewardPayoutService.bulkConfirm(any(), any(), any()))
        .thenReturn(new AdminRewardPayoutBulkResultResDto(List.of()));

    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-confirm")
                .contentType("application/json")
                .content(
                    "{\"payoutIds\":[\"" + UUID.randomUUID() + "\"],\"expectedRevisions\":{}}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("일괄 확정: payoutIds가 비어있으면 400")
  void bulkConfirmValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-confirm")
                .contentType("application/json")
                .content("{\"payoutIds\":[],\"expectedRevisions\":{}}"))
        .andExpect(status().isBadRequest());
  }
```
(`import com.butingbe.domain.reward.dto.response.AdminRewardPayoutBulkResultResDto;` 추가.)

- [ ] **Step 8: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java src/main/java/com/butingbe/global/error/exception/BulkPayoutConflictException.java src/main/java/com/butingbe/global/error/GlobalExceptionHandler.java src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkConfirmReqDto.java src/main/java/com/butingbe/domain/reward/dto/response/AdminRewardPayoutBulkResultResDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): POST admin reward payout bulk-confirm (all-or-nothing)"
```

---

## Task 6: `POST /admin/reward-payouts/bulk-schedule` — 일괄 일정

**Files:**
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkScheduleReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.bulkSchedule(user, AdminRewardPayoutBulkScheduleReqDto, idempotencyKey) -> AdminRewardPayoutBulkResultResDto`.
- Consumes: `RewardPayout.updateSchedule`, `BaseRewardPayout.updateSchedule`(Task 4에서 이미 추가됨).

- [ ] **Step 1: DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AdminRewardPayoutBulkScheduleReqDto(
    @NotEmpty List<String> payoutIds,
    @NotNull OffsetDateTime scheduledAt,
    @NotNull Map<String, Long> expectedRevisions) {}
```

- [ ] **Step 2: 서비스 `bulkSchedule()` 추가**

```java
  private static final String BULK_SCHEDULE_ENDPOINT = "reward-payout-bulk-schedule";

  @Transactional
  public AdminRewardPayoutBulkResultResDto bulkSchedule(
      AuthenticatedUser user, AdminRewardPayoutBulkScheduleReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    List<UUID> ids = request.payoutIds().stream().map(UUID::fromString).toList();
    String fingerprint = ids + ":" + request.scheduledAt() + ":" + request.expectedRevisions();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, BULK_SCHEDULE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutBulkResultResDto.class);
    }

    List<String> problems = new ArrayList<>();
    for (UUID id : ids) {
      Long expected = request.expectedRevisions().get(id.toString());
      Optional<RewardPayout> topLike = rewardPayoutRepository.findById(id);
      if (topLike.isPresent()) {
        RewardPayout p = topLike.get();
        if (expected == null || !p.getRevision().equals(expected)) {
          problems.add(id.toString());
        } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
          problems.add(id.toString());
        } else if (p.getStatus() != RewardPayoutStatus.CONFIRMED) {
          problems.add(id.toString());
        }
        continue;
      }
      Optional<BaseRewardPayout> base = baseRewardPayoutRepository.findById(id);
      if (base.isEmpty()) {
        problems.add(id.toString());
        continue;
      }
      BaseRewardPayout p = base.get();
      if (expected == null || !p.getRevision().equals(expected)) {
        problems.add(id.toString());
      } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
        problems.add(id.toString());
      } else if (p.getStatus() != BaseRewardPayoutStatus.CONFIRMED) {
        problems.add(id.toString());
      }
    }
    if (!problems.isEmpty()) {
      throw new BulkPayoutConflictException("error.reward.payout.bulk_conflict", problems);
    }

    for (UUID id : ids) {
      rewardPayoutRepository
          .findById(id)
          .ifPresentOrElse(
              p -> {
                p.updateSchedule(request.scheduledAt());
                auditLogRepository.save(
                    ZoneEventAuditLog.builder()
                        .actorId(user.id())
                        .action("SCHEDULE_PAYOUT")
                        .targetType("REWARD_PAYOUT")
                        .targetId(id)
                        .detail(Map.of("payoutType", "TOP_LIKE", "scheduledAt", request.scheduledAt().toString()))
                        .build());
              },
              () ->
                  baseRewardPayoutRepository
                      .findById(id)
                      .ifPresent(
                          p -> {
                            p.updateSchedule(request.scheduledAt());
                            auditLogRepository.save(
                                ZoneEventAuditLog.builder()
                                    .actorId(user.id())
                                    .action("SCHEDULE_PAYOUT")
                                    .targetType("REWARD_PAYOUT")
                                    .targetId(id)
                                    .detail(
                                        Map.of(
                                            "payoutType", "BASE", "scheduledAt", request.scheduledAt().toString()))
                                    .build());
                          }));
    }

    AdminRewardPayoutBulkResultResDto result =
        new AdminRewardPayoutBulkResultResDto(ids.stream().map(UUID::toString).toList());
    idempotencyService.save(idempotencyKey, BULK_SCHEDULE_ENDPOINT, fingerprint, result);
    return result;
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto`.

- [ ] **Step 3: 컨트롤러 엔드포인트 추가**

```java
  @PostMapping("/bulk-schedule")
  public ResponseEntity<ApiResponse<AdminRewardPayoutBulkResultResDto>> bulkSchedule(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutBulkScheduleReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 일괄 일정", adminRewardPayoutService.bulkSchedule(user, request, idempotencyKey)));
  }
```

- [ ] **Step 4: 서비스 테스트 추가**

```java
  @Test
  @DisplayName("일괄 일정: CONFIRMED인 건들의 scheduledAt을 한 번에 바꾼다")
  void bulkSchedulesConfirmedPayouts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(3);

    var result =
        payoutService.bulkSchedule(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                List.of(payout.getId().toString()), schedule, Map.of(payout.getId().toString(), payout.getRevision())),
            null);

    assertThat(result.processedPayoutIds()).containsExactly(payout.getId().toString());
    assertThat(baseRewardPayoutRepository.findById(payout.getId()).orElseThrow().getScheduledAt())
        .isEqualTo(schedule);
  }

  @Test
  @DisplayName("일괄 일정: 아직 CONFIRMED가 아닌 건이 섞여 있으면 전부 반영하지 않고 409다")
  void bulkScheduleRejectsNotYetConfirmed() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout confirmed =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    confirmed.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(confirmed);
    BaseRewardPayout notConfirmed =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p2.getId()).reward(new RewardSnapshot(50, null, null, null)).build());

    assertThatThrownBy(
            () ->
                payoutService.bulkSchedule(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                        List.of(confirmed.getId().toString(), notConfirmed.getId().toString()),
                        OffsetDateTime.now().plusDays(1),
                        Map.of(
                            confirmed.getId().toString(), confirmed.getRevision(),
                            notConfirmed.getId().toString(), notConfirmed.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class);
    assertThat(baseRewardPayoutRepository.findById(confirmed.getId()).orElseThrow().getScheduledAt()).isNull();
  }
```

- [ ] **Step 5: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("일괄 일정 200")
  void bulkSchedule() throws Exception {
    when(adminRewardPayoutService.bulkSchedule(any(), any(), any()))
        .thenReturn(new AdminRewardPayoutBulkResultResDto(List.of()));

    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-schedule")
                .contentType("application/json")
                .content(
                    "{\"payoutIds\":[\""
                        + UUID.randomUUID()
                        + "\"],\"scheduledAt\":\"2026-10-01T00:00:00Z\",\"expectedRevisions\":{}}"))
        .andExpect(status().isOk());
  }
```

- [ ] **Step 6: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutBulkScheduleReqDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): POST admin reward payout bulk-schedule (all-or-nothing)"
```

---

## Task 7: `POST /admin/reward-payouts/mark-mail-sent`, `POST /admin/reward-payouts/mark-info-collected` — TOP_LIKE 전용 발송 단계

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Modify: `src/main/resources/messages*.properties`(4개)
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.markMailSent(user, AdminRewardPayoutMarkReqDto, idempotencyKey) -> AdminRewardPayoutDetailResDto`, `markInfoCollected(...)`(동일 시그니처). `RewardPayout.markMailSent(OffsetDateTime, String note)`, `markInfoCollected(OffsetDateTime, String note)`(신규).

- [ ] **Step 1: 메시지 키 추가**

4개 파일 71번째 줄(끝)에 이어서:
- `messages.properties`:
```
error.reward.payout.wrong_type=기본 보상(BASE) 지급 건에는 사용할 수 없는 API입니다.
error.reward.payout.invalid_state=현재 단계에서는 처리할 수 없습니다.
```
- `messages_en.properties`:
```
error.reward.payout.wrong_type=This API can't be used for a BASE reward payout.
error.reward.payout.invalid_state=This can't be processed at the current stage.
```
- `messages_ja.properties`:
```
error.reward.payout.wrong_type=基本報酬(BASE)の支給には使用できないAPIです。
error.reward.payout.invalid_state=現在の段階では処理できません。
```
- `messages_zh.properties`:
```
error.reward.payout.wrong_type=该接口不适用于基础奖励(BASE)发放。
error.reward.payout.invalid_state=当前阶段无法处理。
```

- [ ] **Step 2: 엔티티에 전이 메서드 추가**

`RewardPayout.java`(`confirm` 다음):
```java
  public void markMailSent(OffsetDateTime mailedAt, String note) {
    this.status = RewardPayoutStatus.MAIL_SENT;
    this.mailedAt = mailedAt;
    if (note != null) {
      this.memo = note;
    }
  }

  public void markInfoCollected(OffsetDateTime informationCollectedAt, String note) {
    this.status = RewardPayoutStatus.INFO_COLLECTED;
    this.informationCollectedAt = informationCollectedAt;
    if (note != null) {
      this.memo = note;
    }
  }
```

- [ ] **Step 3: DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutMarkReqDto(
    @NotNull UUID payoutId, OffsetDateTime at, String note, @NotNull Long expectedRevision) {}
```

- [ ] **Step 4: 서비스 메서드 2개 추가**

```java
  private static final String MARK_MAIL_SENT_ENDPOINT = "reward-payout-mark-mail-sent";
  private static final String MARK_INFO_COLLECTED_ENDPOINT = "reward-payout-mark-info-collected";

  @Transactional
  public AdminRewardPayoutDetailResDto markMailSent(
      AuthenticatedUser user, AdminRewardPayoutMarkReqDto request, String idempotencyKey) {
    return applyTopLikeOnlyStep(
        user,
        request,
        idempotencyKey,
        MARK_MAIL_SENT_ENDPOINT,
        "MARK_MAIL_SENT",
        RewardPayoutStatus.CONFIRMED,
        (payout, at, note) -> payout.markMailSent(at == null ? OffsetDateTime.now() : at, note));
  }

  @Transactional
  public AdminRewardPayoutDetailResDto markInfoCollected(
      AuthenticatedUser user, AdminRewardPayoutMarkReqDto request, String idempotencyKey) {
    return applyTopLikeOnlyStep(
        user,
        request,
        idempotencyKey,
        MARK_INFO_COLLECTED_ENDPOINT,
        "MARK_INFO_COLLECTED",
        RewardPayoutStatus.MAIL_SENT,
        (payout, at, note) -> payout.markInfoCollected(at == null ? OffsetDateTime.now() : at, note));
  }

  private AdminRewardPayoutDetailResDto applyTopLikeOnlyStep(
      AuthenticatedUser user,
      AdminRewardPayoutMarkReqDto request,
      String idempotencyKey,
      String endpoint,
      String auditAction,
      RewardPayoutStatus requiredStatus,
      TopLikeStepAction action) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        request.payoutId() + ":" + request.at() + ":" + request.note() + ":" + request.expectedRevision();
    Optional<String> replay = idempotencyService.findReplay(idempotencyKey, endpoint, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    if (baseRewardPayoutRepository.findById(request.payoutId()).isPresent()) {
      throw new ConflictException("error.reward.payout.wrong_type");
    }
    RewardPayout payout =
        rewardPayoutRepository
            .findById(request.payoutId())
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != requiredStatus) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    action.apply(payout, request.at(), request.note());
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(auditAction)
            .targetType("REWARD_PAYOUT")
            .targetId(payout.getId())
            .detail(Map.of("note", request.note() == null ? "" : request.note()))
            .build());
    AdminRewardPayoutDetailResDto result = AdminRewardPayoutDetailResDto.ofTopLike(payout);
    idempotencyService.save(idempotencyKey, endpoint, fingerprint, result);
    return result;
  }

  @FunctionalInterface
  private interface TopLikeStepAction {
    void apply(RewardPayout payout, OffsetDateTime at, String note);
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto`.

- [ ] **Step 5: 컨트롤러 엔드포인트 2개 추가**

```java
  @PostMapping("/mark-mail-sent")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> markMailSent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutMarkReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "메일 발송 기록", adminRewardPayoutService.markMailSent(user, request, idempotencyKey)));
  }

  @PostMapping("/mark-info-collected")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> markInfoCollected(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutMarkReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "개인정보 수집 기록", adminRewardPayoutService.markInfoCollected(user, request, idempotencyKey)));
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto`.

- [ ] **Step 6: 서비스 테스트 추가**

```java
  @Test
  @DisplayName("mark-mail-sent: CONFIRMED인 TOP_LIKE를 MAIL_SENT로 바꾼다")
  void marksMailSent() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markMailSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                payout.getId(), null, "메일 발송함", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("MAIL_SENT");
    assertThat(result.memo()).isEqualTo("메일 발송함");
    assertThat(result.mailedAt()).isNotNull();
  }

  @Test
  @DisplayName("mark-mail-sent: BASE 지급 건에 호출하면 409(wrong_type)다")
  void markMailSentRejectsBaseType() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.wrong_type");
  }

  @Test
  @DisplayName("mark-mail-sent: CONFIRMED가 아니면 409(invalid_state)다")
  void markMailSentRejectsWrongStatus() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-info-collected: MAIL_SENT인 TOP_LIKE를 INFO_COLLECTED로 바꾼다")
  void marksInfoCollected() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markInfoCollected(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                payout.getId(), null, "주소 수집 완료", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("INFO_COLLECTED");
    assertThat(result.informationCollectedAt()).isNotNull();
  }
```

- [ ] **Step 7: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("메일 발송 기록 200")
  void markMailSent() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.markMailSent(any(), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "MAIL_SENT", "NONE",
                null, null, null, null, null, null, null, null, null, null, 1L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-mail-sent")
                .contentType("application/json")
                .content(
                    "{\"payoutId\":\"" + payoutId + "\",\"note\":\"발송\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("MAIL_SENT"));
  }
```

- [ ] **Step 8: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkReqDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): POST admin reward payout mark-mail-sent / mark-info-collected"
```

---

## Task 8: `POST /admin/reward-payouts/mark-sent` — 실제 발송 완료 기록 (BASE는 여기서 reward_grant 원자 반영)

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java`
- Modify: `src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkSentReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.markSent(user, AdminRewardPayoutMarkSentReqDto, idempotencyKey) -> AdminRewardPayoutDetailResDto`. `RewardPayout.markSent(OffsetDateTime, String reference, String note)`(INFO_COLLECTED→SENT). `BaseRewardPayout.markSent(OffsetDateTime paidAt, String note)`(CONFIRMED→PAID).
- Consumes: `RewardService.grantBaseReward(UUID userId, UUID participationId, UUID eventId, Integer points, String badgeCode)`(기존, `com.butingbe.domain.reward.service.RewardService`) — 이미 UK 가드로 재호출 시 이중 지급 방지.

- [ ] **Step 1: 엔티티에 전이 메서드 추가**

`RewardPayout.java`(`markInfoCollected` 다음):
```java
  public void markSent(OffsetDateTime sentAt, String reference, String note) {
    this.status = RewardPayoutStatus.SENT;
    this.sentAt = sentAt;
    this.reference = reference;
    if (note != null) {
      this.memo = note;
    }
  }
```

`BaseRewardPayout.java`(`confirm` 다음):
```java
  public void markSent(OffsetDateTime paidAt, String note) {
    this.status = BaseRewardPayoutStatus.PAID;
    this.paidAt = paidAt;
    if (note != null) {
      this.memo = note;
    }
  }
```

- [ ] **Step 2: DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutMarkSentReqDto(
    @NotNull UUID payoutId,
    OffsetDateTime sentAt,
    String reference,
    String note,
    @NotNull Long expectedRevision) {}
```

- [ ] **Step 3: 서비스 `markSent()` 추가**

`AdminRewardPayoutService.java` 생성자 필드에 `RewardService`를 추가한다:
```java
  private final RewardService rewardService;
```
import: `com.butingbe.domain.reward.service.RewardService`(같은 패키지 다른 클래스지만 명시적 import 없이도 컴파일되지만, 스타일 일관성을 위해 명시하지 않아도 무방 — 실제로는 같은 패키지이므로 import 문 불필요, 그냥 필드 선언만 하면 된다).

```java
  private static final String MARK_SENT_ENDPOINT = "reward-payout-mark-sent";

  @Transactional
  public AdminRewardPayoutDetailResDto markSent(
      AuthenticatedUser user, AdminRewardPayoutMarkSentReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        request.payoutId() + ":" + request.sentAt() + ":" + request.reference() + ":"
            + request.note() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, MARK_SENT_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(request.payoutId());
    if (topLike.isPresent()) {
      result = markTopLikeSent(topLike.get(), request);
    } else {
      BaseRewardPayout base =
          baseRewardPayoutRepository
              .findById(request.payoutId())
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      result = markBaseSent(base, request);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("MARK_SENT")
            .targetType("REWARD_PAYOUT")
            .targetId(request.payoutId())
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, MARK_SENT_ENDPOINT, fingerprint, result);
    return result;
  }

  private AdminRewardPayoutDetailResDto markTopLikeSent(
      RewardPayout payout, AdminRewardPayoutMarkSentReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != RewardPayoutStatus.INFO_COLLECTED) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    payout.markSent(request.sentAt() == null ? OffsetDateTime.now() : request.sentAt(), request.reference(), request.note());
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofTopLike(payout);
  }

  private AdminRewardPayoutDetailResDto markBaseSent(
      BaseRewardPayout payout, AdminRewardPayoutMarkSentReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != BaseRewardPayoutStatus.CONFIRMED) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    ZoneEventParticipation participation =
        participationRepository
            .findById(payout.getParticipationId())
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    UUID eventId = participation.getEvent().getId();
    var reward = payout.getReward();
    rewardService.grantBaseReward(
        participation.getUserId(),
        payout.getParticipationId(),
        eventId,
        reward == null ? null : reward.points(),
        reward == null ? null : reward.badgeCode());
    payout.markSent(request.sentAt() == null ? OffsetDateTime.now() : request.sentAt(), request.note());
    try {
      baseRewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofBase(payout, eventId);
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto`, `com.butingbe.domain.reward.service.RewardService`는 이미 같은 패키지(`com.butingbe.domain.reward.service`) 소속이므로 import 불필요 — 필드 선언만 추가.

- [ ] **Step 4: 컨트롤러 엔드포인트 추가**

```java
  @PostMapping("/mark-sent")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> markSent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutMarkSentReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "발송 완료 기록", adminRewardPayoutService.markSent(user, request, idempotencyKey)));
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto`.

- [ ] **Step 5: 서비스 테스트 추가**

`AdminRewardPayoutServiceTest.java`에 `RewardGrantRepository`/`UserPointBalanceRepository` autowire 추가(원장 반영 검증용):
```java
  @Autowired private com.butingbe.domain.reward.repository.RewardGrantRepository rewardGrantRepository;
  @Autowired private com.butingbe.domain.reward.repository.UserPointBalanceRepository userPointBalanceRepository;
```

```java
  @Test
  @DisplayName("mark-sent(BASE): CONFIRMED를 PAID로 바꾸고 같은 트랜잭션에서 reward_grant·포인트 잔액에 원자적으로 반영한다")
  void marksBaseSentAndGrantsAtomically() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                payout.getId(), null, null, "지급 완료", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("PAID");
    assertThat(result.paidAt()).isNotNull();
    assertThat(
            rewardGrantRepository.existsByParticipationIdAndGrantReasonAndReward_Id(
                p.getId(),
                com.butingbe.domain.reward.entity.GrantReason.BASE,
                rewardCatalogRepository.findByCode("POINT_BASE").orElseThrow().getId()))
        .isTrue();
    assertThat(userPointBalanceRepository.findById(p.getUserId()).orElseThrow().getBalance())
        .isEqualTo(50);
  }

  @Test
  @DisplayName("mark-sent(BASE): 재시도로 두 번 호출돼도 이중 지급되지 않는다(UK 가드)")
  void marksBaseSentTwiceIsSafe() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    payoutService.markSent(
        operator,
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
            payout.getId(), null, null, "1차 발송", payout.getRevision()),
        null);
    payout = baseRewardPayoutRepository.findById(payout.getId()).orElseThrow();
    // FAILED로 되돌린 뒤 재시도 상황을 흉내낸다 (retry()는 Task 9에서 추가되므로 여기서는 직접 상태를 되돌린다).
    ReflectionTestUtils.setField(payout, "status", com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.CONFIRMED);
    baseRewardPayoutRepository.saveAndFlush(payout);

    payoutService.markSent(
        operator,
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
            payout.getId(), null, null, "2차 발송", payout.getRevision()),
        null);

    assertThat(
            rewardGrantRepository.countByParticipationIdAndGrantReasonAndReward_Id(
                p.getId(),
                com.butingbe.domain.reward.entity.GrantReason.BASE,
                rewardCatalogRepository.findByCode("POINT_BASE").orElseThrow().getId()))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): INFO_COLLECTED를 SENT로 바꾸고 reference를 저장한다")
  void marksTopLikeSent() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    payout.markInfoCollected(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                payout.getId(), null, "REF-001", "발송함", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("SENT");
    assertThat(result.reference()).isEqualTo("REF-001");
  }
```

`rewardCatalogRepository`가 아직 `AdminRewardPayoutServiceTest`에 autowire돼 있지 않으면 추가: `@Autowired private com.butingbe.domain.reward.repository.RewardCatalogRepository rewardCatalogRepository;`. `POINT_BASE` 카탈로그는 `V33__seed_reward_catalog.sql`로 이미 시드돼 있으므로 테스트 컨테이너 DB에 항상 존재한다(다른 테스트들처럼 `findByCode("POINT_BASE").orElseThrow()`로 바로 조회 가능).

`countByParticipationIdAndGrantReasonAndReward_Id`가 `RewardGrantRepository`에 없으면 이 태스크에서 추가한다(파일을 먼저 Read해서 이미 있는지 확인 — 없으면 `existsByParticipationIdAndGrantReasonAndReward_Id` 옆에 `long countByParticipationIdAndGrantReasonAndReward_Id(UUID participationId, GrantReason grantReason, UUID rewardId);` 추가).

- [ ] **Step 6: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("발송 완료 기록 200")
  void markSent() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.markSent(any(), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "BASE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), null, null, null, "PAID", "NONE",
                null, null, null, null, null, null,
                java.time.OffsetDateTime.now(), null, null, null, 1L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-sent")
                .contentType("application/json")
                .content(
                    "{\"payoutId\":\"" + payoutId + "\",\"note\":\"완료\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));
  }
```

- [ ] **Step 7: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS. `UserPointBalance`는 `userId` 자체가 `@Id`이므로 조회는 `userPointBalanceRepository.findById(userId)`(위 테스트 코드에 이미 반영됨) — `findByUserId`라는 메서드는 존재하지 않으니 만들지 않는다. `RewardGrantRepository.countByParticipationIdAndGrantReasonAndReward_Id`만 Step 5에서 새로 추가하면 된다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutMarkSentReqDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/main/java/com/butingbe/domain/reward/repository/RewardGrantRepository.java src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): POST admin reward payout mark-sent (BASE grants atomically)"
```

---

## Task 9: `POST /admin/reward-payouts/{payoutId}/retry` — 재시도

**Files:**
- Modify: `src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java`
- Modify: `src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java`
- Create: `src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutRetryReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java`
- Modify: `src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java`
- Test: `AdminRewardPayoutServiceTest.java`, `AdminRewardPayoutControllerTest.java`(확장)

**Interfaces:**
- Produces: `AdminRewardPayoutService.retry(user, UUID payoutId, AdminRewardPayoutRetryReqDto, idempotencyKey) -> AdminRewardPayoutDetailResDto`. `RewardPayout.retry()`, `BaseRewardPayout.retry()`(FAILED → CONFIRMED, `failureCode` null화).

- [ ] **Step 1: 엔티티에 `retry()` 추가**

`RewardPayout.java`(`markSent` 다음):
```java
  public void retry() {
    this.status = RewardPayoutStatus.CONFIRMED;
    this.failureCode = null;
  }
```

`BaseRewardPayout.java`(`markSent` 다음):
```java
  public void retry() {
    this.status = BaseRewardPayoutStatus.CONFIRMED;
    this.failureCode = null;
  }
```

- [ ] **Step 2: DTO 작성**

```java
package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminRewardPayoutRetryReqDto(String note, @NotNull Long expectedRevision) {}
```

- [ ] **Step 3: 서비스 `retry()` 추가**

```java
  private static final String RETRY_ENDPOINT = "reward-payout-retry";

  @Transactional
  public AdminRewardPayoutDetailResDto retry(
      AuthenticatedUser user, UUID payoutId, AdminRewardPayoutRetryReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = payoutId + ":" + request.note() + ":" + request.expectedRevision();
    Optional<String> replay = idempotencyService.findReplay(idempotencyKey, RETRY_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      RewardPayout p = topLike.get();
      if (!p.getRevision().equals(request.expectedRevision())) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      if (p.getStatus() != RewardPayoutStatus.FAILED) {
        throw new ConflictException("error.reward.payout.invalid_state");
      }
      p.retry();
      try {
        rewardPayoutRepository.saveAndFlush(p);
      } catch (ObjectOptimisticLockingFailureException e) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      result = AdminRewardPayoutDetailResDto.ofTopLike(p);
    } else {
      BaseRewardPayout p =
          baseRewardPayoutRepository
              .findById(payoutId)
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      if (!p.getRevision().equals(request.expectedRevision())) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      if (p.getStatus() != BaseRewardPayoutStatus.FAILED) {
        throw new ConflictException("error.reward.payout.invalid_state");
      }
      p.retry();
      UUID eventId =
          participationRepository
              .findById(p.getParticipationId())
              .map(ZoneEventParticipation::getEvent)
              .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
              .orElse(null);
      try {
        baseRewardPayoutRepository.saveAndFlush(p);
      } catch (ObjectOptimisticLockingFailureException e) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      result = AdminRewardPayoutDetailResDto.ofBase(p, eventId);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("RETRY_PAYOUT")
            .targetType("REWARD_PAYOUT")
            .targetId(payoutId)
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, RETRY_ENDPOINT, fingerprint, result);
    return result;
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto`.

- [ ] **Step 4: 컨트롤러 엔드포인트 추가**

```java
  @PostMapping("/{payoutId}/retry")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> retry(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID payoutId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid AdminRewardPayoutRetryReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("지급 재시도", adminRewardPayoutService.retry(user, payoutId, request, idempotencyKey)));
  }
```
import 추가: `com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto`.

- [ ] **Step 5: 서비스 테스트 추가**

```java
  @Test
  @DisplayName("retry: FAILED인 지급을 CONFIRMED로 되돌리고 failureCode를 지운다")
  void retriesFailedPayout() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    baseRewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.retry(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto("재시도", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("CONFIRMED");
    assertThat(result.failureCode()).isNull();
  }

  @Test
  @DisplayName("retry: FAILED가 아니면 409다")
  void retryRejectsNonFailedStatus() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p.getId()).reward(new RewardSnapshot(50, null, null, null)).build());

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }
```

`BaseRewardPayout`에 `fail(String)` 메서드가 없다 — `RewardPayout`에는 있지만 `BaseRewardPayout`에는 없으므로 이 태스크에서 함께 추가한다(`retry`와 대칭):
```java
  /** 지급 처리 실패(외부 발송/원장 반영 실패 등). */
  public void fail(String failureCode) {
    this.status = BaseRewardPayoutStatus.FAILED;
    this.failureCode = failureCode;
  }
```
`BaseRewardPayout.java`의 `updateSchedule` 다음, `confirm` 이전에 추가.

- [ ] **Step 6: 컨트롤러 테스트 추가**

```java
  @Test
  @DisplayName("재시도 200")
  void retry() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.retry(any(), eq(payoutId), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "BASE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), null, null, null, "CONFIRMED", "NONE",
                null, null, null, null, null, null, null, null, null, null, 2L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/{id}/retry", payoutId)
                .contentType("application/json")
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
  }
```

- [ ] **Step 7: 실행 확인**

```bash
./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminRewardPayoutServiceTest" --tests "*AdminRewardPayoutControllerTest"
```
Expected: PASS.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/reward/entity/RewardPayout.java src/main/java/com/butingbe/domain/reward/entity/BaseRewardPayout.java src/main/java/com/butingbe/domain/reward/dto/request/AdminRewardPayoutRetryReqDto.java src/main/java/com/butingbe/domain/reward/service/AdminRewardPayoutService.java src/main/java/com/butingbe/domain/reward/controller/AdminRewardPayoutController.java src/test/java/com/butingbe/domain/reward/service/AdminRewardPayoutServiceTest.java src/test/java/com/butingbe/domain/reward/controller/AdminRewardPayoutControllerTest.java
git commit -m "feat(reward): POST admin reward payout retry"
```

---

## Task 10: OpenAPI 문서화 + 전체 검증

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml`

**Interfaces:**
- Produces: 없음(문서만). `npm install js-yaml --no-save` 후 파싱 검증.

- [ ] **Step 1: 스키마 컴포넌트 추가**

`openapi3.yaml`의 `AdminRewardPayoutReleaseHoldEnvelope` 스키마(11733번째 줄 근처) 바로 다음에 추가:

```yaml
    AdminRewardPayoutDetail:
      type: object
      properties:
        payoutId: { type: string, format: uuid }
        payoutType: { type: string, enum: [TOP_LIKE, BASE] }
        eventId: { type: string, format: uuid, nullable: true }
        participationId: { type: string, format: uuid }
        rankN: { type: integer, nullable: true }
        likeCountAtClose: { type: integer, format: int64, nullable: true }
        reward:
          type: object
          nullable: true
          properties:
            points: { type: integer, nullable: true }
            badgeCode: { type: string, nullable: true }
            topN: { type: integer, nullable: true }
            prizeRewardCode: { type: string, nullable: true }
        status: { type: string }
        holdStatus: { type: string, enum: [NONE, HELD_REPORT] }
        scheduledAt: { type: string, format: date-time, nullable: true }
        confirmedBy: { type: string, format: uuid, nullable: true }
        confirmedAt: { type: string, format: date-time, nullable: true }
        mailedAt: { type: string, format: date-time, nullable: true }
        informationCollectedAt: { type: string, format: date-time, nullable: true }
        sentAt: { type: string, format: date-time, nullable: true }
        paidAt: { type: string, format: date-time, nullable: true }
        reference: { type: string, nullable: true }
        memo: { type: string, nullable: true }
        failureCode: { type: string, nullable: true }
        revision: { type: integer, format: int64 }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }

    AdminRewardPayoutDetailEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "지급 건 상세" }
        data:
          $ref: "#/components/schemas/AdminRewardPayoutDetail"

    AdminRewardPayoutListItem:
      type: object
      properties:
        payoutId: { type: string, format: uuid }
        payoutType: { type: string, enum: [TOP_LIKE, BASE] }
        participationId: { type: string, format: uuid }
        eventId: { type: string, format: uuid, nullable: true }
        status: { type: string }
        holdStatus: { type: string, enum: [NONE, HELD_REPORT] }
        scheduledAt: { type: string, format: date-time, nullable: true }
        reward:
          type: object
          nullable: true
        revision: { type: integer, format: int64 }

    AdminRewardPayoutPage:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: "#/components/schemas/AdminRewardPayoutListItem"
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }
        hasNext: { type: boolean }

    AdminRewardPayoutPageEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "지급 목록" }
        data:
          $ref: "#/components/schemas/AdminRewardPayoutPage"

    AdminRewardPayoutUpdateRequest:
      type: object
      properties:
        reward:
          type: object
          nullable: true
        memo: { type: string, nullable: true }
        scheduledAt: { type: string, format: date-time, nullable: true }
        expectedRevision: { type: integer, format: int64 }
      required: [expectedRevision]

    AdminRewardPayoutBulkConfirmRequest:
      type: object
      properties:
        payoutIds:
          type: array
          items: { type: string, format: uuid }
        expectedRevisions:
          type: object
          additionalProperties: { type: integer, format: int64 }
      required: [payoutIds, expectedRevisions]

    AdminRewardPayoutBulkScheduleRequest:
      type: object
      properties:
        payoutIds:
          type: array
          items: { type: string, format: uuid }
        scheduledAt: { type: string, format: date-time }
        expectedRevisions:
          type: object
          additionalProperties: { type: integer, format: int64 }
      required: [payoutIds, scheduledAt, expectedRevisions]

    AdminRewardPayoutBulkResult:
      type: object
      properties:
        processedPayoutIds:
          type: array
          items: { type: string, format: uuid }

    AdminRewardPayoutBulkResultEnvelope:
      type: object
      properties:
        success: { type: boolean, example: true }
        message: { type: string, example: "지급 일괄 확정" }
        data:
          $ref: "#/components/schemas/AdminRewardPayoutBulkResult"

    AdminRewardPayoutMarkRequest:
      type: object
      properties:
        payoutId: { type: string, format: uuid }
        at: { type: string, format: date-time, nullable: true }
        note: { type: string, nullable: true }
        expectedRevision: { type: integer, format: int64 }
      required: [payoutId, expectedRevision]

    AdminRewardPayoutMarkSentRequest:
      type: object
      properties:
        payoutId: { type: string, format: uuid }
        sentAt: { type: string, format: date-time, nullable: true }
        reference: { type: string, nullable: true }
        note: { type: string, nullable: true }
        expectedRevision: { type: integer, format: int64 }
      required: [payoutId, expectedRevision]

    AdminRewardPayoutRetryRequest:
      type: object
      properties:
        note: { type: string, nullable: true }
        expectedRevision: { type: integer, format: int64 }
      required: [expectedRevision]
```

- [ ] **Step 2: 경로 추가**

`/api/v1/admin/reward-payouts/{payoutId}/release-hold` 블록(5748번째 줄 근처) 바로 앞에 삽입(같은 태그 `Zone Event` 사용):

```yaml
  /api/v1/admin/reward-payouts:
    get:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 목록"
      description: "roundId/eventId/rewardReason(BASE|TOP_LIKE)/status/holdStatus/scheduledFrom/scheduledTo/page/size로 필터링합니다. rewardReason을 지정하지 않으면 BASE·TOP_LIKE를 병합해 돌려줍니다. status는 타입별 상태 enum이 달라 rewardReason 없이 단독으로는 지정할 수 없습니다(400)."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: roundId, in: query, schema: { type: string, format: uuid } }
        - { name: eventId, in: query, schema: { type: string, format: uuid } }
        - { name: rewardReason, in: query, schema: { type: string, enum: [BASE, TOP_LIKE] } }
        - { name: status, in: query, schema: { type: string } }
        - { name: holdStatus, in: query, schema: { type: string, enum: [NONE, HELD_REPORT] } }
        - { name: scheduledFrom, in: query, schema: { type: string, format: date-time } }
        - { name: scheduledTo, in: query, schema: { type: string, format: date-time } }
        - { name: page, in: query, schema: { type: integer } }
        - { name: size, in: query, schema: { type: integer } }
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutPageEnvelope"
        "400":
          description: "status를 rewardReason 없이 지정함"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: "운영 권한 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/{payoutId}:
    get:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 상세"
      description: "payoutId는 TOP_LIKE(RewardPayout) 또는 BASE(BaseRewardPayout) 중 하나를 가리킵니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: payoutId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "403":
          description: "운영 권한 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: "지급 건을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
    patch:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 건 보상·메모·일정 수정"
      description: "reward는 PENDING_ASSIGN(TOP_LIKE) 또는 PENDING_CONFIRM 상태에서만 변경 가능합니다(확정 후 변경 시 409). memo·scheduledAt은 FAILED/SENT/PAID가 아니면 언제든 변경 가능합니다. TOP_LIKE는 reward.prizeRewardCode가 채워지면 PENDING_ASSIGN에서 PENDING_CONFIRM으로 자동 전진합니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: payoutId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutUpdateRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "400":
          description: "유효성 실패"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: "운영 권한 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: "지급 건을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "확정 후 보상 변경 시도, 또는 오래된 expectedRevision"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/bulk-confirm:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 일괄 확정"
      description: "PENDING_CONFIRM이고 holdStatus=NONE인 건만 CONFIRMED로 바꿉니다. 하나라도 조건을 충족하지 않으면(오래된 revision, 보류 중, 상태 불일치, TOP_LIKE 품목 미정 등) 아무 것도 반영하지 않고 409와 함께 문제 payoutIds를 반환합니다(all-or-nothing)."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutBulkConfirmRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutBulkResultEnvelope"
        "400":
          description: "유효성 실패"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "403":
          description: "운영 권한 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "일부 건이 조건 미충족(all-or-nothing, problemPayoutIds 포함)"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/bulk-schedule:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 일괄 일정"
      description: "CONFIRMED이고 holdStatus=NONE인 건만 scheduledAt을 일괄 변경합니다. 하나라도 조건 미충족이면 아무 것도 반영하지 않고 409(problemPayoutIds 포함)."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutBulkScheduleRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutBulkResultEnvelope"
        "409":
          description: "일부 건이 조건 미충족(all-or-nothing, problemPayoutIds 포함)"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/mark-mail-sent:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 메일 발송 기록(TOP_LIKE 전용)"
      description: "CONFIRMED인 TOP_LIKE 지급을 MAIL_SENT로 바꿉니다. BASE 지급 건에 호출하면 409(wrong_type)."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutMarkRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "409":
          description: "잘못된 지급 타입, 현재 단계에서 처리 불가, 또는 오래된 revision"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/mark-info-collected:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 개인정보 수집 기록(TOP_LIKE 전용)"
      description: "MAIL_SENT인 TOP_LIKE 지급을 INFO_COLLECTED로 바꿉니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutMarkRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "409":
          description: "잘못된 지급 타입, 현재 단계에서 처리 불가, 또는 오래된 revision"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/mark-sent:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 실제 발송 완료 기록"
      description: "TOP_LIKE는 INFO_COLLECTED를 SENT로 바꿉니다. BASE는 CONFIRMED를 PAID로 바꾸면서 같은 트랜잭션에서 실제 포인트·배지 원장(reward_grant)에 원자적으로 반영합니다 — UI 기록만으로 PAID 처리하지 않습니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutMarkSentRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "409":
          description: "현재 단계에서 처리 불가, 보류 중, 또는 오래된 revision"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

  /api/v1/admin/reward-payouts/{payoutId}/retry:
    post:
      tags: [Zone Event]
      summary: "ROLE_ADMIN/MANAGER. 지급 재시도"
      description: "FAILED인 지급을 CONFIRMED로 되돌립니다(failureCode 초기화). BASE 재시도 후 mark-sent를 다시 호출해도 reward_grant의 UK 가드로 이중 지급되지 않습니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - { name: payoutId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/AdminRewardPayoutRetryRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminRewardPayoutDetailEnvelope"
        "404":
          description: "지급 건을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "FAILED가 아니거나 오래된 revision"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"

```

- [ ] **Step 3: YAML 파싱 검증**

```bash
cd /c/dev/bu-ting-backend-244
mkdir -p /tmp/yamlcheck && cd /tmp/yamlcheck && npm install js-yaml --no-save
node -e "require('js-yaml').load(require('fs').readFileSync('/c/dev/bu-ting-backend-244/src/main/resources/static/docs/openapi3.yaml', 'utf8')); console.log('OK')"
```
Expected: `OK` 출력(파싱 에러 없음).

- [ ] **Step 4: 포맷팅 + 전체 검증**

```bash
cd /c/dev/bu-ting-backend-244
./gradlew spotlessApply --no-daemon -g "C:\gradle-home"
./gradlew check --no-daemon -g "C:\gradle-home"
```
Expected: `BUILD SUCCESSFUL`. Jacoco 커버리지 100% 게이트를 통과해야 한다 — 만약 특정 라인(예: `TopLikeStepAction` 람다의 특정 분기, `mapBaseItems`의 빈 리스트 경로 등)이 커버되지 않으면 그 분기를 exercising하는 테스트를 추가한다(예: `list()`에서 BASE 결과가 0건일 때의 경로, `applyTopLikeOnlyStep`에서 `at` null일 때 `OffsetDateTime.now()` 폴백 분기 등 — 이미 위 테스트들이 대부분 `at=null`로 호출하므로 커버될 것이나, `./gradlew check`의 Jacoco 리포트(`build/reports/jacoco/test/html/index.html`)를 열어 미달 라인이 있으면 그 라인만 targeted로 테스트 추가).

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(reward): document admin reward payout management endpoints in openapi3.yaml"
```

---

## 최종 확인

전체 브랜치 diff가 이슈 #244의 체크리스트 9개 항목·완료 조건 3개를 전부 만족하는지 마지막으로 대조한다:
- [ ] 9개 엔드포인트 전부 구현(release-hold는 #243에서 이미 존재).
- [ ] BASE/TOP_LIKE가 서로 다른 건으로 관리되고 상태를 공유하지 않는다(Task 1~9 전체에서 `RewardPayout`/`BaseRewardPayout`을 항상 분리 처리).
- [ ] 일괄 처리 중 하나라도 신고 보류·상태 충돌이면 전체가 처리되지 않고 문제 payoutIds가 반환된다(Task 5, 6의 2-pass 검증).
- [ ] 서버가 reward_grant에 원자적으로 반영한다(Task 8의 `markBaseSent`가 같은 `@Transactional` 안에서 `grantBaseReward` 호출 후 상태 전이).
- [ ] 테스트 통과(Task 10의 `./gradlew check`).
