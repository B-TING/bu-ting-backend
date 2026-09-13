# 참여·재제출 사용자 API 확장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the already-modeled-but-unused `ZoneEventSubmission` entity into the real join/submit/review/history flow so users can resubmit after a rejection, each attempt keeps its own history row, targets are selectable per call, the event deadline is enforced, and uploaded media is validated more strictly (issue #240, `B-TING/bu-ting-backend#240`).

**Architecture:** No new tables/columns — `ZoneEventSubmission` and its repository already exist with the right shape (`attemptNo`, target/coordinate snapshot, `reviewStatus`). `ZoneEventParticipationService.join()` and `ZoneEventSubmitService.submit()` gain a `targetId` selection parameter, an `endsAt()` deadline check, and (submit only) real `ZoneEventSubmission` row creation per attempt. `ParticipationStatus.FAIL` is reused as "rejected, resubmittable until deadline" — `submit()` now accepts participations in `JOINED` or `FAIL`. `AdminReviewService.approve/reject` are updated to also transition the current `ZoneEventSubmission`, since review now judges submissions, not just participations. Query-side (`ZoneEventQueryService`, `ZoneEventParticipationQueryService`) surface the new data (multi-target list, `slotCode`, `deadline`, caller's `myParticipation`/`canResubmit`, per-participation submission history).

**Tech Stack:** Spring Boot, Spring Data JPA, Bean Validation, JUnit 5 + AssertJ + Mockito (pure-mock service tests) / Testcontainers Postgres (`AbstractContainerTest`, used by the two tests that already extend it) + MockMvc standalone (controller tests).

**Design doc:** `docs/superpowers/specs/2026-09-08-zone-event-participation-resubmit-design.md` — read it first for the "why" behind each decision below.

## Global Constraints

- `targetId` request fields are `java.util.UUID` (matches the existing `SwapTargetReqDto` precedent), not `String`.
- Every check that rejects a `targetId` (wrong event, not `ACTIVE`) throws `ResourceNotFoundException("error.zone_event.target_not_found")` — reuse the existing key, do not add a new one.
- Deadline check (`now >= event.endsAt()`) uses the new key `error.zone_event.ended`, mapped to `ConflictException` (409), added to all four locale files (`messages.properties`, `_en`, `_ja`, `_zh`).
- Every new/changed error path must have a message key in all four locale files, matching existing sibling entries' tone.
- Every changed controller endpoint's OpenAPI entry in `src/main/resources/static/docs/openapi3.yaml` must be updated in the same task that changes its request/response shape — do not defer to a separate documentation pass.
- Run `./gradlew test`/`./gradlew check` via the ASCII-path clone workaround before declaring any task's tests verified (Korean user-profile path breaks the Gradle worker JVM otherwise) — see Task 10 for the exact commands.
- Do not touch `AdminReviewController`'s endpoint contract, `ZoneEventParticipationController`'s `/participations/me` response shape, or `ParticipationResDto.mediaUrl` — all out of scope per the design doc's 범위 제외 section.

---

### Task 1: Repository additions

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuthTargetRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventSubmissionRepository.java`

**Interfaces:**
- Produces: `ZoneEventAuthTargetRepository.findByEvent_IdAndStatus(UUID eventId, ZoneEventTargetStatus status)` → `List<ZoneEventAuthTarget>` (consumed by Task 7).
- Produces: `ZoneEventSubmissionRepository.existsByMediaFileKey(String mediaFileKey)` → `boolean` (consumed by Task 5).
- Produces: `ZoneEventSubmissionRepository.findByParticipation_IdIn(List<UUID> participationIds)` → `List<ZoneEventSubmission>` (consumed by Task 8).

- [ ] **Step 1: Add the methods**

In `ZoneEventAuthTargetRepository.java`, add after `findByEvent_Id`:

```java
  /** 이벤트의 ACTIVE 타겟 전체(참여자가 고를 수 있는 목록). */
  List<ZoneEventAuthTarget> findByEvent_IdAndStatus(UUID eventId, ZoneEventTargetStatus status);
```

(`List` and `ZoneEventTargetStatus` are already imported in this file.)

In `ZoneEventSubmissionRepository.java`, replace the full file with:

```java
package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventSubmissionRepository extends JpaRepository<ZoneEventSubmission, UUID> {

  List<ZoneEventSubmission> findByParticipation_IdOrderByAttemptNoDesc(UUID participationId);

  Optional<ZoneEventSubmission> findFirstByParticipation_IdOrderByAttemptNoDesc(
      UUID participationId);

  long countByParticipation_Id(UUID participationId);

  /** 파일이 이미 다른 제출에 쓰였는지(재사용 방지). */
  boolean existsByMediaFileKey(String mediaFileKey);

  /** 이력 페이지의 참여 id 목록으로 배치 조회(N+1 방지). */
  List<ZoneEventSubmission> findByParticipation_IdIn(List<UUID> participationIds);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileJava --no-daemon -g "C:\\gradle-home"`
Expected: BUILD SUCCESSFUL (Spring Data derives the queries at runtime; compile just checks method syntax).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuthTargetRepository.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventSubmissionRepository.java
git commit -m "feat(zoneevent): add repository queries for target list, media reuse, and submission batch lookup"
```

---

### Task 2: i18n error messages (4 locales)

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_en.properties`
- Modify: `src/main/resources/messages_ja.properties`
- Modify: `src/main/resources/messages_zh.properties`

**Interfaces:**
- Produces: `error.zone_event.ended`, `error.zone_event.media.already_used`, `error.zone_event.media.stale` — consumed by Task 4 (ended), Task 5 (all three).

- [ ] **Step 1: Add to `messages.properties`** (after the existing `error.zone_event.media.forbidden` line):

```properties
error.zone_event.ended=이벤트 참여 가능 시간이 지났습니다.
error.zone_event.media.already_used=이미 다른 제출에 사용된 파일입니다.
error.zone_event.media.stale=업로드한 지 너무 오래된 파일입니다.
```

- [ ] **Step 2: Add to `messages_en.properties`** (same position):

```properties
error.zone_event.ended=The event's participation window has ended.
error.zone_event.media.already_used=This file has already been used in another submission.
error.zone_event.media.stale=This file was uploaded too long ago.
```

- [ ] **Step 3: Add to `messages_ja.properties`** (same position):

```properties
error.zone_event.ended=イベントの参加可能時間が過ぎました。
error.zone_event.media.already_used=すでに他の提出に使用されたファイルです。
error.zone_event.media.stale=アップロードから時間が経ちすぎたファイルです。
```

- [ ] **Step 4: Add to `messages_zh.properties`** (same position):

```properties
error.zone_event.ended=活动参与时间已结束。
error.zone_event.media.already_used=该文件已用于其他提交。
error.zone_event.media.stale=该文件上传时间过久。
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties
git commit -m "feat(zoneevent): add i18n messages for deadline and upload-hardening errors"
```

---

### Task 3: New response DTOs (`MyParticipationResDto`, `SubmissionHistoryItemResDto`)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/MyParticipationResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/SubmissionHistoryItemResDto.java`

**Interfaces:**
- Produces: `MyParticipationResDto(String participationId, String status, boolean canResubmit)` with `of(ZoneEventParticipation participation, boolean canResubmit)` — consumed by Task 7.
- Produces: `SubmissionHistoryItemResDto(String submissionId, int attemptNo, String targetId, String placeName, String mediaUrl, String reviewStatus, String rejectionReason, OffsetDateTime submittedAt, OffsetDateTime reviewedAt)` with `of(ZoneEventSubmission submission, String mediaUrl)` — consumed by Task 8.

These are pure additions (no existing file touched), so this task is safe to land standalone with no test needed beyond a compile check (both are exercised through Task 7/8's service tests once wired in).

- [ ] **Step 1: Create `MyParticipationResDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;

/** 이벤트 상세에 포함되는 로그인 사용자의 가장 최근 참여 요약. */
public record MyParticipationResDto(String participationId, String status, boolean canResubmit) {

  public static MyParticipationResDto of(
      ZoneEventParticipation participation, boolean canResubmit) {
    return new MyParticipationResDto(
        participation.getId().toString(), participation.getStatus().name(), canResubmit);
  }
}
```

- [ ] **Step 2: Create `SubmissionHistoryItemResDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.time.OffsetDateTime;

/** 내 참여 이력 한 항목에 포함되는 제출 시도 1건. attemptNo 내림차순으로 나열된다. */
public record SubmissionHistoryItemResDto(
    String submissionId,
    int attemptNo,
    String targetId,
    String placeName,
    String mediaUrl,
    String reviewStatus,
    String rejectionReason,
    OffsetDateTime submittedAt,
    OffsetDateTime reviewedAt) {

  public static SubmissionHistoryItemResDto of(ZoneEventSubmission submission, String mediaUrl) {
    return new SubmissionHistoryItemResDto(
        submission.getId().toString(),
        submission.getAttemptNo(),
        submission.getTarget().getId().toString(),
        submission.getPlaceName(),
        mediaUrl,
        submission.getReviewStatus().name(),
        submission.getRejectionReason(),
        submission.getSubmittedAt(),
        submission.getReviewedAt());
  }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileJava --no-daemon -g "C:\\gradle-home"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/response/MyParticipationResDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/SubmissionHistoryItemResDto.java
git commit -m "feat(zoneevent): add MyParticipationResDto and SubmissionHistoryItemResDto"
```

---

### Task 4: `targetId` selection + deadline + FAIL-dedup on join

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/request/ParticipationJoinReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationController.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationServiceTest.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationControllerTest.java`

**Interfaces:**
- Consumes: `ZoneEventAuthTargetRepository.findByIdAndEvent_Id` (already exists).
- Produces: `ZoneEventParticipationService.join(AuthenticatedUser user, UUID eventId, UUID targetId, double latitude, double longitude)` — signature gains `targetId` as the 3rd parameter (was 4-arg, now 5-arg). Consumed by the controller and by Task 7 indirectly (no — Task 7 doesn't call `join`; only the controller and this test call it).

This task changes a widely-called method signature, so it must land as one atomic unit (service + both test files) or the module won't compile.

- [ ] **Step 1: Write the failing tests — replace `ZoneEventParticipationServiceTest.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.exception.OpenParticipationExistsException;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZoneEventParticipationServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID OPEN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("22222222-0000-0000-0000-000000000009");
  private static final UUID TARGET_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final double IN_LAT = 35.1532;
  private static final double IN_LNG = 129.1182;
  private static final double OUT_LAT = 35.16;
  private static final double OUT_LNG = 129.13;

  @Mock private ZoneEventRepository zoneEventRepository;
  @Mock private ZoneEventAuthTargetRepository authTargetRepository;
  @Mock private ZoneEventParticipationRepository participationRepository;

  private ZoneEventParticipationService service;
  private AuthenticatedUser user;
  private ZoneEvent event;

  @BeforeEach
  void setUp() {
    service =
        new ZoneEventParticipationService(
            zoneEventRepository, authTargetRepository, participationRepository);
    user = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    event = event(ZoneEventStatus.ACTIVE, 1);
  }

  @Test
  @DisplayName("반경 이내면 JOINED 참여를 만들고 거리를 채운다")
  void joinWithinRadius() {
    stubActiveEventWithTarget();
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty());
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(
            EVENT_ID, USER_ID, ParticipationStatus.SUCCESS))
        .thenReturn(0L);
    when(participationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              ZoneEventParticipation p = invocation.getArgument(0);
              ReflectionTestUtils.setField(p, "id", OPEN_ID);
              return p;
            });

    ParticipationResDto result = service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG);

    assertThat(result.status()).isEqualTo("JOINED");
    assertThat(result.distanceM()).isEqualTo(28);
    assertThat(result.zoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(result.visibility()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("반경 밖이면 400(out_of_range)이고 거리를 담는다")
  void joinOutOfRange() {
    stubActiveEventWithTarget();

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, OUT_LAT, OUT_LNG))
        .isInstanceOf(ZoneEventOutOfRangeException.class)
        .satisfies(
            e ->
                assertThat(((ZoneEventOutOfRangeException) e).getDistanceMeters()).isEqualTo(1340));
  }

  @Test
  @DisplayName("이미 열린 참여가 있으면 409(already_open)에 기존 id를 담는다")
  void joinWhenOpenExists() {
    stubActiveEventWithTarget();
    ZoneEventParticipation open = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(open, "id", OPEN_ID);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.of(open));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(OpenParticipationExistsException.class)
        .satisfies(
            e ->
                assertThat(((OpenParticipationExistsException) e).getParticipationId())
                    .isEqualTo(OPEN_ID));
  }

  @Test
  @DisplayName("반려(FAIL)로 재제출 대기 중인 참여가 있어도 새 참여는 409다")
  void joinWhenFailedParticipationExists() {
    stubActiveEventWithTarget();
    ZoneEventParticipation failed = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(failed, "id", OPEN_ID);
    ReflectionTestUtils.setField(failed, "status", ParticipationStatus.FAIL);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.of(failed));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(OpenParticipationExistsException.class)
        .satisfies(
            e ->
                assertThat(((OpenParticipationExistsException) e).getParticipationId())
                    .isEqualTo(OPEN_ID));
  }

  @Test
  @DisplayName("성공 상한에 도달하면 409(limit_reached)다")
  void joinWhenLimitReached() {
    stubActiveEventWithTarget();
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty());
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(
            EVENT_ID, USER_ID, ParticipationStatus.SUCCESS))
        .thenReturn(1L);

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.limit_reached");
  }

  @Test
  @DisplayName("동시 요청으로 부분 UK를 위반하면 409(already_open)로 매핑한다")
  void joinConcurrentConflict() {
    stubActiveEventWithTarget();
    ZoneEventParticipation open = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(open, "id", OPEN_ID);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty(), Optional.of(open));
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(any(), any(), any()))
        .thenReturn(0L);
    when(participationRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("uk_zone_event_participation_open"));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(OpenParticipationExistsException.class)
        .satisfies(
            e ->
                assertThat(((OpenParticipationExistsException) e).getParticipationId())
                    .isEqualTo(OPEN_ID));
  }

  @Test
  @DisplayName("ACTIVE가 아니면 409(not_active)다")
  void joinWhenNotActive() {
    when(zoneEventRepository.findById(EVENT_ID))
        .thenReturn(Optional.of(event(ZoneEventStatus.SCHEDULED, 1)));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.not_active");
  }

  @Test
  @DisplayName("마감 시각이 지났으면 409(ended)다")
  void joinAfterDeadline() {
    ZoneEvent ended = event(ZoneEventStatus.ACTIVE, 1);
    ReflectionTestUtils.setField(ended, "startsAt", OffsetDateTime.now().minusMinutes(20));
    ReflectionTestUtils.setField(ended, "durationMinutes", 10);
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(ended));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.ended");
  }

  @Test
  @DisplayName("없는 이벤트는 404다")
  void joinWhenEventNotFound() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.not_found");
  }

  @Test
  @DisplayName("다른 이벤트 소속이거나 존재하지 않는 타겟은 404(target_not_found)다")
  void joinWhenTargetMissing() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.target_not_found");
  }

  @Test
  @DisplayName("비활성(취소·교체) 타겟은 404(target_not_found)다")
  void joinWhenTargetInactive() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    ZoneEventAuthTarget cancelled = target(event);
    cancelled.cancel();
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.target_not_found");
  }

  @Test
  @DisplayName("미인증이면 401이다")
  void joinWhenUnauthenticated() {
    assertThatThrownBy(() -> service.join(null, EVENT_ID, TARGET_ID, IN_LAT, IN_LNG))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private void stubActiveEventWithTarget() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    lenient()
        .when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target(event)));
  }

  @Test
  @DisplayName("열린 참여를 취소하면 CANCELLED가 된다")
  void cancelOpenParticipation() {
    ZoneEventParticipation joined = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(joined, "id", OPEN_ID);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(joined));

    service.cancel(user, EVENT_ID, OPEN_ID);

    assertThat(joined.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(joined.getCancelReason()).isEqualTo("USER");
  }

  @Test
  @DisplayName("SUCCESS 참여는 취소할 수 없다(409)")
  void cancelSuccessRejected() {
    ZoneEventParticipation success = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(success, "id", OPEN_ID);
    ReflectionTestUtils.setField(success, "status", ParticipationStatus.SUCCESS);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(success));

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("타인의 참여는 취소할 수 없다(403)")
  void cancelOthersForbidden() {
    ZoneEventParticipation joined = ZoneEventParticipation.join(event, OTHER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(joined, "id", OPEN_ID);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(joined));

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("없는 참여 취소는 404다")
  void cancelNotFound() {
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private ZoneEvent event(ZoneEventStatus status, int successLimit) {
    ZoneEventType type =
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build();
    ZoneEvent created =
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("광안대교 야경 담기")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
            .successLimitPerUser(successLimit)
            .build();
    ReflectionTestUtils.setField(created, "id", EVENT_ID);
    return created;
  }

  private ZoneEventAuthTarget target(ZoneEvent event) {
    ZoneEventAuthTarget created =
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("광안대교 야경")
            .latitude(35.153)
            .longitude(129.118)
            .radiusM(100)
            .build();
    ReflectionTestUtils.setField(created, "id", TARGET_ID);
    return created;
  }
}
```

- [ ] **Step 2: Update the controller test's join tests**

In `ZoneEventParticipationControllerTest.java`:

Add a `TARGET_ID` constant after `OPEN_ID`:

```java
  private static final UUID OPEN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID TARGET_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
```

Replace every occurrence of:

```java
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
```

with:

```java
    when(participationService.join(any(), eq(EVENT_ID), any(), anyDouble(), anyDouble()))
```

(There are 4 occurrences: `joinReturns201`, `outOfRangeReturns400WithDistance`, `alreadyOpenReturns409WithId`, `alreadyOpenWithoutIdReturns409EmptyData`, `unauthenticatedReturns401` — 5 total.)

Replace every join-request JSON body that currently omits `targetId`:

```java
                .content("{\"latitude\":35.1532,\"longitude\":129.1182}"))
```

with (adjust the `TARGET_ID` literal to match — use the constant via string concatenation in Java, not a hardcoded literal, since MockMvc `.content(String)` needs a compile-time-safe way to interpolate):

```java
                .content(
                    "{\"targetId\":\""
                        + TARGET_ID
                        + "\",\"latitude\":35.1532,\"longitude\":129.1182}"))
```

Apply this to `joinReturns201`, `alreadyOpenReturns409WithId`, `alreadyOpenWithoutIdReturns409EmptyData`, `unauthenticatedReturns401` (the ones using `{"latitude":35.1532,"longitude":129.1182}`).

For `outOfRangeReturns400WithDistance` (uses `{"latitude":35.16,"longitude":129.13}`):

```java
                .content(
                    "{\"targetId\":\""
                        + TARGET_ID
                        + "\",\"latitude\":35.16,\"longitude\":129.13}"))
```

`missingCoordinatesReturns400` (content `{"latitude":35.1532}"`) stays as-is — it's testing that a required field is missing, and now `targetId` is *also* missing, which still triggers the same 400; no change needed.

- [ ] **Step 3: Modify `ParticipationJoinReqDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 참여 시작 요청. 선택한 타겟과 현재 GPS 좌표. */
public record ParticipationJoinReqDto(
    @NotNull UUID targetId,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}
```

- [ ] **Step 4: Modify `ZoneEventParticipationController.java`**

Change the `join` method body:

```java
  @PostMapping
  public ResponseEntity<ApiResponse<ParticipationResDto>> join(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestBody @Valid ParticipationJoinReqDto request) {
    ParticipationResDto participation =
        participationService.join(
            user, eventId, request.targetId(), request.latitude(), request.longitude());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("이벤트 참여 시작", participation));
  }
```

- [ ] **Step 5: Modify `ZoneEventParticipationService.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.exception.OpenParticipationExistsException;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.support.GpsDistance;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 참여 시작.
 *
 * <p>선택한 타겟의 반경 이내일 때만 JOINED 참여를 만든다. 유저·이벤트당 열린 참여(반려되어 재제출 대기 중인 FAIL 포함)는 하나이고(부분 UK,
 * NFR-02), 성공 상한을 넘기면 새 참여를 막는다. 마감(endsAt) 이후에는 신규 참여를 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventParticipationService {

  private static final List<ParticipationStatus> OPEN_STATUSES =
      List.of(
          ParticipationStatus.JOINED,
          ParticipationStatus.SUBMITTED,
          ParticipationStatus.UNDER_REVIEW,
          ParticipationStatus.FAIL);

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;

  /** 반경 검증을 통과하면 JOINED 참여를 만들어 돌려준다. */
  @Transactional
  public ParticipationResDto join(
      AuthenticatedUser user, UUID eventId, UUID targetId, double latitude, double longitude) {
    UUID userId = requireUserId(user);
    ZoneEvent event =
        zoneEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    if (event.getStatus() != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.not_active");
    }
    if (!OffsetDateTime.now().isBefore(event.endsAt())) {
      throw new ConflictException("error.zone_event.ended");
    }

    ZoneEventAuthTarget target = requireActiveTarget(eventId, targetId);
    int distance =
        GpsDistance.meters(latitude, longitude, target.getLatitude(), target.getLongitude());
    if (distance > target.getRadiusM()) {
      throw new ZoneEventOutOfRangeException(distance);
    }

    participationRepository
        .findByEvent_IdAndUserIdAndStatusIn(eventId, userId, OPEN_STATUSES)
        .ifPresent(
            open -> {
              throw new OpenParticipationExistsException(open.getId());
            });

    long successes =
        participationRepository.countByEvent_IdAndUserIdAndStatus(
            eventId, userId, ParticipationStatus.SUCCESS);
    if (successes >= event.getSuccessLimitPerUser()) {
      throw new ConflictException("error.zone_event.participation.limit_reached");
    }

    ZoneEventParticipation saved;
    try {
      saved =
          participationRepository.save(
              ZoneEventParticipation.join(event, userId, latitude, longitude));
    } catch (DataIntegrityViolationException concurrent) {
      // 부분 UK 위반: 동시 요청이 먼저 열린 참여를 만들었다.
      UUID existing =
          participationRepository
              .findByEvent_IdAndUserIdAndStatusIn(eventId, userId, OPEN_STATUSES)
              .map(ZoneEventParticipation::getId)
              .orElse(null);
      throw new OpenParticipationExistsException(existing);
    }

    return ParticipationResDto.of(saved, distance);
  }

  /** 열린 참여를 취소한다. SUCCESS/FAIL/REVOKED/이미 취소된 참여는 취소할 수 없다. */
  @Transactional
  public void cancel(AuthenticatedUser user, UUID eventId, UUID participationId) {
    UUID userId = requireUserId(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .filter(p -> p.getEvent().getId().equals(eventId))
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (!participation.getUserId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.participation.forbidden");
    }
    if (!participation.getStatus().isOpen()) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    participation.cancel("USER");
  }

  private ZoneEventAuthTarget requireActiveTarget(UUID eventId, UUID targetId) {
    ZoneEventAuthTarget target =
        authTargetRepository
            .findByIdAndEvent_Id(targetId, eventId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.target_not_found"));
    if (target.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ResourceNotFoundException("error.zone_event.target_not_found");
    }
    return target;
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.ZoneEventParticipationServiceTest" --tests "com.butingbe.domain.zoneevent.controller.ZoneEventParticipationControllerTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all tests green (13 in the service test, existing count in the controller test unchanged since no tests were added/removed).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/request/ParticipationJoinReqDto.java src/main/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationController.java src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationService.java src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationServiceTest.java src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationControllerTest.java
git commit -m "feat(zoneevent): add targetId selection, deadline check, and FAIL-dedup to join"
```

---

### Task 5: Resubmission + `ZoneEventSubmission` wiring + upload hardening on submit

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/request/ParticipationSubmitReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/SubmitResultResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventSubmitService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventSubmitServiceTest.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationControllerTest.java`

**Interfaces:**
- Consumes: `ZoneEventSubmissionRepository.{countByParticipation_Id, existsByMediaFileKey, save}` (Task 1), `ZoneEventAuthTargetRepository.findByIdAndEvent_Id` (existing), `ZoneEventParticipation.{submit, linkSubmission, markSuccess, markUnderReview}` (existing, unchanged).
- Produces: `ZoneEventSubmitService.submit(...)` — same signature, new behavior. `SubmitResultResDto.of(ParticipationResDto, String submissionId, int attemptNo, List<GrantedRewardDto> rewards, int pointBalance)` and the 6-arg overload adding `List<Object> newlyEarnedTitles` — **replaces** the old 3-arg/4-arg factories (both call sites, here and in `AdminReviewService`, are updated together: this task updates the `ZoneEventSubmitService` call site; Task 6 updates the `AdminReviewService` call site using the shape this task defines).

This task changes `SubmitResultResDto`'s factory shape, which `AdminReviewService.approve()` also calls — but Task 6 runs after this one and updates that call site, so `AdminReviewService` will not compile between this task's commit and Task 6's commit if built in isolation. Since both tasks land in the same session before any intermediate push, this is acceptable — but if executing via subagent-driven-development with a build gate between tasks, do Task 5 and Task 6 back-to-back before running a full build.

- [ ] **Step 1: Write the failing tests — replace `ZoneEventSubmitServiceTest.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZoneEventSubmitServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("22222222-0000-0000-0000-000000000009");
  private static final UUID PARTICIPATION_ID =
      UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID TARGET_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final double IN_LAT = 35.1532;
  private static final double IN_LNG = 129.1182;
  private static final String FILE_KEY = "uploads/images/photo.jpg";

  @Mock private ZoneEventParticipationRepository participationRepository;
  @Mock private ZoneEventAuthTargetRepository authTargetRepository;
  @Mock private ZoneEventSubmissionRepository submissionRepository;
  @Mock private FileMetadataRepository fileMetadataRepository;
  @Mock private RewardService rewardService;
  @Mock private UserPointService userPointService;
  @Mock private com.butingbe.domain.zonetitle.service.ZoneTitleService zoneTitleService;

  private ZoneEventSubmitService service;
  private AuthenticatedUser user;
  private ZoneEvent event;

  @BeforeEach
  void setUp() {
    service =
        new ZoneEventSubmitService(
            participationRepository,
            authTargetRepository,
            submissionRepository,
            fileMetadataRepository,
            rewardService,
            userPointService,
            zoneTitleService);
    ReflectionTestUtils.setField(service, "reviewMode", "AUTO");
    ReflectionTestUtils.setField(service, "capturedAtThresholdMinutes", 10L);
    ReflectionTestUtils.setField(service, "uploadRecencyThresholdMinutes", 30L);
    user = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    event = event(ZoneEventStatus.ACTIVE);
    lenient()
        .when(submissionRepository.save(any()))
        .thenAnswer(
            invocation -> {
              ZoneEventSubmission s = invocation.getArgument(0);
              ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
              return s;
            });
  }

  @Test
  @DisplayName("AUTO 판정에서 제출이 통과하면 SUCCESS로 확정하고 기본 보상을 지급하며 첫 제출 이력을 남긴다")
  void autoApproveGrantsRewardAndCreatesFirstSubmission() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(rewardService.grantBaseReward(
            USER_ID, PARTICIPATION_ID, EVENT_ID, 50, "SPOT_GWANGAN_BRIDGE"))
        .thenReturn(
            new BaseRewardResult(
                List.of(
                    new GrantedRewardDto(
                        UUID.randomUUID().toString(),
                        "POINT",
                        "POINT_BASE",
                        "기본 포인트",
                        50,
                        "BASE",
                        OffsetDateTime.now())),
                350));

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY));

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(participation.getSuccess()).isTrue();
    assertThat(participation.getCompletedAt()).isNotNull();
    assertThat(participation.getMediaFileKey()).isEqualTo(FILE_KEY);
    assertThat(result.rewards()).hasSize(1);
    assertThat(result.pointBalance()).isEqualTo(350);
    assertThat(result.participation().status()).isEqualTo("SUCCESS");
    assertThat(result.attemptNo()).isEqualTo(1);
    assertThat(result.submissionId()).isNotBlank();
  }

  @Test
  @DisplayName("MANUAL 모드면 검수 대기로 보내고 보상을 지급하지 않는다")
  void manualModeGoesUnderReview() {
    ReflectionTestUtils.setField(service, "reviewMode", "MANUAL");
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/png");
    when(userPointService.getBalance(USER_ID)).thenReturn(100);

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY));

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);
    assertThat(result.rewards()).isEmpty();
    assertThat(result.pointBalance()).isEqualTo(100);
    assertThat(result.attemptNo()).isEqualTo(1);
    verify(rewardService, never()).grantBaseReward(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("촬영 시각이 임계치보다 오래되면 AUTO여도 검수 대기로 강등한다")
  void staleCapturedAtGoesUnderReview() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(userPointService.getBalance(USER_ID)).thenReturn(0);
    ParticipationSubmitReqDto stale =
        new ParticipationSubmitReqDto(
            TARGET_ID, FILE_KEY, "후기", IN_LAT, IN_LNG, OffsetDateTime.now().minusMinutes(30));

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, stale);

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);
    assertThat(result.rewards()).isEmpty();
    verify(rewardService, never()).grantBaseReward(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("반려 후 재제출하면 attemptNo가 늘어난 새 제출 이력이 쌓인다")
  void resubmitAfterRejectionCreatesNewAttempt() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.FAIL);
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(submissionRepository.countByParticipation_Id(PARTICIPATION_ID)).thenReturn(1L);
    when(rewardService.grantBaseReward(any(), any(), any(), any(), any()))
        .thenReturn(new BaseRewardResult(List.of(), 0));

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY));

    assertThat(result.attemptNo()).isEqualTo(2);
    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.SUCCESS);
  }

  @Test
  @DisplayName("UNDER_REVIEW 상태에서는 재제출할 수 없다(409)")
  void resubmitFromUnderReviewRejected() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.UNDER_REVIEW);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("SUCCESS 상태에서는 재제출할 수 없다(409)")
  void resubmitFromSuccessRejected() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.SUCCESS);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("마감 시각이 지나면 최초 제출도 409(ended)다")
  void submitAfterDeadlineRejected() {
    ZoneEvent ended = event(ZoneEventStatus.ACTIVE);
    ReflectionTestUtils.setField(ended, "startsAt", OffsetDateTime.now().minusMinutes(20));
    ReflectionTestUtils.setField(ended, "durationMinutes", 10);
    ZoneEventParticipation participation = joinedOf(ended);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.ended");
  }

  @Test
  @DisplayName("마감 시각이 지나면 FAIL 상태의 재제출도 409(ended)다")
  void resubmitAfterDeadlineRejected() {
    ZoneEvent ended = event(ZoneEventStatus.ACTIVE);
    ReflectionTestUtils.setField(ended, "startsAt", OffsetDateTime.now().minusMinutes(20));
    ReflectionTestUtils.setField(ended, "durationMinutes", 10);
    ZoneEventParticipation participation = joinedOf(ended);
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.FAIL);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.ended");
  }

  @Test
  @DisplayName("이미지가 아닌 미디어는 400(media.invalid)이다")
  void nonImageMediaRejected() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "application/pdf");

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.invalid");
  }

  @Test
  @DisplayName("등록되지 않은 fileKey는 400(media.invalid)이다")
  void unknownMediaRejected() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target()));
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.invalid");
  }

  @Test
  @DisplayName("제출 좌표가 반경 밖이면 400(out_of_range)이다")
  void submitOutOfRange() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target()));

    ParticipationSubmitReqDto far =
        new ParticipationSubmitReqDto(TARGET_ID, FILE_KEY, "후기", 35.16, 129.13, null);
    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, far))
        .isInstanceOf(ZoneEventOutOfRangeException.class);
  }

  @Test
  @DisplayName("JOINED도 FAIL도 아니면 409(invalid_state)다")
  void notJoinedOrFailedRejected() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.CANCELLED);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("타인의 참여 제출은 403이다")
  void otherUserForbidden() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "userId", OTHER_ID);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("이벤트가 ACTIVE가 아니면 409(not_active)다")
  void eventNotActive() {
    ZoneEventParticipation participation = joinedOf(event(ZoneEventStatus.CLOSED));
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.not_active");
  }

  @Test
  @DisplayName("없는 참여는 404다")
  void participationNotFound() {
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.participation.not_found");
  }

  @Test
  @DisplayName("다른 이벤트 소속이거나 비활성인 타겟으로 제출하면 404(target_not_found)다")
  void submitWithInvalidTargetRejected() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.target_not_found");
  }

  @Test
  @DisplayName("미인증이면 401이다")
  void unauthenticated() {
    assertThatThrownBy(() -> service.submit(null, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("업로더 정보가 없거나(익명·레거시) 타인이 업로드한 미디어는 403이다")
  void rejectsMediaWithoutOwnerOrUploadedByAnother() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target()));
    FileMetadata anonymous = mock(FileMetadata.class);
    when(anonymous.getContentType()).thenReturn("image/jpeg");
    when(anonymous.getUploaderId()).thenReturn(null);
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.of(anonymous));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("error.zone_event.media.forbidden");
  }

  @Test
  @DisplayName("이미 다른 제출에 쓰인 fileKey는 400(media.already_used)이다")
  void rejectsAlreadyUsedMedia() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(submissionRepository.existsByMediaFileKey(FILE_KEY)).thenReturn(true);

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.already_used");
  }

  @Test
  @DisplayName("업로드한 지 너무 오래된 fileKey는 400(media.stale)이다")
  void rejectsStaleMedia() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target()));
    FileMetadata stale = mock(FileMetadata.class);
    when(stale.getContentType()).thenReturn("image/jpeg");
    when(stale.getUploaderId()).thenReturn(USER_ID);
    when(stale.getCreatedAt()).thenReturn(LocalDateTime.now().minusHours(2));
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.of(stale));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.stale");
  }

  private void stubJoinedWithTargetAndMedia(
      ZoneEventParticipation participation, String contentType) {
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByIdAndEvent_Id(TARGET_ID, EVENT_ID))
        .thenReturn(Optional.of(target()));
    FileMetadata file = mock(FileMetadata.class);
    lenient().when(file.getContentType()).thenReturn(contentType);
    lenient().when(file.getUploaderId()).thenReturn(USER_ID);
    lenient().when(file.getCreatedAt()).thenReturn(LocalDateTime.now());
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.of(file));
  }

  private ParticipationSubmitReqDto request(String fileKey) {
    return new ParticipationSubmitReqDto(
        TARGET_ID, fileKey, "야경 미쳤다", IN_LAT, IN_LNG, OffsetDateTime.now());
  }

  private ZoneEventParticipation joined() {
    return joinedOf(event);
  }

  private ZoneEventParticipation joinedOf(ZoneEvent event) {
    ZoneEventParticipation p = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(p, "id", PARTICIPATION_ID);
    return p;
  }

  private ZoneEvent event(ZoneEventStatus status) {
    ZoneEventType type =
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build();
    ZoneEvent created =
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("광안대교 야경 담기")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
            .successLimitPerUser(1)
            .build();
    ReflectionTestUtils.setField(created, "id", EVENT_ID);
    return created;
  }

  private ZoneEventAuthTarget target() {
    ZoneEventAuthTarget created =
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("광안대교 야경")
            .latitude(35.153)
            .longitude(129.118)
            .radiusM(100)
            .build();
    ReflectionTestUtils.setField(created, "id", TARGET_ID);
    return created;
  }
}
```

- [ ] **Step 2: Update the controller test's submit tests**

In `ZoneEventParticipationControllerTest.java`, update the two submit-request JSON bodies to include `targetId` (the `TARGET_ID` constant was already added in Task 4, Step 2):

`submitReturns200` — replace:

```java
                .content(
                    "{\"mediaFileKey\":\"uploads/p.jpg\",\"latitude\":35.1532,\"longitude\":129.1182}"))
```

with:

```java
                .content(
                    "{\"targetId\":\""
                        + TARGET_ID
                        + "\",\"mediaFileKey\":\"uploads/p.jpg\","
                        + "\"latitude\":35.1532,\"longitude\":129.1182}"))
```

`submitMissingMediaKeyReturns400` (content `{"latitude":35.1532,"longitude":129.1182}"`) stays as-is — it's testing that `mediaFileKey` is missing, and now `targetId` is also missing, which still 400s the same way.

Also update the `SubmitResultResDto.of(...)` call inside `submitReturns200`'s mocked response — it currently calls the old 3-arg factory:

```java
                com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto.of(
                    new com.butingbe.domain.zoneevent.dto.response.ParticipationResDto(
                        ...),
                    List.of(
                        new com.butingbe.domain.reward.dto.response.GrantedRewardDto(
                            ...)),
                    350));
```

Replace with the new 5-arg factory (insert `submissionId`/`attemptNo` after the participation argument):

```java
                com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto.of(
                    new com.butingbe.domain.zoneevent.dto.response.ParticipationResDto(
                        OPEN_ID.toString(),
                        EVENT_ID.toString(),
                        "SUYEONG_NAMGU",
                        "PLACE_AUTH",
                        "SUCCESS",
                        true,
                        null,
                        null,
                        "야경 미쳤다",
                        0,
                        "PUBLIC",
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        List.of()),
                    UUID.randomUUID().toString(),
                    1,
                    List.of(
                        new com.butingbe.domain.reward.dto.response.GrantedRewardDto(
                            UUID.randomUUID().toString(),
                            "POINT",
                            "POINT_BASE",
                            "기본 포인트",
                            50,
                            "BASE",
                            OffsetDateTime.now())),
                    350));
```

Add an assertion for the new fields at the end of `submitReturns200`:

```java
        .andExpect(jsonPath("$.data.attemptNo").value(1));
```

- [ ] **Step 3: Modify `ParticipationSubmitReqDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 인증 제출 요청. 선택한 타겟, 미디어 fileKey, 촬영 시점 좌표. 반려 후 재제출 시 다른 타겟을 고를 수 있다. */
public record ParticipationSubmitReqDto(
    @NotNull UUID targetId,
    @NotBlank String mediaFileKey,
    @Size(max = 300) String content,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    OffsetDateTime capturedAt) {}
```

- [ ] **Step 4: Modify `SubmitResultResDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import java.util.List;

/**
 * 제출 결과.
 *
 * <p>AUTO 판정에서 성공하면 참여는 SUCCESS이고 지급된 보상과 잔액을 함께 돌려준다. 검수 대기(MANUAL/HYBRID)면 참여는 UNDER_REVIEW이고 보상은
 * 비어 있다. {@code submissionId}/{@code attemptNo}는 이번 호출이 만든 제출 이력을 가리킨다. {@code newlyEarnedTitles}·{@code
 * titleProgress}는 Phase 2에서 채워진다.
 */
public record SubmitResultResDto(
    ParticipationResDto participation,
    String submissionId,
    int attemptNo,
    List<GrantedRewardDto> rewards,
    int pointBalance,
    List<Object> newlyEarnedTitles,
    Object titleProgress) {

  public static SubmitResultResDto of(
      ParticipationResDto participation,
      String submissionId,
      int attemptNo,
      List<GrantedRewardDto> rewards,
      int pointBalance) {
    return new SubmitResultResDto(
        participation, submissionId, attemptNo, rewards, pointBalance, List.of(), null);
  }

  public static SubmitResultResDto of(
      ParticipationResDto participation,
      String submissionId,
      int attemptNo,
      List<GrantedRewardDto> rewards,
      int pointBalance,
      List<Object> newlyEarnedTitles) {
    return new SubmitResultResDto(
        participation, submissionId, attemptNo, rewards, pointBalance, newlyEarnedTitles, null);
  }
}
```

- [ ] **Step 5: Modify `ZoneEventSubmitService.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zoneevent.support.GpsDistance;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 제출과 판정.
 *
 * <p>제출을 모두 검증한 뒤 판정 모드에 따라 처리한다. AUTO면 같은 트랜잭션에서 SUCCESS로 확정하고 기본 보상을 지급한다(FR-RWD-01).
 * MANUAL/HYBRID면 검수 대기로 보낸다. 반경은 참여 시작에 이어 제출 시점에도 다시 검증한다(BR-05).
 *
 * <p>참여가 JOINED거나(최초 제출) FAIL이면(반려 후 재제출) 제출을 받는다. 매 호출마다 새 {@link ZoneEventSubmission} row를 만들고
 * 이전 이력은 바꾸지 않는다. 이벤트 마감({@code endsAt}) 이후에는 최초 제출도 재제출도 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventSubmitService {

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final FileMetadataRepository fileMetadataRepository;
  private final RewardService rewardService;
  private final UserPointService userPointService;
  private final ZoneTitleService zoneTitleService;

  @Value("${zone-event.review.mode:AUTO}")
  private String reviewMode;

  @Value("${zone-event.review.captured-at-threshold-minutes:10}")
  private long capturedAtThresholdMinutes;

  @Value("${zone-event.review.upload-recency-threshold-minutes:30}")
  private long uploadRecencyThresholdMinutes;

  @Transactional
  public SubmitResultResDto submit(
      AuthenticatedUser user,
      UUID eventId,
      UUID participationId,
      ParticipationSubmitReqDto request) {
    UUID userId = requireUserId(user);

    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .filter(p -> p.getEvent().getId().equals(eventId))
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));

    if (!participation.getUserId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.participation.forbidden");
    }
    if (participation.getStatus() != ParticipationStatus.JOINED
        && participation.getStatus() != ParticipationStatus.FAIL) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }

    ZoneEvent event = participation.getEvent();
    if (event.getStatus() != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.not_active");
    }
    if (!OffsetDateTime.now().isBefore(event.endsAt())) {
      throw new ConflictException("error.zone_event.ended");
    }

    ZoneEventAuthTarget target = requireActiveTarget(eventId, request.targetId());
    int distance =
        GpsDistance.meters(
            request.latitude(), request.longitude(), target.getLatitude(), target.getLongitude());
    if (distance > target.getRadiusM()) {
      throw new ZoneEventOutOfRangeException(distance);
    }

    validateMedia(request.mediaFileKey(), userId);

    int attemptNo = (int) submissionRepository.countByParticipation_Id(participationId) + 1;
    ZoneEventSubmission submission =
        submissionRepository.save(
            ZoneEventSubmission.builder()
                .participation(participation)
                .attemptNo(attemptNo)
                .target(target)
                .placeName(target.getPlaceName())
                .targetLatitude(target.getLatitude())
                .targetLongitude(target.getLongitude())
                .radiusM(target.getRadiusM())
                .guideTextSnapshot(target.getGuideText())
                .mediaFileKey(request.mediaFileKey())
                .gpsLat(request.latitude())
                .gpsLng(request.longitude())
                .capturedAt(request.capturedAt())
                .build());

    participation.submit(
        request.mediaFileKey(),
        request.content(),
        request.latitude(),
        request.longitude(),
        request.capturedAt());
    participation.linkSubmission(submission.getId());

    if (isAutoApprove() && !capturedTooOld(request.capturedAt())) {
      submission.approve(null);
      participation.markSuccess();
      RewardSnapshot base = event.getBaseReward();
      BaseRewardResult reward =
          rewardService.grantBaseReward(
              userId,
              participationId,
              eventId,
              base == null ? null : base.points(),
              base == null ? null : base.badgeCode());
      List<Object> newlyEarnedTitles =
          new ArrayList<>(zoneTitleService.awardTitles(userId, event.getZoneId()));
      return SubmitResultResDto.of(
          ParticipationResDto.of(participation, null),
          submission.getId().toString(),
          attemptNo,
          reward.rewards(),
          reward.pointBalance(),
          newlyEarnedTitles);
    }

    participation.markUnderReview();
    return SubmitResultResDto.of(
        ParticipationResDto.of(participation, null),
        submission.getId().toString(),
        attemptNo,
        List.of(),
        userPointService.getBalance(userId));
  }

  private ZoneEventAuthTarget requireActiveTarget(UUID eventId, UUID targetId) {
    ZoneEventAuthTarget target =
        authTargetRepository
            .findByIdAndEvent_Id(targetId, eventId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.target_not_found"));
    if (target.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ResourceNotFoundException("error.zone_event.target_not_found");
    }
    return target;
  }

  /** fileKey가 등록된 본인 소유의 이미지이고, 다른 제출에 쓰이지 않았으며, 업로드한 지 오래되지 않았는지 확인한다. */
  private void validateMedia(String mediaFileKey, UUID userId) {
    FileMetadata file =
        fileMetadataRepository
            .findByObjectKey(mediaFileKey)
            .orElseThrow(() -> new IllegalArgumentException("error.zone_event.media.invalid"));
    if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
      throw new IllegalArgumentException("error.zone_event.media.invalid");
    }
    if (file.getUploaderId() == null || !file.getUploaderId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.media.forbidden");
    }
    if (submissionRepository.existsByMediaFileKey(mediaFileKey)) {
      throw new IllegalArgumentException("error.zone_event.media.already_used");
    }
    if (Duration.between(file.getCreatedAt(), LocalDateTime.now()).toMinutes()
        > uploadRecencyThresholdMinutes) {
      throw new IllegalArgumentException("error.zone_event.media.stale");
    }
  }

  /** 촬영 시각과 서버 수신 시각 차이가 임계치를 넘으면 자동 성공 대신 검수로 보낸다(FR-PTC-09). */
  private boolean capturedTooOld(OffsetDateTime capturedAt) {
    return capturedAt != null
        && Duration.between(capturedAt, OffsetDateTime.now()).toMinutes()
            > capturedAtThresholdMinutes;
  }

  private boolean isAutoApprove() {
    return reviewMode == null || "AUTO".equalsIgnoreCase(reviewMode);
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.ZoneEventSubmitServiceTest" --tests "com.butingbe.domain.zoneevent.controller.ZoneEventParticipationControllerTest" --no-daemon -g "C:\\gradle-home"`
Expected: FAIL at this point — `AdminReviewService` (untouched until Task 6) still calls the old 3-arg `SubmitResultResDto.of`, so the module won't compile yet. This is expected; proceed directly to Task 6 before running the build again.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/request/ParticipationSubmitReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/SubmitResultResDto.java src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventSubmitService.java src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventSubmitServiceTest.java src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventParticipationControllerTest.java
git commit -m "feat(zoneevent): wire ZoneEventSubmission creation, resubmission, and upload hardening into submit"
```

(Committing here is fine even though the module doesn't compile yet — Task 6 is the very next task and fixes it. Do not push or open a PR between these two tasks.)

---

### Task 6: `AdminReviewService` — wire submission approve/reject

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java`

**Interfaces:**
- Consumes: `ZoneEventSubmissionRepository.findFirstByParticipation_IdOrderByAttemptNoDesc` (existing), `ZoneEventSubmission.{approve, reject}` (existing), `SubmitResultResDto.of(ParticipationResDto, String, int, List<GrantedRewardDto>, int, List<Object>)` (Task 5's new shape).
- Produces: no external API change. `approve`/`reject` now also transition the participation's current `ZoneEventSubmission`.

This task fixes the compilation break left at the end of Task 5.

- [ ] **Step 1: Write the failing tests — update `AdminReviewServiceTest.java`**

Add imports (after the existing `com.butingbe.domain.zoneevent.entity.ZoneEventType` import):

```java
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
```

Add autowired fields (after `@Autowired private ZoneEventReportRepository reportRepository;`):

```java
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventSubmissionRepository submissionRepository;
```

Add two private helpers (near the bottom, alongside `participation(...)`):

```java
  private ZoneEventAuthTarget savedTarget() {
    return authTargetRepository.save(
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("장소")
            .latitude(35.1)
            .longitude(129.1)
            .radiusM(100)
            .build());
  }

  private ZoneEventSubmission savedSubmission(ZoneEventParticipation participation) {
    ZoneEventAuthTarget target = savedTarget();
    return submissionRepository.save(
        ZoneEventSubmission.builder()
            .participation(participation)
            .attemptNo(1)
            .target(target)
            .placeName(target.getPlaceName())
            .targetLatitude(target.getLatitude())
            .targetLongitude(target.getLongitude())
            .radiusM(target.getRadiusM())
            .mediaFileKey("uploads/images/photo.jpg")
            .gpsLat(35.1)
            .gpsLng(129.1)
            .capturedAt(OffsetDateTime.now())
            .build());
  }
```

Replace `approveGrantsReward`:

```java
  @Test
  @DisplayName("승인하면 SUCCESS로 확정되고 보상이 지급되며 제출도 함께 승인된다")
  void approveGrantsReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    ZoneEventSubmission submission = savedSubmission(p);

    SubmitResultResDto result = reviewService.approve(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(participationRepository.findById(p.getId()).orElseThrow().getReviewedBy())
        .isEqualTo(operator.id());
    assertThat(result.rewards()).isNotEmpty();
    assertThat(result.submissionId()).isEqualTo(submission.getId().toString());
    assertThat(result.attemptNo()).isEqualTo(1);
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.SUCCESS);
  }
```

Replace `rejectMarksFail`:

```java
  @Test
  @DisplayName("반려하면 FAIL이 되고 사유가 남으며 제출도 함께 반려된다")
  void rejectMarksFail() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    ZoneEventSubmission submission = savedSubmission(p);

    reviewService.reject(operator, p.getId(), "NOT_ON_SITE");

    ZoneEventParticipation after = participationRepository.findById(p.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(ParticipationStatus.FAIL);
    assertThat(after.getFailReason()).isEqualTo("NOT_ON_SITE");
    ZoneEventSubmission submissionAfter =
        submissionRepository.findById(submission.getId()).orElseThrow();
    assertThat(submissionAfter.getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.REJECTED);
    assertThat(submissionAfter.getRejectionReason()).isEqualTo("NOT_ON_SITE");
  }
```

Update `revokeReversesReward` to create a submission before the setup `approve` call:

```java
  @Test
  @DisplayName("회수하면 REVOKED가 되고 지급 포인트가 되돌아간다")
  void revokeReversesReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    savedSubmission(p);
    reviewService.approve(operator, p.getId());
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);

    reviewService.revoke(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.REVOKED);
    assertThat(userPointService.getBalance(p.getUserId())).isZero();
  }
```

- [ ] **Step 2: Run the test to verify it fails to compile / fails**

Run: `./gradlew compileTestJava --no-daemon -g "C:\\gradle-home"`
Expected: fails — `AdminReviewService.approve/reject` don't yet look up a submission, so `IllegalStateException` (no submission) would be thrown once `AdminReviewService` itself is fixed to require one. At this exact point the whole module still fails to compile because `AdminReviewService`'s call to `SubmitResultResDto.of` uses the old 3-arg shape removed in Task 5 — proceed to Step 3 immediately.

- [ ] **Step 3: Modify `AdminReviewService.java`**

Add the import `com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;` and `com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;`, add the field, and update `approve`/`reject`:

```java
  private final ZoneEventSubmissionRepository submissionRepository;
```

(add this field declaration alongside the existing `private final ZoneEventParticipationRepository participationRepository;` etc. — Lombok's `@RequiredArgsConstructor` picks it up automatically, no constructor code to write.)

Replace `approve`:

```java
  /** UNDER_REVIEW → SUCCESS + 보상·칭호 지급(제출 성공 경로와 동일). 현재 제출도 함께 승인한다. */
  @Transactional
  public SubmitResultResDto approve(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.UNDER_REVIEW);
    ZoneEventSubmission submission = requireLatestSubmission(participationId);
    participation.stampReview(user.id());
    participation.markSuccess();
    submission.approve(user.id());

    ZoneEvent event = participation.getEvent();
    RewardSnapshot base = event.getBaseReward();
    BaseRewardResult reward =
        rewardService.grantBaseReward(
            participation.getUserId(),
            participationId,
            event.getId(),
            base == null ? null : base.points(),
            base == null ? null : base.badgeCode());
    List<Object> titles =
        new ArrayList<>(zoneTitleService.awardTitles(participation.getUserId(), event.getZoneId()));
    return SubmitResultResDto.of(
        ParticipationResDto.of(participation, null),
        submission.getId().toString(),
        submission.getAttemptNo(),
        reward.rewards(),
        reward.pointBalance(),
        titles);
  }
```

Replace `reject`:

```java
  /** UNDER_REVIEW → FAIL. 현재 제출도 함께 반려한다(같은 참여는 재제출로 재시도 가능). */
  @Transactional
  public void reject(AuthenticatedUser user, UUID participationId, String failReason) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.UNDER_REVIEW);
    ZoneEventSubmission submission = requireLatestSubmission(participationId);
    participation.stampReview(user.id());
    participation.markFail(failReason);
    submission.reject(user.id(), failReason);
  }
```

Add the helper (near `requireStatus`):

```java
  private ZoneEventSubmission requireLatestSubmission(UUID participationId) {
    return submissionRepository
        .findFirstByParticipation_IdOrderByAttemptNoDesc(participationId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "UNDER_REVIEW participation has no submission: " + participationId));
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.AdminReviewServiceTest" --tests "com.butingbe.domain.zoneevent.service.ZoneEventSubmitServiceTest" --tests "com.butingbe.domain.zoneevent.controller.ZoneEventParticipationControllerTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all green. This also confirms Task 5's tests (which couldn't compile in isolation) now pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java
git commit -m "feat(zoneevent): approve/reject also transition the current submission"
```

---

### Task 7: Event detail — `targets`, `slotCode`, `deadline`, `myParticipation`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/ZoneEventDetailResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventQueryService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventQueryServiceTest.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventControllerTest.java`

**Interfaces:**
- Consumes: `ZoneEventAuthTargetRepository.findByEvent_IdAndStatus` (Task 1), `MyParticipationResDto.of` (Task 3), `ZoneEventParticipationRepository.findByEvent_IdAndUserIdOrderByJoinedAtDesc` (existing).
- Produces: `ZoneEventDetailResDto.of(ZoneEvent, ZoneEventAuthTarget, List<AuthTargetDetailResDto>, String, long, long, Integer, MyParticipationResDto)` — arity changes from 6 to 8 params (`targets` inserted after `target`, `myParticipation` appended).

- [ ] **Step 1: Write the failing tests — update `ZoneEventQueryServiceTest.java`**

Replace the stub in `detailFillsExampleUrlAndRemainingAttempts`:

```java
    when(authTargetRepository.findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
            EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(Optional.of(target));
```

with:

```java
    when(authTargetRepository.findByEvent_IdAndStatus(EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(List.of(target));
```

(this appears once in `detailFillsExampleUrlAndRemainingAttempts` — the `getActiveEvents`-related tests keep using `findFirstByEvent_IdAndStatusOrderByCreatedAtAsc`, unchanged, since `toSummary()` is untouched.)

Add assertions to `detailFillsExampleUrlAndRemainingAttempts`, right after the existing `assertThat(detail.authTarget().guideText())...` line:

```java
    assertThat(detail.targets()).hasSize(1);
    assertThat(detail.targets().get(0).exampleImageUrl())
        .isEqualTo("https://signed.example/uploads/example.jpg");
    assertThat(detail.slotCode()).isNull();
    assertThat(detail.deadline()).isEqualTo(detail.endsAt());
    assertThat(detail.myParticipation()).isNull();
```

Replace the stub in `detailWithoutLoginAndWithoutExample`:

```java
    when(authTargetRepository.findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
            EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(Optional.of(noImage));
```

with:

```java
    when(authTargetRepository.findByEvent_IdAndStatus(EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(List.of(noImage));
```

Add two new tests after `detailWithoutLoginAndWithoutExample`:

```java
  @Test
  @DisplayName("이벤트에 ACTIVE 타겟이 여러 개면 모두 targets에 담긴다")
  void detailListsAllActiveTargets() {
    ZoneEventAuthTarget second = target(event);
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByEvent_IdAndStatus(EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(List.of(target, second));
    when(participationRepository.countByEvent_IdAndStatus(any(), any())).thenReturn(0L);
    when(fileStorageService.getPresignedUrl("uploads/example.jpg"))
        .thenReturn("https://signed.example/uploads/example.jpg");

    ZoneEventDetailResDto detail = service.getEventDetail(EVENT_ID, null);

    assertThat(detail.targets()).hasSize(2);
  }

  @Test
  @DisplayName("로그인 유저의 최근 참여가 FAIL이고 마감 전이면 canResubmit이 true다")
  void detailFillsMyParticipationWhenFailed() {
    ZoneEventParticipation failed =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(USER_ID)
            .status(ParticipationStatus.FAIL)
            .gpsLat(35.15)
            .gpsLng(129.11)
            .joinedAt(OffsetDateTime.now())
            .build();
    ReflectionTestUtils.setField(failed, "id", PARTICIPATION_ID);
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByEvent_IdAndStatus(EVENT_ID, ZoneEventTargetStatus.ACTIVE))
        .thenReturn(List.of(target));
    when(participationRepository.countByEvent_IdAndStatus(any(), any())).thenReturn(0L);
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(any(), any(), any()))
        .thenReturn(0L);
    when(participationRepository.findByEvent_IdAndUserIdOrderByJoinedAtDesc(EVENT_ID, USER_ID))
        .thenReturn(List.of(failed));
    when(fileStorageService.getPresignedUrl("uploads/example.jpg"))
        .thenReturn("https://signed.example/uploads/example.jpg");

    ZoneEventDetailResDto detail = service.getEventDetail(EVENT_ID, USER_ID);

    assertThat(detail.myParticipation()).isNotNull();
    assertThat(detail.myParticipation().status()).isEqualTo("FAIL");
    assertThat(detail.myParticipation().canResubmit()).isTrue();
  }
```

Add the import `com.butingbe.domain.zoneevent.entity.ParticipationStatus;` if not already present (it already is, per the existing file), and confirm `ZoneEventAuthTarget`, `ZoneEventParticipation` imports exist (they do).

- [ ] **Step 2: Update the controller test**

In `ZoneEventControllerTest.java`, the `getEventDetail` test constructs `ZoneEventDetailResDto` positionally. Replace the constructor call:

```java
            new com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto(
                EVENT_ID.toString(),
                ZoneRef.from("SUYEONG_NAMGU"),
                "PLACE_AUTH",
                "장소 인증",
                true,
                "광안대교 야경 담기",
                "야경 촬영",
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(23),
                1440,
                52340,
                "ACTIVE",
                null,
                new RewardSummaryResDto(50, "SPOT_GWANGAN_BRIDGE", null, null),
                new RewardSummaryResDto(null, null, 5, "COUPON_CAFE_3000"),
                new com.butingbe.domain.zoneevent.dto.response.AuthTargetDetailResDto(
                    UUID.randomUUID().toString(),
                    "PLACE",
                    "gwangan-bridge",
                    "광안대교 야경",
                    "가로로 촬영",
                    "https://signed.example/example.jpg",
                    35.153,
                    129.118,
                    100),
                27,
                1,
                1,
                null));
```

with:

```java
            new com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto(
                EVENT_ID.toString(),
                ZoneRef.from("SUYEONG_NAMGU"),
                "PLACE_AUTH",
                "장소 인증",
                true,
                "광안대교 야경 담기",
                "야경 촬영",
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(23),
                1440,
                52340,
                "ACTIVE",
                null,
                new RewardSummaryResDto(50, "SPOT_GWANGAN_BRIDGE", null, null),
                new RewardSummaryResDto(null, null, 5, "COUPON_CAFE_3000"),
                new com.butingbe.domain.zoneevent.dto.response.AuthTargetDetailResDto(
                    UUID.randomUUID().toString(),
                    "PLACE",
                    "gwangan-bridge",
                    "광안대교 야경",
                    "가로로 촬영",
                    "https://signed.example/example.jpg",
                    35.153,
                    129.118,
                    100),
                List.of(),
                "1-A",
                OffsetDateTime.now().plusHours(23),
                27,
                1,
                1,
                null,
                null));
```

Add an assertion to `getEventDetail`, right after the `excellenceReward.topN` assertion:

```java
        .andExpect(jsonPath("$.data.slotCode").value("1-A"));
```

- [ ] **Step 3: Modify `ZoneEventDetailResDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 상세. 목록 항목에 촬영 가이드·예시 이미지·우수 보상·성공 참여자 수·남은 참여 가능 횟수를 더한다.
 *
 * <p>{@code targets}는 이 이벤트의 ACTIVE 타겟 전체(참여·제출 시 targetId로 선택). {@code deadline}은 {@code endsAt}과
 * 같은 값을 재제출 UI 용도로 명시적 이름으로 내려준다. {@code myParticipation}은 비로그인 시 null이다. {@code round}는 Phase 2에서
 * 채워진다.
 */
public record ZoneEventDetailResDto(
    String eventId,
    ZoneRef zone,
    String typeCode,
    String typeName,
    boolean requiresUpload,
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer durationMinutes,
    long remainingSeconds,
    String status,
    UUID roundId,
    RewardSummaryResDto baseReward,
    RewardSummaryResDto excellenceReward,
    AuthTargetDetailResDto authTarget,
    List<AuthTargetDetailResDto> targets,
    String slotCode,
    OffsetDateTime deadline,
    long successCount,
    Integer successLimitPerUser,
    Integer myRemainingAttempts,
    MyParticipationResDto myParticipation,
    Object round) {

  public static ZoneEventDetailResDto of(
      ZoneEvent event,
      ZoneEventAuthTarget target,
      List<AuthTargetDetailResDto> targets,
      String exampleImageUrl,
      long remainingSeconds,
      long successCount,
      Integer myRemainingAttempts,
      MyParticipationResDto myParticipation) {
    return new ZoneEventDetailResDto(
        event.getId().toString(),
        ZoneRef.from(event.getZoneId()),
        event.getType().getTypeCode(),
        event.getType().getName(),
        Boolean.TRUE.equals(event.getType().getRequiresUpload()),
        event.getTitle(),
        event.getDescription(),
        event.getStartsAt(),
        event.endsAt(),
        event.getDurationMinutes(),
        remainingSeconds,
        event.getStatus().name(),
        event.getRoundId(),
        RewardSummaryResDto.from(event.getBaseReward()),
        RewardSummaryResDto.from(event.getExcellenceReward()),
        AuthTargetDetailResDto.from(target, exampleImageUrl),
        targets,
        event.getSlotCode(),
        event.endsAt(),
        successCount,
        event.getSuccessLimitPerUser(),
        myRemainingAttempts,
        myParticipation,
        null);
  }
}
```

- [ ] **Step 4: Modify `ZoneEventQueryService.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.zoneevent.dto.response.AuthTargetDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.MyParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventSummaryResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 이벤트 조회. 비로그인도 허용하며, 로그인 시에만 개인화 필드(내 참여 상태·남은 참여 가능 횟수)를 채운다.
 *
 * <p>열린 참여를 판별하는 상태 집합은 {@link ParticipationStatus#isOpen()}과 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneEventQueryService {

  private static final List<ParticipationStatus> OPEN_STATUSES =
      List.of(
          ParticipationStatus.JOINED,
          ParticipationStatus.SUBMITTED,
          ParticipationStatus.UNDER_REVIEW);

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final FileStorageService fileStorageService;

  /** 구역의 활성 이벤트 목록. userId가 있으면 내 참여 상태를 함께 채운다. */
  public List<ZoneEventSummaryResDto> getActiveEvents(String zone, UUID userId) {
    String zoneId = parseZone(zone);
    OffsetDateTime now = OffsetDateTime.now();
    return zoneEventRepository
        .findByZoneIdAndStatusOrderByStartsAtAsc(zoneId, ZoneEventStatus.ACTIVE)
        .stream()
        .map(event -> toSummary(event, now, userId))
        .toList();
  }

  /** 이벤트 상세. 없는 이벤트는 404. */
  public ZoneEventDetailResDto getEventDetail(UUID eventId, UUID userId) {
    ZoneEvent event =
        zoneEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    List<ZoneEventAuthTarget> activeTargets =
        authTargetRepository.findByEvent_IdAndStatus(eventId, ZoneEventTargetStatus.ACTIVE);
    ZoneEventAuthTarget target = activeTargets.isEmpty() ? null : activeTargets.get(0);
    OffsetDateTime now = OffsetDateTime.now();

    long successCount =
        participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.SUCCESS);
    String exampleImageUrl = presignedOrNull(target == null ? null : target.getExampleFileKey());
    List<AuthTargetDetailResDto> targets =
        activeTargets.stream()
            .map(t -> AuthTargetDetailResDto.from(t, presignedOrNull(t.getExampleFileKey())))
            .toList();
    Integer myRemainingAttempts = userId == null ? null : remainingAttempts(event, userId);
    MyParticipationResDto myParticipation =
        userId == null ? null : myParticipation(event, userId, now);

    return ZoneEventDetailResDto.of(
        event,
        target,
        targets,
        exampleImageUrl,
        remainingSeconds(event, now),
        successCount,
        myRemainingAttempts,
        myParticipation);
  }

  private MyParticipationResDto myParticipation(ZoneEvent event, UUID userId, OffsetDateTime now) {
    Optional<ZoneEventParticipation> latest =
        participationRepository
            .findByEvent_IdAndUserIdOrderByJoinedAtDesc(event.getId(), userId)
            .stream()
            .findFirst();
    return latest
        .map(
            p ->
                MyParticipationResDto.of(
                    p, p.getStatus() == ParticipationStatus.FAIL && now.isBefore(event.endsAt())))
        .orElse(null);
  }

  private String presignedOrNull(String fileKey) {
    return fileKey == null ? null : fileStorageService.getPresignedUrl(fileKey);
  }

  private ZoneEventSummaryResDto toSummary(ZoneEvent event, OffsetDateTime now, UUID userId) {
    ZoneEventAuthTarget target =
        authTargetRepository
            .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
                event.getId(), ZoneEventTargetStatus.ACTIVE)
            .orElse(null);
    long successCount =
        participationRepository.countByEvent_IdAndStatus(
            event.getId(), ParticipationStatus.SUCCESS);

    String myStatus = null;
    UUID myOpenParticipationId = null;
    if (userId != null) {
      Optional<ZoneEventParticipation> open =
          participationRepository.findByEvent_IdAndUserIdAndStatusIn(
              event.getId(), userId, OPEN_STATUSES);
      if (open.isPresent()) {
        myStatus = open.get().getStatus().name();
        myOpenParticipationId = open.get().getId();
      }
    }

    return ZoneEventSummaryResDto.of(
        event, target, remainingSeconds(event, now), successCount, myStatus, myOpenParticipationId);
  }

  private Integer remainingAttempts(ZoneEvent event, UUID userId) {
    long successes =
        participationRepository.countByEvent_IdAndUserIdAndStatus(
            event.getId(), userId, ParticipationStatus.SUCCESS);
    return Math.max(0, event.getSuccessLimitPerUser() - (int) successes);
  }

  private long remainingSeconds(ZoneEvent event, OffsetDateTime now) {
    long seconds = java.time.Duration.between(now, event.endsAt()).getSeconds();
    return Math.max(0, seconds);
  }

  /** ChatZone enum으로 검증한다. 잘못된 값이면 400(error.zone_event.invalid_zone). */
  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.ZoneEventQueryServiceTest" --tests "com.butingbe.domain.zoneevent.controller.ZoneEventControllerTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/response/ZoneEventDetailResDto.java src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventQueryService.java src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventQueryServiceTest.java src/test/java/com/butingbe/domain/zoneevent/controller/ZoneEventControllerTest.java
git commit -m "feat(zoneevent): expose targets, slotCode, deadline, and myParticipation on event detail"
```

---

### Task 8: Participation history — submissions, rejection reason, canResubmit

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/ParticipationHistoryItemResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationQueryService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationQueryServiceTest.java`

**Interfaces:**
- Consumes: `ZoneEventSubmissionRepository.findByParticipation_IdIn` (Task 1), `SubmissionHistoryItemResDto.of` (Task 3).
- Produces: `ParticipationHistoryItemResDto.of(...)` gains `rejectionReason`, `canResubmit`, `submissions` (arity 7 → 10).

- [ ] **Step 1: Write the failing tests — add to `ZoneEventParticipationQueryServiceTest.java`**

Add imports:

```java
import com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
```

Add autowired fields (after `@Autowired private RewardGrantRepository rewardGrantRepository;`):

```java
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventSubmissionRepository submissionRepository;
```

Add a new test after `historyIncludesRewards`:

```java
  @Test
  @DisplayName("반려된 참여는 제출 이력과 반려 사유·재제출 가능 여부를 함께 담는다")
  void historyIncludesSubmissionsAndRejection() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    ZoneEventParticipation participation =
        savedParticipation(event, ParticipationStatus.FAIL, OffsetDateTime.now());
    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("광안대교 야경")
                .latitude(35.153)
                .longitude(129.118)
                .radiusM(100)
                .build());
    ZoneEventSubmission submission =
        submissionRepository.save(
            ZoneEventSubmission.builder()
                .participation(participation)
                .attemptNo(1)
                .target(target)
                .placeName(target.getPlaceName())
                .targetLatitude(target.getLatitude())
                .targetLongitude(target.getLongitude())
                .radiusM(target.getRadiusM())
                .mediaFileKey("uploads/images/photo.jpg")
                .gpsLat(35.153)
                .gpsLng(129.118)
                .capturedAt(OffsetDateTime.now())
                .build());
    submission.reject(UUID.randomUUID(), "NOT_ON_SITE");

    ParticipationHistoryPageResDto page =
        queryService.history(user, null, null, List.of(), null, null, null, 20);

    var item = page.items().get(0);
    assertThat(item.rejectionReason()).isEqualTo("NOT_ON_SITE");
    assertThat(item.canResubmit()).isTrue();
    assertThat(item.submissions()).hasSize(1);
    assertThat(item.submissions().get(0).attemptNo()).isEqualTo(1);
    assertThat(item.submissions().get(0).reviewStatus())
        .isEqualTo(SubmissionReviewStatus.REJECTED.name());
  }

  @Test
  @DisplayName("성공한 참여는 재제출 불가이고 반려 사유가 없다")
  void historySuccessHasNoRejectionAndCannotResubmit() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    savedParticipation(event, ParticipationStatus.SUCCESS, OffsetDateTime.now());

    ParticipationHistoryPageResDto page =
        queryService.history(user, null, null, List.of(), null, null, null, 20);

    var item = page.items().get(0);
    assertThat(item.rejectionReason()).isNull();
    assertThat(item.canResubmit()).isFalse();
    assertThat(item.submissions()).isEmpty();
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew compileTestJava --no-daemon -g "C:\\gradle-home"`
Expected: fails — `ParticipationHistoryItemResDto` doesn't have `rejectionReason()`/`canResubmit()`/`submissions()` yet.

- [ ] **Step 3: Modify `ParticipationHistoryItemResDto.java`**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;
import java.util.List;

/** 내 참여 이력 한 항목. 미디어는 요청 시점 presigned URL과 만료 시간(초). 반려된 참여는 사유·재제출 가능 여부·제출 이력을 함께 담는다. */
public record ParticipationHistoryItemResDto(
    String participationId,
    String status,
    EventBriefResDto event,
    String mediaUrl,
    Integer mediaUrlExpiresIn,
    String content,
    long likeCount,
    int commentCount,
    String visibility,
    List<GrantedRewardDto> rewards,
    OffsetDateTime joinedAt,
    OffsetDateTime completedAt,
    String rejectionReason,
    boolean canResubmit,
    List<SubmissionHistoryItemResDto> submissions) {

  public static ParticipationHistoryItemResDto of(
      ZoneEventParticipation participation,
      String mediaUrl,
      Integer mediaUrlExpiresIn,
      List<GrantedRewardDto> rewards,
      String rejectionReason,
      boolean canResubmit,
      List<SubmissionHistoryItemResDto> submissions) {
    return new ParticipationHistoryItemResDto(
        participation.getId().toString(),
        participation.getStatus().name(),
        EventBriefResDto.from(participation.getEvent()),
        mediaUrl,
        mediaUrl == null ? null : mediaUrlExpiresIn,
        participation.getContent(),
        participation.getLikeCount(),
        participation.getCommentCount(),
        participation.getVisibility().name(),
        rewards,
        participation.getJoinedAt(),
        participation.getCompletedAt(),
        rejectionReason,
        canResubmit,
        submissions);
  }
}
```

- [ ] **Step 4: Modify `ZoneEventParticipationQueryService.java`**

Replace the full file content with:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryItemResDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryPageResDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmissionHistoryItemResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 참여 조회: 이벤트별 내 참여 목록과, 필터·커서 페이징 기반 전체 이력(제출 이력·반려 사유·재제출 가능 여부 포함). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneEventParticipationQueryService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final FileStorageService fileStorageService;

  @Value("${file-storage.s3.presigned-url-expiration:3600}")
  private int presignedUrlExpiration;

  /** 이 이벤트에 대한 내 참여 목록(최신순, 취소 포함). */
  public List<ParticipationResDto> myEventParticipations(AuthenticatedUser user, UUID eventId) {
    UUID userId = requireUserId(user);
    return participationRepository
        .findByEvent_IdAndUserIdOrderByJoinedAtDesc(eventId, userId)
        .stream()
        .map(participation -> ParticipationResDto.of(participation, null))
        .toList();
  }

  /** 내 참여 이력. joinedAt 내림차순 커서 페이징 + 구역·타입·상태·기간 필터. */
  public ParticipationHistoryPageResDto history(
      AuthenticatedUser user,
      String zone,
      String type,
      List<ParticipationStatus> statuses,
      OffsetDateTime from,
      OffsetDateTime to,
      String cursor,
      Integer size) {
    UUID userId = requireUserId(user);
    String zoneId = zone == null || zone.isBlank() ? null : ChatZone.fromString(zone).name();
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<ZoneEventParticipation> spec =
        buildSpec(userId, zoneId, type, statuses, from, to, decoded);
    List<ZoneEventParticipation> rows =
        participationRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<ZoneEventParticipation> page = hasNext ? rows.subList(0, pageSize) : rows;

    Map<UUID, List<GrantedRewardDto>> rewardsByParticipation = rewardsFor(page);
    Map<UUID, List<ZoneEventSubmission>> submissionsByParticipation = submissionsFor(page);
    OffsetDateTime now = OffsetDateTime.now();
    List<ParticipationHistoryItemResDto> items =
        page.stream()
            .map(
                participation ->
                    toHistoryItem(
                        participation,
                        rewardsByParticipation.getOrDefault(participation.getId(), List.of()),
                        submissionsByParticipation.getOrDefault(participation.getId(), List.of()),
                        now))
            .toList();

    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new ParticipationHistoryPageResDto(items, nextCursor, hasNext);
  }

  private ParticipationHistoryItemResDto toHistoryItem(
      ZoneEventParticipation participation,
      List<GrantedRewardDto> rewards,
      List<ZoneEventSubmission> submissions,
      OffsetDateTime now) {
    List<ZoneEventSubmission> sorted =
        submissions.stream()
            .sorted(Comparator.comparing(ZoneEventSubmission::getAttemptNo).reversed())
            .toList();
    ZoneEventSubmission latest = sorted.isEmpty() ? null : sorted.get(0);
    String rejectionReason =
        latest != null && latest.getReviewStatus() == SubmissionReviewStatus.REJECTED
            ? latest.getRejectionReason()
            : null;
    boolean canResubmit =
        participation.getStatus() == ParticipationStatus.FAIL
            && now.isBefore(participation.getEvent().endsAt());
    List<SubmissionHistoryItemResDto> submissionItems =
        sorted.stream()
            .map(s -> SubmissionHistoryItemResDto.of(s, presignedUrl(s.getMediaFileKey())))
            .toList();
    return ParticipationHistoryItemResDto.of(
        participation,
        presignedUrl(participation.getMediaFileKey()),
        presignedUrlExpiration,
        rewards,
        rejectionReason,
        canResubmit,
        submissionItems);
  }

  private Map<UUID, List<GrantedRewardDto>> rewardsFor(List<ZoneEventParticipation> page) {
    if (page.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = page.stream().map(ZoneEventParticipation::getId).toList();
    return rewardGrantRepository.findByParticipationIdInAndRevokedAtIsNull(ids).stream()
        .collect(
            Collectors.groupingBy(
                RewardGrant::getParticipationId,
                Collectors.mapping(
                    grant -> GrantedRewardDto.of(grant, grant.getReward().getPointAmount()),
                    Collectors.toList())));
  }

  private Map<UUID, List<ZoneEventSubmission>> submissionsFor(List<ZoneEventParticipation> page) {
    if (page.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = page.stream().map(ZoneEventParticipation::getId).toList();
    return submissionRepository.findByParticipation_IdIn(ids).stream()
        .collect(Collectors.groupingBy(s -> s.getParticipation().getId()));
  }

  private Specification<ZoneEventParticipation> buildSpec(
      UUID userId,
      String zoneId,
      String type,
      List<ParticipationStatus> statuses,
      OffsetDateTime from,
      OffsetDateTime to,
      Cursor cursor) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userId"), userId));
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      if (type != null && !type.isBlank()) {
        predicates.add(cb.equal(root.get("event").get("type").get("typeCode"), type));
      }
      if (statuses != null && !statuses.isEmpty()) {
        predicates.add(root.get("status").in(statuses));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("joinedAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("joinedAt"), to));
      }
      if (cursor != null) {
        Predicate earlier = cb.lessThan(root.get("joinedAt"), cursor.joinedAt());
        Predicate sameTimeLowerId =
            cb.and(
                cb.equal(root.get("joinedAt"), cursor.joinedAt()),
                cb.lessThan(root.get("id"), cursor.id()));
        predicates.add(cb.or(earlier, sameTimeLowerId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String presignedUrl(String mediaFileKey) {
    return mediaFileKey == null ? null : fileStorageService.getPresignedUrl(mediaFileKey);
  }

  private String encodeCursor(ZoneEventParticipation participation) {
    String raw = participation.getJoinedAt() + "|" + participation.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid participation cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid participation cursor.");
    }
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }

  private record Cursor(OffsetDateTime joinedAt, UUID id) {}
}
```

Note: `fileStorageService.getPresignedUrl` is now called once per submission (not just once per participation) — this is intentional (each attempt's photo needs its own URL) and matches the design doc's `submissions[].mediaUrl` field.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.ZoneEventParticipationQueryServiceTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all green (including the two new tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/response/ParticipationHistoryItemResDto.java src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationQueryService.java src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventParticipationQueryServiceTest.java
git commit -m "feat(zoneevent): surface submission history, rejection reason, and canResubmit in participation history"
```

---

### Task 9: OpenAPI documentation

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml`

**Interfaces:**
- Consumes: nothing (pure documentation, but must accurately reflect Tasks 4–8's finished contracts).

- [ ] **Step 1: Update the `AuthTargetDetail` schema is unchanged — add `targetId` is already present** (no-op; the existing schema at line ~8632 already has every field `targets[]` needs, since it reuses `AuthTargetDetail`).

- [ ] **Step 2: Update the `ZoneEventDetail` schema**

Find the `ZoneEventDetail` schema (search for `    ZoneEventDetail:`) and replace the block from `authTarget:` through `round:` with:

```yaml
        authTarget:
          allOf:
            - $ref: "#/components/schemas/AuthTargetDetail"
          nullable: true
          description: 하위 호환용 단일 필드(ACTIVE 타겟 중 하나). 신규 클라이언트는 targets를 쓸 것.
        targets:
          type: array
          items:
            $ref: "#/components/schemas/AuthTargetDetail"
          description: 이 이벤트의 ACTIVE 타겟 전체. 참여·제출 시 targetId로 하나를 선택합니다.
        slotCode:
          type: string
          nullable: true
          example: 1-A
        deadline:
          type: string
          format: date-time
          description: 신규 참여·재제출 모두 이 시각 이후 거부됩니다(endsAt과 동일 값).
        successCount:
          type: integer
          format: int64
        successLimitPerUser:
          type: integer
          example: 1
        myRemainingAttempts:
          type: integer
          nullable: true
          description: 로그인 시에만 채워집니다.
        myParticipation:
          nullable: true
          description: 로그인 시, 이 이벤트에 대한 내 가장 최근 참여 요약. 참여 이력이 없으면 null.
          allOf:
            - $ref: "#/components/schemas/MyParticipation"
        round:
          nullable: true
          description: 회차 정보(향후 확장). 현재는 null.
```

- [ ] **Step 3: Add the `MyParticipation` schema**

Add a new schema right after `ZoneEventDetail` (before `RoundStatusZoneSlot`):

```yaml
    MyParticipation:
      type: object
      properties:
        participationId:
          type: string
          format: uuid
        status:
          type: string
          enum:
            - JOINED
            - SUBMITTED
            - UNDER_REVIEW
            - SUCCESS
            - FAIL
            - CANCELLED
            - REVOKED
        canResubmit:
          type: boolean
          description: status가 FAIL이고 아직 deadline 전이면 true.
```

- [ ] **Step 4: Update `ParticipationJoinRequest`**

Replace:

```yaml
    ParticipationJoinRequest:
      type: object
      required:
        - latitude
        - longitude
      properties:
        latitude:
          type: number
          format: double
          minimum: -90
          maximum: 90
          example: 35.1532
        longitude:
          type: number
          format: double
          minimum: -180
          maximum: 180
          example: 129.1182
```

with:

```yaml
    ParticipationJoinRequest:
      type: object
      required:
        - targetId
        - latitude
        - longitude
      properties:
        targetId:
          type: string
          format: uuid
          description: 참여할 타겟(선택 장소). 이 이벤트의 ACTIVE 타겟이어야 합니다.
        latitude:
          type: number
          format: double
          minimum: -90
          maximum: 90
          example: 35.1532
        longitude:
          type: number
          format: double
          minimum: -180
          maximum: 180
          example: 129.1182
```

- [ ] **Step 5: Update `ParticipationSubmitRequest`**

Replace:

```yaml
    ParticipationSubmitRequest:
      type: object
      required:
        - mediaFileKey
      properties:
        mediaFileKey:
```

with:

```yaml
    ParticipationSubmitRequest:
      type: object
      required:
        - targetId
        - mediaFileKey
      properties:
        targetId:
          type: string
          format: uuid
          description: 제출할 타겟(선택 장소). 재제출 시 최초 제출과 다른 타겟을 고를 수 있습니다.
        mediaFileKey:
```

- [ ] **Step 6: Update `SubmitResult`**

Replace:

```yaml
    SubmitResult:
      type: object
      properties:
        participation:
          $ref: "#/components/schemas/Participation"
        rewards:
```

with:

```yaml
    SubmitResult:
      type: object
      properties:
        participation:
          $ref: "#/components/schemas/Participation"
        submissionId:
          type: string
          format: uuid
          description: 이번 호출이 만든 제출 이력의 id.
        attemptNo:
          type: integer
          description: 이 참여에서 몇 번째 제출 시도인지(1부터 시작).
          example: 1
        rewards:
```

- [ ] **Step 7: Update the `Participation` `status` enum description context and add `ParticipationHistoryItem` fields**

Find `ParticipationHistoryItem` and replace the block from `rewards:` through `completedAt:`'s closing (the last property) with:

```yaml
        rewards:
          type: array
          items:
            $ref: "#/components/schemas/GrantedReward"
        joinedAt:
          type: string
          format: date-time
        completedAt:
          type: string
          format: date-time
          nullable: true
        rejectionReason:
          type: string
          nullable: true
          description: 최신 제출이 반려됐을 때만 채워집니다.
        canResubmit:
          type: boolean
          description: status가 FAIL이고 아직 이벤트 deadline 전이면 true.
        submissions:
          type: array
          items:
            $ref: "#/components/schemas/SubmissionHistoryItem"
          description: attemptNo 내림차순(최신 제출이 먼저).
```

- [ ] **Step 8: Add the `SubmissionHistoryItem` schema**

Add right after `ParticipationHistoryItem` (before `ParticipationHistoryPage`):

```yaml
    SubmissionHistoryItem:
      type: object
      properties:
        submissionId:
          type: string
          format: uuid
        attemptNo:
          type: integer
          example: 1
        targetId:
          type: string
          format: uuid
        placeName:
          type: string
          example: 광안대교 전망 포인트
        mediaUrl:
          type: string
          nullable: true
          description: presigned URL
        reviewStatus:
          type: string
          enum:
            - UNDER_REVIEW
            - SUCCESS
            - REJECTED
        rejectionReason:
          type: string
          nullable: true
        submittedAt:
          type: string
          format: date-time
        reviewedAt:
          type: string
          format: date-time
          nullable: true
```

- [ ] **Step 9: Update the 4 endpoint descriptions and examples**

`GET /api/v1/zone-events/{eventId}` — replace the `description`:

```yaml
      description: |
        이벤트 상세를 조회합니다. 촬영 가이드·예시 이미지·우수 보상·성공 참여자 수·남은 참여 가능 횟수·이 이벤트의 ACTIVE 타겟 전체(targets)·
        슬롯 코드(slotCode)·마감 시각(deadline)을 포함합니다. 비로그인도 허용하며, myRemainingAttempts와 myParticipation은
        로그인한 경우에만 채워집니다.
```

`POST /api/v1/zone-events/{eventId}/participations` — replace `description` and `example`:

```yaml
      description: |
        선택한 targetId와 현재 GPS 좌표로 이벤트 참여를 시작합니다. targetId가 이 이벤트의 ACTIVE 타겟이 아니면 404, 타겟 반경 밖이면
        400, 이미 열린 참여(반려되어 재제출 대기 중인 참여 포함)가 있으면 409, 이벤트 마감(deadline) 이후면 409입니다.
```

```yaml
            example:
              targetId: 550e8400-e29b-41d4-a716-446655440000
              latitude: 35.1532
              longitude: 129.1182
```

`POST /api/v1/zone-events/{eventId}/participations/{participationId}/submit` — replace `description` and `example`, and add a `409` case for `ended`:

```yaml
      description: |
        선택한 targetId·미디어(fileKey)·촬영 좌표로 인증을 제출합니다. 최초 제출(JOINED)뿐 아니라 반려 후 재제출(FAIL)도 이 엔드포인트로
        처리하며, 호출마다 새 제출 이력(submissionId, attemptNo)이 쌓이고 이전 이력은 바뀌지 않습니다. 재제출 시 최초 제출과 다른 targetId를
        고를 수 있습니다. AUTO 판정이면 즉시 성공 확정+기본 보상, 아니면 검수 대기입니다. 본인이 업로드하고, 아직 다른 제출에 쓰이지 않았으며,
        업로드한 지 오래되지 않은 미디어만 제출할 수 있습니다.
```

```yaml
            example:
              targetId: 550e8400-e29b-41d4-a716-446655440000
              mediaFileKey: uploads/images/photo.jpg
              content: 광안대교 야경 미쳤다
              latitude: 35.1532
              longitude: 129.1182
              capturedAt: 2026-09-06T21:30:00+09:00
```

Add a `409` response entry to this endpoint's `responses` (right after the existing `"409"` entry — replace it):

```yaml
        "409":
          description: 참여 상태가 제출·재제출 가능하지 않거나(UNDER_REVIEW/SUCCESS 등) 이벤트 마감(deadline) 이후
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
```

`GET /api/v1/users/me/zone-event-participations` — replace its `description`:

```yaml
      description: |
        내 구역 이벤트 참여 이력을 커서 페이징으로 조회합니다. 구역·타입·상태·기간으로 필터할 수 있습니다.
```

with:

```yaml
      description: |
        내 구역 이벤트 참여 이력을 커서 페이징으로 조회합니다. 구역·타입·상태·기간으로 필터할 수 있습니다. 각 항목에 반려 사유
        (rejectionReason), 재제출 가능 여부(canResubmit), 제출 시도별 이력(submissions)을 포함합니다.
```

- [ ] **Step 10: Validate the YAML**

Run (from repo root, using Node if available, matching the convention referenced in PR #251's description — "Swagger 확인 — js-yaml로 openapi3.yaml 파싱"):

```bash
node -e "const yaml=require('js-yaml');const fs=require('fs');const doc=yaml.load(fs.readFileSync('src/main/resources/static/docs/openapi3.yaml','utf8'));console.log('paths:',Object.keys(doc.paths).length,'schemas:',Object.keys(doc.components.schemas).length);"
```

Expected: prints counts with no parse error. If `js-yaml` isn't installed, `npm install --no-save js-yaml` first, or validate with any other locally-available YAML parser — the goal is confirming the file still parses as valid YAML after these edits.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(zoneevent): document targetId, resubmission, deadline, and submission history in openapi3.yaml"
```

---

### Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

From an ASCII-path clone (per the Global Constraints and this repo's established workaround — see `docs/superpowers/plans/2026-09-08-admin-auth-target-api.md` Task 6 Step 4 for why):

```bash
./gradlew test --no-daemon -g "C:\\gradle-home"
```

Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Run the full check (includes coverage gate and spotless)**

```bash
./gradlew check --no-daemon -g "C:\\gradle-home"
```

Expected: BUILD SUCCESSFUL. If Spotless formatting fails, run `./gradlew spotlessApply --no-daemon -g "C:\\gradle-home"` and re-run `check`. If the coverage gate fails on any newly-touched class, add the missing test case(s) called out by the JaCoCo report rather than lowering the gate.

- [ ] **Step 3: Manually sanity-check the new error keys resolve**

```bash
grep -c "error.zone_event.ended" src/main/resources/messages*.properties
```

Expected: `1` for each of the 4 files (confirms no duplicate/missing key across locales).

- [ ] **Step 4: Confirm no leftover references to the removed `SubmitResultResDto` factory arity**

```bash
grep -rn "SubmitResultResDto.of(" src/main/java src/test/java
```

Expected: every call site passes `submissionId`/`attemptNo` (5 or 6 args, never the old 3/4-arg shape). Manually eyeball each match.

- [ ] **Step 5: Final commit if Step 2 required any fixes**

```bash
git add -A
git commit -m "fix(zoneevent): spotless formatting and coverage gate fixes for participation resubmit API"
```

(Skip this step entirely if Step 2 passed clean on the first run — no empty commits.)
