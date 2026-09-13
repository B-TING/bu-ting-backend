# 전체 참여·사진 인증 검수 API (issue #241) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement admin APIs for (1) a full participation list, (2) a submission-level photo-review queue/detail/approve/reject flow with optimistic-concurrency (`expectedRevision`) and `Idempotency-Key` support, replacing the old whole-participation approve/reject from issue #215.

**Architecture:** Two controllers under `/admin`: the existing `AdminReviewController` (`/admin/zone-event-participations`) gains a new `GET` list endpoint and loses its old `review-queue`/`approve`/`reject` (policy-incompatible — see Global Constraints). A brand-new `AdminZoneEventReviewController`/`AdminZoneEventReviewService` owns `/admin/zone-event-reviews/**` (queue, detail, submission-scoped approve/reject). A new small `IdempotencyRecord`/`IdempotencyService` (zoneevent-scoped, not a generic framework) backs the `Idempotency-Key` header on approve/reject. Concurrency is enforced two ways: a fast manual `expectedRevision` comparison, plus JPA `@Version` (already on `ZoneEventSubmission.revision`) as the real race backstop via `saveAndFlush` + catching `ObjectOptimisticLockingFailureException`.

**Tech Stack:** Java 21 / Spring Boot / Spring Data JPA (Specifications) / PostgreSQL + Flyway / JUnit 5 + Mockito (controller tests) + Testcontainers (`AbstractContainerTest`, service tests) / Jackson.

## Global Constraints

- Branch: create `feature/241-zone-event-review-api` off latest `dev` (`git pull --ff-only origin dev` first — a prior PR may have just auto-merged).
- Every new/changed controller endpoint must get a matching Korean entry in `src/main/resources/static/docs/openapi3.yaml` in the same branch (see Task 9). Validate YAML parses (`node -e "require('js-yaml').load(...)"`, `js-yaml` installed via `npm install js-yaml --no-save` in a scratch dir) before committing.
- Local test runs must use the ASCII-path clone workaround: `git clone --branch <branch> "C:/Users/조준연/Desktop/bu-ting-backend" /c/dev/bu-ting-backend-test && cd /c/dev/bu-ting-backend-test && ./gradlew check --no-daemon -g "C:\gradle-home"`. Use `./gradlew check`, not just `test` — this repo enforces 100% Jacoco line coverage and Spotless (Google Java Format); run `./gradlew spotlessApply` before `check` if formatting fails.
- **Policy decision (documented, not to be re-litigated per task):** the old `AdminReviewController`/`AdminReviewService` methods `reviewQueue()`, `approve()`, `reject()` (issue #215) grant a base reward and auto-equip titles immediately on approve. Issue #241 / parent issue #235 explicitly change this policy: approve only sets `SUCCESS` + album visibility, reward payout is a separate later confirmation step (issue #244), and title issuance must not auto-equip. These old methods are **removed** in Task 3, not kept alongside the new ones — running both would let an operator grant a reward through the old path that the new policy says shouldn't happen yet. `revoke()`/`unhide()` are out of scope for #241 (belong to #243/#244) and are kept as-is.
- All Korean user-facing strings (`ApiResponse.success` messages, error message-property values, javadoc) match the terse style already used in this domain (see `AdminReviewController.java`, `AdminZoneEventTargetController.java`).
- Message keys go in all 4 locale files: `src/main/resources/messages.properties`, `messages_en.properties`, `messages_ja.properties`, `messages_zh.properties` — append in the same order to each so line numbers stay aligned (existing convention).

---

## File Structure

**New files:**
- `src/main/resources/db/migration/V45__idempotency_key.sql`
- `src/main/java/com/butingbe/domain/zoneevent/entity/IdempotencyRecord.java`
- `src/main/java/com/butingbe/domain/zoneevent/repository/IdempotencyRecordRepository.java`
- `src/main/java/com/butingbe/domain/zoneevent/service/IdempotencyService.java`
- `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java`
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewApproveReqDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewRejectReqDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueueItemResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueuePageResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminSubmissionDetailResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDetailResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDecisionResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationListItemResDto.java`
- `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationPageResDto.java`
- Tests mirroring each of the above under `src/test/java/...` (see per-task lists).

**Modified files:**
- `src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java` — add `autoEquip` overload.
- `src/main/java/com/butingbe/domain/zoneevent/controller/AdminReviewController.java` — remove `reviewQueue`/`approve`/`reject`, add `list`.
- `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java` — same.
- `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventParticipationRepository.java` — none required (Specification-based, uses existing `JpaSpecificationExecutor`).
- `src/main/java/com/butingbe/domain/user/repository/UserRepository.java` — add keyword-search method.
- `src/test/java/com/butingbe/domain/zoneevent/controller/AdminReviewControllerTest.java`, `src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java` — trim to revoke/unhide/list coverage.
- `src/main/resources/static/docs/openapi3.yaml` — remove stale `/admin/zone-event-participations/review-queue`, `.../approve`, `.../reject` paths+schemas; add new ones.
- `src/main/resources/messages*.properties` (4 files) — new keys.
- Delete: `src/main/java/com/butingbe/domain/zoneevent/dto/response/ReviewQueueItemResDto.java`, `ReviewQueuePageResDto.java` (only consumers are the code being removed — confirmed via grep in Task 3).

---

## Task 1: `ZoneTitleService` — add a no-auto-equip variant

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java:44-71`
- Test: `src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java` (find/reuse existing file; if none exists, check `src/test/java/com/butingbe/domain/zonetitle/` first)

**Interfaces:**
- Produces: `ZoneTitleService.awardTitles(UUID userId, String zoneId, boolean autoEquip)` — new overload. Existing `awardTitles(UUID userId, String zoneId)` becomes a thin delegate that keeps its current behavior (`autoEquip = (user has zero equipped titles)`) by calling the new overload with `autoEquip=true`.

Currently `awardTitles(userId, zoneId)` computes its own `autoEquip` flag internally (`userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId) == 0`). The new admin-approve flow (Task 8) must guarantee **zero** auto-equip side effects even for a user's very first title. So the `autoEquip` intent must be an explicit caller-supplied gate that's ANDed with the "first title" condition — not a separate independent flag, since equipping only ever makes sense on a user's first title in the first place (that invariant doesn't change, we're only adding a way to suppress it).

- [ ] **Step 1: Write the failing test** (find the existing test file first via `Glob src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java`; if it exists, add these as new `@Test` methods matching its existing style/fixtures — read it first to match its `@BeforeEach` setup for `ZoneTitleDef`/`participationRepository` fixtures)

```java
@Test
@DisplayName("autoEquip=false면 첫 칭호여도 자동 장착하지 않는다")
void awardTitlesWithoutAutoEquipDoesNotEquip() {
  // reuse this test class's existing fixture that creates a ZoneTitleDef (tier 1, requiredSuccessCount=1)
  // and enough SUCCESS participations for userId+zoneId to cross tier 1.
  List<EquippedTitleResDto> awarded = zoneTitleService.awardTitles(userId, zoneId, false);

  assertThat(awarded).isNotEmpty();
  assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId)).isZero();
}

@Test
@DisplayName("autoEquip=true는 기존 동작(첫 칭호 자동 장착)과 같다")
void awardTitlesWithAutoEquipStillEquipsFirstTitle() {
  List<EquippedTitleResDto> awarded = zoneTitleService.awardTitles(userId, zoneId, true);

  assertThat(awarded).isNotEmpty();
  assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId)).isEqualTo(1);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from the ASCII clone, see Global Constraints): `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*ZoneTitleServiceTest"`
Expected: FAIL — `awardTitles(UUID, String, boolean)` does not exist (compile error).

- [ ] **Step 3: Implement**

Replace `ZoneTitleService.java:43-71`:

```java
  /** 구역 성공 누적으로 새로 도달한 칭호를 발급하고, 새로 얻은 칭호 목록을 돌려준다. 처음 칭호를 얻을 때만 자동 장착한다. */
  @Transactional
  public List<EquippedTitleResDto> awardTitles(UUID userId, String zoneId) {
    return awardTitles(userId, zoneId, true);
  }

  /**
   * 구역 성공 누적으로 새로 도달한 칭호를 발급한다. {@code autoEquip}이 false면 첫 칭호여도 자동 장착하지 않는다(운영자 검수 승인
   * 등 대표 칭호 변경을 유발하면 안 되는 호출부용).
   */
  @Transactional
  public List<EquippedTitleResDto> awardTitles(UUID userId, String zoneId, boolean autoEquip) {
    long successCount = participationRepository.countSuccessByUserAndZone(userId, zoneId);
    boolean shouldAutoEquip =
        autoEquip && userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId) == 0;

    List<UserZoneTitle> newlyEarned = new ArrayList<>();
    for (ZoneTitleDef def : titleDefRepository.findByZoneIdOrderByTierAsc(zoneId)) {
      if (def.getRequiredSuccessCount() <= successCount
          && !userZoneTitleRepository.existsByUserIdAndTitleDef_Id(userId, def.getId())) {
        newlyEarned.add(
            userZoneTitleRepository.save(
                UserZoneTitle.builder()
                    .userId(userId)
                    .titleDef(def)
                    .zoneId(zoneId)
                    .equipped(false)
                    .build()));
      }
    }
    if (shouldAutoEquip && !newlyEarned.isEmpty()) {
      // tier 오름차순으로 발급했으므로 마지막이 가장 높은 tier.
      newlyEarned.get(newlyEarned.size() - 1).equip();
    }
    if (!newlyEarned.isEmpty()) {
      cityGradeService.recordIfRisen(userId);
    }
    return newlyEarned.stream().map(EquippedTitleResDto::from).toList();
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*ZoneTitleServiceTest"`
Expected: PASS, and pre-existing tests in this file (which call the 2-arg `awardTitles`) still pass unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/zonetitle/service/ZoneTitleService.java src/test/java/com/butingbe/domain/zonetitle/service/ZoneTitleServiceTest.java
git commit -m "feat(zonetitle): add autoEquip-suppressible awardTitles overload"
```

---

## Task 2: Idempotency infrastructure

**Files:**
- Create: `src/main/resources/db/migration/V45__idempotency_key.sql`
- Create: `src/main/java/com/butingbe/domain/zoneevent/entity/IdempotencyRecord.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/repository/IdempotencyRecordRepository.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/IdempotencyService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/IdempotencyServiceTest.java`

**Interfaces:**
- Produces:
  - `IdempotencyService.findReplay(String idempotencyKey, String endpoint, String fingerprint) -> Optional<String>` — empty = no record (proceed normally); `Optional.of("")` = record exists with no body (e.g. a `Void` reject response); `Optional.of(json)` = record exists with a JSON body to deserialize. Throws `ConflictException("error.zone_event.review.idempotency_key_conflict")` if a record exists under this key but its `endpoint`/`fingerprint` don't match (key reused for a different request).
  - `IdempotencyService.save(String idempotencyKey, String endpoint, String fingerprint, Object responseBody)` — no-ops if `idempotencyKey` is null/blank. Serializes `responseBody` to JSON via the injected `ObjectMapper` (`null` body stored as empty string).
- Consumes: nothing from other tasks (self-contained; Task 8 will consume this).

- [ ] **Step 1: Write the failing test**

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.support.AbstractContainerTest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdempotencyServiceTest extends AbstractContainerTest {

  @Autowired private IdempotencyService idempotencyService;

  @Test
  @DisplayName("키가 없으면 빈 Optional을 돌려준다")
  void noRecordYieldsEmpty() {
    assertThat(idempotencyService.findReplay("missing-key", "ep", "fp")).isEmpty();
  }

  @Test
  @DisplayName("저장 후 같은 키·엔드포인트·지문이면 저장된 JSON을 그대로 돌려준다")
  void saveThenReplay() {
    idempotencyService.save("key-1", "zone-event-review-approve", "fp-1", new Sample("a", 1));

    Optional<String> replay = idempotencyService.findReplay("key-1", "zone-event-review-approve", "fp-1");

    assertThat(replay).isPresent();
    assertThat(replay.get()).contains("\"name\":\"a\"").contains("\"value\":1");
  }

  @Test
  @DisplayName("바디가 null이면 빈 문자열로 저장되고 재조회 시 빈 Optional이 아니라 빈 문자열이다")
  void saveNullBody() {
    idempotencyService.save("key-2", "zone-event-review-reject", "fp-2", null);

    assertThat(idempotencyService.findReplay("key-2", "zone-event-review-reject", "fp-2"))
        .contains("");
  }

  @Test
  @DisplayName("같은 키인데 엔드포인트나 지문이 다르면 409다")
  void mismatchedReplayConflicts() {
    idempotencyService.save("key-3", "zone-event-review-approve", "fp-3", new Sample("a", 1));

    assertThatThrownBy(() -> idempotencyService.findReplay("key-3", "zone-event-review-approve", "different-fp"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> idempotencyService.findReplay("key-3", "zone-event-review-reject", "fp-3"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("키가 비어 있으면 저장·조회 모두 아무 일도 하지 않는다")
  void blankKeyIsNoop() {
    idempotencyService.save(null, "zone-event-review-approve", "fp", new Sample("a", 1));
    idempotencyService.save("", "zone-event-review-approve", "fp", new Sample("a", 1));

    assertThat(idempotencyService.findReplay(null, "zone-event-review-approve", "fp")).isEmpty();
    assertThat(idempotencyService.findReplay("", "zone-event-review-approve", "fp")).isEmpty();
  }

  private record Sample(String name, int value) {}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*IdempotencyServiceTest"`
Expected: FAIL to compile (`IdempotencyService` doesn't exist yet).

- [ ] **Step 3: Write the migration**

`src/main/resources/db/migration/V45__idempotency_key.sql`:

```sql
-- Generic per-key replay store for POST endpoints that accept an Idempotency-Key header
-- (currently: zone-event review approve/reject, issue #241).
CREATE TABLE idempotency_key (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(300) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 4: Write the entity**

`src/main/java/com/butingbe/domain/zoneevent/entity/IdempotencyRecord.java`:

```java
package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Idempotency-Key 재전송 응답 저장소. 성공 처리 결과만 저장한다(실패한 시도는 저장하지 않음). */
@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

  @Id
  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false, length = 100)
  private String endpoint;

  @Column(nullable = false, updatable = false, length = 300)
  private String fingerprint;

  @Column(name = "response_body", columnDefinition = "text")
  private String responseBody;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public IdempotencyRecord(
      String idempotencyKey, String endpoint, String fingerprint, String responseBody) {
    this.idempotencyKey = idempotencyKey;
    this.endpoint = endpoint;
    this.fingerprint = fingerprint;
    this.responseBody = responseBody;
    this.createdAt = OffsetDateTime.now();
  }
}
```

- [ ] **Step 5: Write the repository**

`src/main/java/com/butingbe/domain/zoneevent/repository/IdempotencyRecordRepository.java`:

```java
package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {}
```

- [ ] **Step 6: Write the service**

`src/main/java/com/butingbe/domain/zoneevent/service/IdempotencyService.java`:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
import com.butingbe.domain.zoneevent.repository.IdempotencyRecordRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key 헤더 처리. 같은 키로 재전송되면 처음 처리했던 결과를 그대로 돌려준다. 키가 없으면(null/blank) 아무 것도 하지 않는다
 * — 이 헤더는 선택적이다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * 재생할 이전 응답이 있으면 그 JSON(바디가 없었으면 빈 문자열)을 돌려준다. 같은 키인데 endpoint·fingerprint가 다르면 다른 요청에
   * 키가 잘못 재사용된 것이므로 409.
   */
  @Transactional(readOnly = true)
  public Optional<String> findReplay(String idempotencyKey, String endpoint, String fingerprint) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findById(idempotencyKey)
        .map(
            record -> {
              if (!record.getEndpoint().equals(endpoint)
                  || !record.getFingerprint().equals(fingerprint)) {
                throw new ConflictException("error.zone_event.review.idempotency_key_conflict");
              }
              return record.getResponseBody() == null ? "" : record.getResponseBody();
            });
  }

  /** 처리 성공 결과를 저장한다. 키가 없으면 아무 것도 하지 않는다. */
  @Transactional
  public void save(String idempotencyKey, String endpoint, String fingerprint, Object responseBody) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return;
    }
    String json = responseBody == null ? "" : writeJson(responseBody);
    repository.save(new IdempotencyRecord(idempotencyKey, endpoint, fingerprint, json));
  }

  private String writeJson(Object responseBody) {
    try {
      return objectMapper.writeValueAsString(responseBody);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize idempotent response.", e);
    }
  }
}
```

- [ ] **Step 7: Add message key**

Append to all four `src/main/resources/messages*.properties` files (same line, matching existing append style at end of file):
- `messages.properties`: `error.zone_event.review.idempotency_key_conflict=이미 사용된 Idempotency-Key이지만 요청 내용이 다릅니다.`
- `messages_en.properties`: `error.zone_event.review.idempotency_key_conflict=This Idempotency-Key was already used for a different request.`
- `messages_ja.properties`: `error.zone_event.review.idempotency_key_conflict=このIdempotency-Keyは異なるリクエストで既に使用されています。`
- `messages_zh.properties`: `error.zone_event.review.idempotency_key_conflict=该 Idempotency-Key 已用于其他请求。`

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*IdempotencyServiceTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V45__idempotency_key.sql src/main/java/com/butingbe/domain/zoneevent/entity/IdempotencyRecord.java src/main/java/com/butingbe/domain/zoneevent/repository/IdempotencyRecordRepository.java src/main/java/com/butingbe/domain/zoneevent/service/IdempotencyService.java src/test/java/com/butingbe/domain/zoneevent/service/IdempotencyServiceTest.java src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties
git commit -m "feat(zoneevent): add Idempotency-Key replay store"
```

---

## Task 3: Remove legacy `reviewQueue`/`approve`/`reject` from `AdminReviewController`/`AdminReviewService`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminReviewController.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java`
- Delete: `src/main/java/com/butingbe/domain/zoneevent/dto/response/ReviewQueueItemResDto.java`
- Delete: `src/main/java/com/butingbe/domain/zoneevent/dto/response/ReviewQueuePageResDto.java`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminReviewControllerTest.java`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java`

**Interfaces:**
- Produces: `AdminReviewService` now only exposes `revoke(AuthenticatedUser, UUID)` and `unhide(AuthenticatedUser, UUID)` (plus the new `list(...)` added in Task 4 — don't add it in this task, just remove).
- Consumes: nothing new.

This task is pure removal + trimming existing tests. No new test-writing step (there's no new behavior — do this as one shot, then run the trimmed suite to confirm nothing else references the removed members).

- [ ] **Step 1: Confirm no other consumers before deleting**

```bash
grep -rn "ReviewQueueItemResDto\|ReviewQueuePageResDto" src/
```

Expected: only `AdminReviewService.java`, `AdminReviewController.java`, and the two test files (already confirmed during planning research — re-verify here since code may have moved).

- [ ] **Step 2: Trim `AdminReviewController.java`**

Remove the `reviewQueue`, `approve`, `reject` methods (lines 30-53 in the pre-change file) and their now-unused imports (`RejectReqDto`, `ReviewQueuePageResDto`, `SubmitResultResDto`, `RequestParam`, `RequestBody`, `Valid`, `GetMapping` stays — used by nothing else here so remove too if `list` isn't added yet; Task 4 re-adds `GetMapping`). Resulting file:

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.service.AdminReviewService;
import com.butingbe.global.common.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 참여 회수·숨김 해제. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). 검수 큐/승인/반려는 {@code /admin/zone-event-reviews}로 이동했다. */
@RestController
@RequestMapping("/admin/zone-event-participations")
@RequiredArgsConstructor
public class AdminReviewController {

  private final AdminReviewService adminReviewService;

  @PostMapping("/{participationId}/revoke")
  public ResponseEntity<ApiResponse<Void>> revoke(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    adminReviewService.revoke(user, participationId);
    return ResponseEntity.ok(ApiResponse.success("참여 회수", null));
  }

  @PostMapping("/{participationId}/unhide")
  public ResponseEntity<ApiResponse<Void>> unhide(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    adminReviewService.unhide(user, participationId);
    return ResponseEntity.ok(ApiResponse.success("숨김 해제", null));
  }
}
```

- [ ] **Step 3: Trim `AdminReviewService.java`**

Remove `reviewQueue`, `approve`, `reject`, `requireLatestSubmission`, `resolveSize`, `encodeCursor`, `decodeCursor`, the `Cursor` record, the `DEFAULT_SIZE`/`MAX_SIZE` constants, and the now-unused fields (`submissionRepository`, `rewardService`, `zoneTitleService`) and imports. `requireStatus` is still used by `revoke`, keep it. Resulting file:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.service.RewardRevokeService;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 검수: 성공 참여의 회수·신고 자동 숨김 해제. 검수 큐/승인/반려는 {@link AdminZoneEventReviewService}로 이동했다. */
@Service
@RequiredArgsConstructor
public class AdminReviewService {

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventReportRepository reportRepository;
  private final RewardRevokeService rewardRevokeService;
  private final OperatorAuthorization operatorAuthorization;

  /** SUCCESS → REVOKED + 보상 회수(포인트 되돌림, 미사용 쿠폰 회수). */
  @Transactional
  public void revoke(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.SUCCESS);
    participation.stampReview(user.id());
    participation.markRevoked();
    rewardRevokeService.revokeParticipationRewards(participationId);
  }

  /** 신고 자동 숨김 해제 + 신고 DISMISSED. */
  @Transactional
  public void unhide(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    participation.unhide();
    for (ZoneEventReport report : reportRepository.findByParticipationId(participationId)) {
      report.resolveAs(ReportStatus.DISMISSED);
    }
  }

  private ZoneEventParticipation requireStatus(UUID participationId, ParticipationStatus expected) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (participation.getStatus() != expected) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }
}
```

- [ ] **Step 4: Delete the now-orphaned DTOs**

```bash
git rm src/main/java/com/butingbe/domain/zoneevent/dto/response/ReviewQueueItemResDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/ReviewQueuePageResDto.java
```

- [ ] **Step 5: Trim `AdminReviewControllerTest.java`**

Remove the `reviewQueue`, `approve`, `reject` test methods and the `forbidden` test's dependency on `reviewQueue` (rewrite `forbidden` to hit `revoke` instead, which still needs operator auth). Remove now-unused imports (`ReviewQueuePageResDto`, `SubmitResultResDto`, `get`, `eq` stays for `revokeAndUnhide`). Resulting file:

```java
package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.service.AdminReviewService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
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
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class AdminReviewControllerTest {

  private static final UUID PID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminReviewService adminReviewService;
  @InjectMocks private AdminReviewController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.operator.forbidden", Locale.KOREAN, "운영 권한이 없습니다.");
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("회수·숨김해제 200")
  void revokeAndUnhide() throws Exception {
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/revoke", PID))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/unhide", PID))
        .andExpect(status().isOk());
    verify(adminReviewService).revoke(any(), eq(PID));
    verify(adminReviewService).unhide(any(), eq(PID));
  }

  @Test
  @DisplayName("운영 권한 없으면 403")
  void forbidden() throws Exception {
    doThrow(new ForbiddenException("error.operator.forbidden"))
        .when(adminReviewService)
        .revoke(any(), eq(PID));
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/revoke", PID))
        .andExpect(status().isForbidden());
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
}
```

- [ ] **Step 6: Trim `AdminReviewServiceTest.java`**

Keep only: `revokeReversesReward` (rewrite so it builds its own SUCCESS participation directly instead of calling the now-removed `reviewService.approve(...)` — grant the reward via `userPointService`/`RewardGrant` fixtures directly, matching how `revokeReversesReward` elsewhere in this codebase sets up state without going through approve — check `RewardRevokeServiceTest` for the direct-grant fixture pattern used there), `unhideDismissesReports`, and the `invalidStateTransitions`/`notFound` cases trimmed to only cover `revoke`/`unhide`. Remove `reviewQueueListsUnderReviewAndHidden`, `approveGrantsReward`, `rejectMarksFail`, `queueCursorPagingAndEdges`, `approveWithoutSubmissionThrows`, `rejectThenResubmitRoundTrip`, and unused fixtures (`savedSubmission`, `savedTarget` if `revokeReversesReward`'s rewrite no longer needs a submission — a SUCCESS participation doesn't require one) and unused imports/fields (`submitService`, `participationService`, `authTargetRepository`, `submissionRepository` if unused after rewrite, `SubmissionReviewStatus`, `ParticipationSubmitReqDto`, `ParticipationResDto`, `ReviewQueuePageResDto`, `SubmitResultResDto`, `ZoneEventTargetKind`, `FileMetadataRepository`/`savedFile` if unused). Concretely:

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private UserRepository userRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("포인트")
            .pointAmount(50)
            .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("회수하면 REVOKED가 되고 지급 포인트가 되돌아간다")
  void revokeReversesReward() {
    ZoneEventParticipation p = participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    userPointService.grant(p.getUserId(), p.getId(), "POINT_BASE", 50);
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);

    reviewService.revoke(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.REVOKED);
    assertThat(userPointService.getBalance(p.getUserId())).isZero();
  }

  @Test
  @DisplayName("숨김 해제하면 hidden이 풀리고 신고가 DISMISSED된다")
  void unhideDismissesReports() {
    ZoneEventParticipation p = participationRepository.save(participation(ParticipationStatus.SUCCESS, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    reviewService.unhide(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getHidden()).isFalse();
    assertThat(reportRepository.findByParticipationId(p.getId()).get(0).getStatus())
        .isEqualTo(ReportStatus.DISMISSED);
  }

  @Test
  @DisplayName("상태가 맞지 않으면 회수는 409다")
  void invalidStateTransitions() {
    ZoneEventParticipation joined = participationRepository.save(participation(ParticipationStatus.JOINED, false));
    assertThatThrownBy(() -> reviewService.revoke(operator, joined.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("없는 참여 회수·숨김해제는 404다")
  void notFound() {
    assertThatThrownBy(() -> reviewService.revoke(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> reviewService.unhide(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private ZoneEventParticipation participation(ParticipationStatus status, boolean hidden) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser("p").getId())
            .status(status)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    if (hidden) {
      ReflectionTestUtils.setField(p, "hidden", true);
    }
    if (status == ParticipationStatus.SUCCESS) {
      ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    }
    return p;
  }

  private User savedUser(String nick) {
    return userRepository.save(
        User.builder()
            .email(nick + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nick)
            .role(UserRole.USER)
            .build());
  }
}
```

**Before writing this**, check `UserPointService` for the exact grant-by-catalog-code method signature (`grep -n "public.*grant" src/main/java/com/butingbe/domain/reward/service/UserPointService.java`) and adjust `userPointService.grant(...)` call above to match — the goal is just "give this participation 50 points through the real ledger so `revoke` has something real to reverse," using whatever the existing public API for that is (check `RewardService.grantBaseReward` if `UserPointService` doesn't expose grant-by-code directly, and call that instead with `event.getBaseReward()`).

- [ ] **Step 7: Run affected tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminReviewControllerTest" --tests "*AdminReviewServiceTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(zoneevent): remove legacy whole-participation approve/reject (superseded by #241 submission-scoped review)"
```

---

## Task 4: `GET /admin/zone-event-participations` — full participation list

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationListItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationPageResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminReviewController.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminReviewService.java`
- Modify: `src/main/java/com/butingbe/domain/user/repository/UserRepository.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminReviewServiceTest.java` (add cases)
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminReviewControllerTest.java` (add a case)

**Interfaces:**
- Produces: `AdminReviewService.list(AuthenticatedUser user, UUID roundId, UUID eventId, String zoneId, UUID userId, String status, String keyword, Integer page, Integer size) -> AdminParticipationPageResDto`
- Consumes: `UserRepository` (new keyword-search method), `ZoneEventParticipationRepository` (existing `JpaSpecificationExecutor`).

- [ ] **Step 1: Write the failing service test** (append to `AdminReviewServiceTest.java`)

```java
  @Test
  @DisplayName("전체 참여 목록은 roundId·eventId·zoneId·userId·status·keyword로 필터링하고 페이지 정보를 돌려준다")
  void listFiltersAndPages() {
    ZoneEventParticipation match =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    participationRepository.save(participation(ParticipationStatus.FAIL, false)); // status 안 맞음

    AdminParticipationPageResDto page =
        reviewService.list(
            operator, null, event.getId(), event.getZoneId(), null, "SUCCESS", null, 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).participationId()).isEqualTo(match.getId().toString());
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("keyword는 참여자 닉네임·이메일로 찾는다")
  void listFiltersByKeyword() {
    User target = savedUser("특이닉네임");
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(target.getId())
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    participationRepository.save(p);
    participationRepository.save(participation(ParticipationStatus.SUCCESS, false));

    AdminParticipationPageResDto page =
        reviewService.list(operator, null, null, null, null, null, "특이닉네임", 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).userId()).isEqualTo(target.getId().toString());
  }

  @Test
  @DisplayName("운영자가 아니면 목록 조회는 403이다")
  void listForbidden() {
    AuthenticatedUser normalUser = AuthenticatedUser.from(userRepository.save(userEntity("normal")));
    assertThatThrownBy(() -> reviewService.list(normalUser, null, null, null, null, null, null, 1, 20))
        .isInstanceOf(com.butingbe.global.error.exception.ForbiddenException.class);
  }

  private User userEntity(String nick) {
    return User.builder()
        .email(nick + "-" + UUID.randomUUID() + "@example.com")
        .provider("google")
        .providerId("google-" + UUID.randomUUID())
        .name(new Name("Kim", "Tester"))
        .nickname(nick)
        .role(UserRole.USER)
        .build();
  }
```

(Rename the existing private `user(String)` helper from Task 3's rewrite to `userEntity(String)` if a name collision would occur — check the file as it stands after Task 3 before pasting; keep only one such helper.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminReviewServiceTest"`
Expected: FAIL to compile — `list(...)` and `AdminParticipationPageResDto` don't exist.

- [ ] **Step 3: Add the keyword-search repository method**

Modify `src/main/java/com/butingbe/domain/user/repository/UserRepository.java`:

```java
package com.butingbe.domain.user.repository;

import com.butingbe.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  boolean existsByEmail(String email);

  /** 관리자 검색용: 닉네임 또는 이메일에 keyword가 포함된 유저. */
  List<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
      String nickname, String email);
}
```

- [ ] **Step 4: Write the response DTOs**

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationListItemResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;

/** 관리자 전체 참여 목록 한 행. */
public record AdminParticipationListItemResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String status,
    Boolean success,
    boolean hidden,
    OffsetDateTime joinedAt,
    OffsetDateTime completedAt) {

  public static AdminParticipationListItemResDto of(ZoneEventParticipation p) {
    return new AdminParticipationListItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        p.getStatus().name(),
        p.getSuccess(),
        Boolean.TRUE.equals(p.getHidden()),
        p.getJoinedAt(),
        p.getCompletedAt());
  }
}
```

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminParticipationPageResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** page/size 기반 페이지 응답(1-based page). */
public record AdminParticipationPageResDto(
    List<AdminParticipationListItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
```

- [ ] **Step 5: Implement `AdminReviewService.list(...)`**

Add to `AdminReviewService.java` (new imports: `AdminParticipationListItemResDto`, `AdminParticipationPageResDto`, `UserRepository`, `ChatZone`, `jakarta.persistence.criteria.Predicate`, `java.util.ArrayList`, `java.util.List`, `org.springframework.data.domain.Page`, `PageRequest`, `Sort`, `org.springframework.data.jpa.domain.Specification`; new field `private final UserRepository userRepository;`):

```java
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  /** 전체 참여 목록. roundId/eventId/zoneId/userId/status/keyword(닉네임·이메일)로 필터링한다. */
  @Transactional(readOnly = true)
  public AdminParticipationPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      UUID eventId,
      String zoneId,
      UUID userId,
      String status,
      String keyword,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    ParticipationStatus statusFilter = status == null || status.isBlank() ? null : ParticipationStatus.valueOf(status.toUpperCase());
    String resolvedZoneId = zoneId == null || zoneId.isBlank() ? null : ChatZone.fromString(zoneId).name();

    List<UUID> keywordUserIds = null;
    if (keyword != null && !keyword.isBlank()) {
      keywordUserIds =
          userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword)
              .stream()
              .map(u -> u.getId())
              .toList();
      if (keywordUserIds.isEmpty()) {
        return new AdminParticipationPageResDto(List.of(), pageNumber, pageSize, 0, 0, false);
      }
    }

    Specification<ZoneEventParticipation> spec =
        buildListSpec(roundId, eventId, resolvedZoneId, userId, statusFilter, keywordUserIds);
    Page<ZoneEventParticipation> result =
        participationRepository.findAll(
            spec, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.desc("joinedAt"))));

    List<AdminParticipationListItemResDto> items =
        result.getContent().stream().map(AdminParticipationListItemResDto::of).toList();
    return new AdminParticipationPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  private Specification<ZoneEventParticipation> buildListSpec(
      UUID roundId,
      UUID eventId,
      String zoneId,
      UUID userId,
      ParticipationStatus status,
      List<UUID> keywordUserIds) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (roundId != null) {
        predicates.add(cb.equal(root.get("event").get("roundId"), roundId));
      }
      if (eventId != null) {
        predicates.add(cb.equal(root.get("event").get("id"), eventId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      if (userId != null) {
        predicates.add(cb.equal(root.get("userId"), userId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (keywordUserIds != null) {
        predicates.add(root.get("userId").in(keywordUserIds));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
```

- [ ] **Step 6: Add the controller endpoint**

Add to `AdminReviewController.java` (new imports `AdminParticipationPageResDto`, `GetMapping`, `RequestParam`):

```java
  @GetMapping
  public ResponseEntity<ApiResponse<AdminParticipationPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) String zoneId,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "전체 참여 목록",
            adminReviewService.list(user, roundId, eventId, zoneId, userId, status, keyword, page, size)));
  }
```

- [ ] **Step 7: Add a controller test case** (append to `AdminReviewControllerTest.java`; add `when`/`get`/`ArgumentMatchers.any` static imports back if removed in Task 3)

```java
  @Test
  @DisplayName("전체 참여 목록 200")
  void list() throws Exception {
    when(adminReviewService.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new com.butingbe.domain.zoneevent.dto.response.AdminParticipationPageResDto(
            java.util.List.of(), 1, 20, 0, 0, false));
    mockMvc.perform(get("/admin/zone-event-participations")).andExpect(status().isOk());
  }
```

(Re-add `import static org.mockito.Mockito.when;` and `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;` at the top if Task 3 removed them.)

- [ ] **Step 8: Run tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminReviewServiceTest" --tests "*AdminReviewControllerTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(zoneevent): add GET /admin/zone-event-participations full list"
```

---

## Task 5: `AdminZoneEventReviewController`/`Service` — review queue (`GET /admin/zone-event-reviews`)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueueItemResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueuePageResDto.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewControllerTest.java`

**Interfaces:**
- Produces: `AdminZoneEventReviewService.queue(AuthenticatedUser user, UUID roundId, UUID eventId, String zoneId, Integer page, Integer size) -> AdminReviewQueuePageResDto`. This class will also grow `detail`/`approve`/`reject` in Tasks 6-8 — write the class now with just `queue` plus its constructor dependencies (`ZoneEventParticipationRepository`, `OperatorAuthorization`) so later tasks add fields/methods rather than create the class from scratch.
- Consumes: nothing new beyond existing repos.

- [ ] **Step 1: Write the failing service test**

`src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewServiceTest.java`:

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ForbiddenException;
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
class AdminZoneEventReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(), "op@example.com", "op", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("검수 큐는 UNDER_REVIEW 참여만, roundId·eventId·zoneId로 필터링해서 돌려준다")
  void queueListsOnlyUnderReview() {
    ZoneEventParticipation underReview = participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW));
    participationRepository.save(participation(ParticipationStatus.SUCCESS));

    AdminReviewQueuePageResDto queue = reviewService.queue(operator, null, event.getId(), event.getZoneId(), 1, 20);

    assertThat(queue.items()).hasSize(1);
    assertThat(queue.items().get(0).participationId()).isEqualTo(underReview.getId().toString());
    assertThat(queue.page()).isEqualTo(1);
  }

  @Test
  @DisplayName("운영자가 아니면 검수 큐 조회는 403이다")
  void queueForbidden() {
    AuthenticatedUser normalUser =
        AuthenticatedUser.from(
            userRepository.save(
                User.builder()
                    .email("normal-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("normal")
                    .role(UserRole.USER)
                    .build()));
    assertThatThrownBy(() -> reviewService.queue(normalUser, null, null, null, 1, 20))
        .isInstanceOf(ForbiddenException.class);
  }

  private ZoneEventParticipation participation(ParticipationStatus status) {
    return participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser("p").getId())
            .status(status)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build());
  }

  private User savedUser(String nick) {
    return userRepository.save(
        User.builder()
            .email(nick + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nick)
            .role(UserRole.USER)
            .build());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Write the response DTOs**

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueueItemResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;

/** 검수 큐 항목. */
public record AdminReviewQueueItemResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String currentSubmissionId,
    OffsetDateTime joinedAt) {

  public static AdminReviewQueueItemResDto of(ZoneEventParticipation p) {
    return new AdminReviewQueueItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        p.getCurrentSubmissionId() == null ? null : p.getCurrentSubmissionId().toString(),
        p.getJoinedAt());
  }
}
```

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewQueuePageResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

public record AdminReviewQueuePageResDto(
    List<AdminReviewQueueItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
```

- [ ] **Step 4: Write the service**

`src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java`:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueueItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사진 인증 검수: 큐 조회, 상세 조회, 제출 단위 승인/반려. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;

  /** 검수 큐: UNDER_REVIEW 참여만, roundId/eventId/zoneId로 필터링, joinedAt 오름차순(먼저 온 순). */
  @Transactional(readOnly = true)
  public AdminReviewQueuePageResDto queue(
      AuthenticatedUser user, UUID roundId, UUID eventId, String zoneId, Integer page, Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String resolvedZoneId = zoneId == null || zoneId.isBlank() ? null : ChatZone.fromString(zoneId).name();

    Specification<ZoneEventParticipation> spec = buildQueueSpec(roundId, eventId, resolvedZoneId);
    Page<ZoneEventParticipation> result =
        participationRepository.findAll(
            spec, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.asc("joinedAt"))));

    List<AdminReviewQueueItemResDto> items =
        result.getContent().stream().map(AdminReviewQueueItemResDto::of).toList();
    return new AdminReviewQueuePageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  private Specification<ZoneEventParticipation> buildQueueSpec(UUID roundId, UUID eventId, String zoneId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("status"), ParticipationStatus.UNDER_REVIEW));
      if (roundId != null) {
        predicates.add(cb.equal(root.get("event").get("roundId"), roundId));
      }
      if (eventId != null) {
        predicates.add(cb.equal(root.get("event").get("id"), eventId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
```

- [ ] **Step 5: Write the controller**

`src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java`:

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReviewService;
import com.butingbe.global.common.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사진 인증 검수 큐·상세·승인·반려. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-reviews")
@RequiredArgsConstructor
public class AdminZoneEventReviewController {

  private final AdminZoneEventReviewService adminZoneEventReviewService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminReviewQueuePageResDto>> queue(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) String zoneId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "검수 큐 조회", adminZoneEventReviewService.queue(user, roundId, eventId, zoneId, page, size)));
  }
}
```

- [ ] **Step 6: Write the controller test**

`src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewControllerTest.java`:

```java
package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReviewService;
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
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class AdminZoneEventReviewControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventReviewService adminZoneEventReviewService;
  @InjectMocks private AdminZoneEventReviewController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("검수 큐 200")
  void queue() throws Exception {
    when(adminZoneEventReviewService.queue(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminReviewQueuePageResDto(List.of(), 1, 20, 0, 0, false));
    mockMvc.perform(get("/admin/zone-event-reviews")).andExpect(status().isOk());
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
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest" --tests "*AdminZoneEventReviewControllerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(zoneevent): add GET /admin/zone-event-reviews queue"
```

---

## Task 6: Review detail (`GET /admin/zone-event-reviews/{participationId}`)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminSubmissionDetailResDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDetailResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java` (add `detail`)
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java` (add endpoint)
- Test: append to `AdminZoneEventReviewServiceTest.java`, `AdminZoneEventReviewControllerTest.java`

**Interfaces:**
- Produces: `AdminZoneEventReviewService.detail(AuthenticatedUser user, UUID participationId) -> AdminReviewDetailResDto`. New constructor deps: `ZoneEventSubmissionRepository`, `UserRepository`, `FileStorageService` (presigned media URL, same pattern as `ZoneEventParticipationQueryService`).
- Consumes: `ZoneEventSubmission` entity fields (target/reward snapshot already on the submission and event), `User` for display info.

- [ ] **Step 1: Write the failing test** (append to `AdminZoneEventReviewServiceTest.java`)

```java
  @Test
  @DisplayName("상세 조회는 사용자 표시 정보·현재 제출·전체 제출 이력·타겟/보상 스냅샷을 담는다")
  void detailReturnsFullPicture() {
    User participant = savedUser("참가자닉");
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(participant.getId())
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("장소")
                .latitude(35.1)
                .longitude(129.1)
                .radiusM(100)
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
                .mediaFileKey("uploads/images/photo.jpg")
                .gpsLat(35.1)
                .gpsLng(129.1)
                .capturedAt(OffsetDateTime.now())
                .build());
    ReflectionTestUtils.setField(p, "currentSubmissionId", submission.getId());
    participationRepository.save(p);

    AdminReviewDetailResDto detail = reviewService.detail(operator, p.getId());

    assertThat(detail.userNickname()).isEqualTo("참가자닉");
    assertThat(detail.currentSubmission().submissionId()).isEqualTo(submission.getId().toString());
    assertThat(detail.submissionHistory()).hasSize(1);
    assertThat(detail.rewardSnapshot().points()).isEqualTo(50);
    assertThat(detail.currentSubmission().placeName()).isEqualTo("장소");
  }

  @Test
  @DisplayName("없는 참여 상세 조회는 404다")
  void detailNotFound() {
    assertThatThrownBy(() -> reviewService.detail(operator, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }
```

Add the needed `@Autowired` fields/imports to the test class: `ZoneEventAuthTargetRepository authTargetRepository`, `ZoneEventSubmissionRepository submissionRepository`, plus entity imports `ZoneEventAuthTarget`, `ZoneEventSubmission`, `ZoneEventTargetKind`, and `org.springframework.test.util.ReflectionTestUtils`, `com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Write the response DTOs**

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminSubmissionDetailResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.time.OffsetDateTime;

/** 검수 상세에 담기는 제출 시도 1건(현재 제출 또는 이력 항목 공용). */
public record AdminSubmissionDetailResDto(
    String submissionId,
    int attemptNo,
    String targetId,
    String placeName,
    Double targetLatitude,
    Double targetLongitude,
    Integer radiusM,
    String guideTextSnapshot,
    String mediaUrl,
    Double gpsLat,
    Double gpsLng,
    OffsetDateTime capturedAt,
    OffsetDateTime submittedAt,
    String reviewStatus,
    String rejectionReason,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    long revision) {

  public static AdminSubmissionDetailResDto of(ZoneEventSubmission s, String mediaUrl) {
    return new AdminSubmissionDetailResDto(
        s.getId().toString(),
        s.getAttemptNo(),
        s.getTarget().getId().toString(),
        s.getPlaceName(),
        s.getTargetLatitude(),
        s.getTargetLongitude(),
        s.getRadiusM(),
        s.getGuideTextSnapshot(),
        mediaUrl,
        s.getGpsLat(),
        s.getGpsLng(),
        s.getCapturedAt(),
        s.getSubmittedAt(),
        s.getReviewStatus().name(),
        s.getRejectionReason(),
        s.getReviewedBy() == null ? null : s.getReviewedBy().toString(),
        s.getReviewedAt(),
        s.getRevision());
  }
}
```

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDetailResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;
import java.util.List;

/** 검수 상세: 사용자 표시 정보 + 현재 제출 + 전체 제출 이력 + 당시(제출 시점) 타겟/보상 스냅샷. */
public record AdminReviewDetailResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String userNickname,
    String userEmail,
    String status,
    RewardSnapshot rewardSnapshot,
    AdminSubmissionDetailResDto currentSubmission,
    List<AdminSubmissionDetailResDto> submissionHistory,
    OffsetDateTime joinedAt) {

  public static AdminReviewDetailResDto of(
      ZoneEventParticipation p,
      String userNickname,
      String userEmail,
      AdminSubmissionDetailResDto currentSubmission,
      List<AdminSubmissionDetailResDto> submissionHistory) {
    return new AdminReviewDetailResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        userNickname,
        userEmail,
        p.getStatus().name(),
        p.getEvent().getBaseReward(),
        currentSubmission,
        submissionHistory,
        p.getJoinedAt());
  }
}
```

- [ ] **Step 4: Implement `detail(...)`**

Add to `AdminZoneEventReviewService.java` — new imports (`AdminReviewDetailResDto`, `AdminSubmissionDetailResDto`, `ZoneEventSubmission`, `ZoneEventSubmissionRepository`, `User`, `UserRepository`, `FileStorageService`, `ResourceNotFoundException`, `Comparator`); new constructor fields:

```java
  private final ZoneEventSubmissionRepository submissionRepository;
  private final UserRepository userRepository;
  private final FileStorageService fileStorageService;
```

```java
  /** 검수 상세: 사용자 표시 정보 + 현재 제출 + 전체 제출 이력(attemptNo 내림차순) + 당시 타겟/보상 스냅샷. */
  @Transactional(readOnly = true)
  public AdminReviewDetailResDto detail(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    User participant =
        userRepository
            .findById(participation.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found"));

    List<ZoneEventSubmission> history =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(participationId);
    List<AdminSubmissionDetailResDto> historyDtos =
        history.stream()
            .map(s -> AdminSubmissionDetailResDto.of(s, presignedUrl(s.getMediaFileKey())))
            .toList();
    AdminSubmissionDetailResDto current =
        historyDtos.stream()
            .filter(
                s ->
                    participation.getCurrentSubmissionId() != null
                        && s.submissionId().equals(participation.getCurrentSubmissionId().toString()))
            .findFirst()
            .orElse(historyDtos.isEmpty() ? null : historyDtos.get(0));

    return AdminReviewDetailResDto.of(
        participation, participant.getNickname(), participant.getEmail(), current, historyDtos);
  }

  private String presignedUrl(String mediaFileKey) {
    return mediaFileKey == null ? null : fileStorageService.getPresignedUrl(mediaFileKey);
  }
```

Check `error.user.not_found` doesn't already exist under a different key (`grep -n "user.not_found" src/main/resources/messages.properties`) — if a differently-named existing key already covers "user not found" for this codebase, reuse it instead of adding a new one; otherwise add `error.user.not_found=사용자를 찾을 수 없습니다.` (+ en/ja/zh) to the four message files.

- [ ] **Step 5: Add the controller endpoint**

Add to `AdminZoneEventReviewController.java`:

```java
  @GetMapping("/{participationId}")
  public ResponseEntity<ApiResponse<AdminReviewDetailResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    return ResponseEntity.ok(
        ApiResponse.success("검수 상세 조회", adminZoneEventReviewService.detail(user, participationId)));
  }
```

(add `PathVariable` import, and `AdminReviewDetailResDto` import)

- [ ] **Step 6: Add a controller test case** (append to `AdminZoneEventReviewControllerTest.java`)

```java
  @Test
  @DisplayName("검수 상세 200")
  void detail() throws Exception {
    UUID pid = UUID.randomUUID();
    when(adminZoneEventReviewService.detail(any(), eq(pid)))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto(
                pid.toString(), null, null, "SUYEONG_NAMGU", UUID.randomUUID().toString(), "닉", "e@x.com",
                "UNDER_REVIEW", null, null, List.of(), java.time.OffsetDateTime.now()));
    mockMvc.perform(get("/admin/zone-event-reviews/{id}", pid)).andExpect(status().isOk());
  }
```

(add `import static org.mockito.ArgumentMatchers.eq;`)

- [ ] **Step 7: Run tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest" --tests "*AdminZoneEventReviewControllerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(zoneevent): add GET /admin/zone-event-reviews/{id} detail"
```

---

## Task 7: Approve (`POST /admin/zone-event-reviews/{participationId}/approve`)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewApproveReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDecisionResDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java` (add `approve`)
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java` (add endpoint)
- Test: append to `AdminZoneEventReviewServiceTest.java`, `AdminZoneEventReviewControllerTest.java`

**Interfaces:**
- Produces: `AdminZoneEventReviewService.approve(AuthenticatedUser user, UUID participationId, ReviewApproveReqDto request, String idempotencyKey) -> AdminReviewDecisionResDto`. New constructor deps: `ZoneTitleService`, `IdempotencyService`.
- Consumes: `ZoneTitleService.awardTitles(UUID, String, boolean)` from Task 1, `IdempotencyService.findReplay`/`save` from Task 2.

- [ ] **Step 1: Write the failing tests** (append to `AdminZoneEventReviewServiceTest.java`)

```java
  @Test
  @DisplayName("승인하면 SUCCESS·앨범 공개만 되고(보상 없음) 현재 제출도 SUCCESS가 된다")
  void approveMarksSuccessWithoutReward() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    AdminReviewDecisionResDto result =
        reviewService.approve(
            operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision()), null);

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.SUCCESS);
  }

  @Test
  @DisplayName("만료된 expectedRevision으로 승인하면 409다")
  void approveStaleRevisionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision() + 1), null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("참여의 currentSubmissionId가 아닌(재제출로 밀려난) submissionId로 승인하면 409다")
  void approveStaleSubmissionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventAuthTarget target2 = authTargetRepository.save(
        ZoneEventAuthTarget.builder().event(event).targetKind(ZoneEventTargetKind.PLACE)
            .placeName("장소2").latitude(35.2).longitude(129.2).radiusM(100).build());
    ZoneEventSubmission stale = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    ZoneEventSubmission current = submissionRepository.save(
        ZoneEventSubmission.builder().participation(p).attemptNo(2).target(target2)
            .placeName(target2.getPlaceName()).targetLatitude(target2.getLatitude())
            .targetLongitude(target2.getLongitude()).radiusM(target2.getRadiusM())
            .mediaFileKey("uploads/images/photo2.jpg").gpsLat(35.2).gpsLng(129.2)
            .capturedAt(OffsetDateTime.now()).build());
    p.linkSubmission(current.getId());
    participationRepository.save(p);

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator, p.getId(), new ReviewApproveReqDto(stale.getId(), stale.getRevision()), null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 재전송하면 처리를 다시 하지 않고 이전 결과를 그대로 돌려준다")
  void approveIsIdempotent() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-" + UUID.randomUUID();

    AdminReviewDecisionResDto first =
        reviewService.approve(
            operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision()), key);
    AdminReviewDecisionResDto replay =
        reviewService.approve(
            operator, p.getId(), new ReviewApproveReqDto(submission.getId(), submission.getRevision()), key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("동시에 두 번 승인 요청이 오면 하나만 성공하고 나머지는 409다(비관적 재현: 두 트랜잭션이 같은 revision을 읽은 상태 시뮬레이션)")
  void concurrentApproveOnlyOneWins() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    Long revisionSeenByBoth = submission.getRevision();

    reviewService.approve(operator, p.getId(), new ReviewApproveReqDto(submission.getId(), revisionSeenByBoth), null);

    // 두 번째 "동시" 요청은 같은 revision을 들고 왔지만 첫 요청이 이미 revision을 올렸으므로 매뉴얼 체크에서 막힌다.
    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator, p.getId(), new ReviewApproveReqDto(submission.getId(), revisionSeenByBoth), null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  private ZoneEventParticipation underReviewWithSubmission() {
    User participant = savedUser("참가자");
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(participant.getId())
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder().event(event).targetKind(ZoneEventTargetKind.PLACE)
                .placeName("장소").latitude(35.1).longitude(129.1).radiusM(100).build());
    ZoneEventSubmission submission =
        submissionRepository.save(
            ZoneEventSubmission.builder().participation(p).attemptNo(1).target(target)
                .placeName(target.getPlaceName()).targetLatitude(target.getLatitude())
                .targetLongitude(target.getLongitude()).radiusM(target.getRadiusM())
                .mediaFileKey("uploads/images/photo.jpg").gpsLat(35.1).gpsLng(129.1)
                .capturedAt(OffsetDateTime.now()).build());
    ReflectionTestUtils.setField(p, "currentSubmissionId", submission.getId());
    return participationRepository.save(p);
  }
```

Note on `concurrentApproveOnlyOneWins`: this test proves the **manual expectedRevision check** (fast-path). It does not exercise a true simultaneous-transaction race — that would need two real overlapping `@Transactional` contexts, which JUnit can't easily produce against `@Transactional` test rollback semantics. The manual check plus the `saveAndFlush`/`ObjectOptimisticLockingFailureException` backstop together satisfy the requirement; the backstop path is defense-in-depth for the true-race window between the manual check and the flush, and is acceptable to leave uncovered by a dedicated race test as long as the surrounding lines are covered by the tests above (the `try`/`catch` block itself executes its try-branch on every approve call, so line coverage is satisfied without needing to force the catch branch — **verify this claim against actual jacoco output in Task 10**; if the catch line shows as uncovered, add a unit-level test that calls `AdminZoneEventReviewService.approve` twice from two independent `TransactionTemplate.execute(...)` blocks in the same test method, forcing one to see a stale in-memory entity via `entityManager.clear()` between the two `execute` calls).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Write the request/response DTOs**

`src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewApproveReqDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 검수 승인 요청. 재제출로 밀려난 이전 submissionId나 오래된 expectedRevision이면 409다. */
public record ReviewApproveReqDto(@NotNull UUID submissionId, @NotNull Long expectedRevision) {}
```

`src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminReviewDecisionResDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import java.util.List;

/** 검수 승인/반려 처리 결과. 승인은 SUCCESS·앨범 공개만 하며 보상은 지급하지 않는다(별도 확정 단계, 이슈 #244). */
public record AdminReviewDecisionResDto(
    String participationId,
    String status,
    String submissionId,
    int attemptNo,
    String reviewStatus,
    List<EquippedTitleResDto> newlyAwardedTitles) {

  public static AdminReviewDecisionResDto of(
      ZoneEventParticipation p, ZoneEventSubmission s, List<EquippedTitleResDto> titles) {
    return new AdminReviewDecisionResDto(
        p.getId().toString(), p.getStatus().name(), s.getId().toString(), s.getAttemptNo(),
        s.getReviewStatus().name(), titles);
  }
}
```

- [ ] **Step 4: Implement `approve(...)`**

Add to `AdminZoneEventReviewService.java` — new imports (`ReviewApproveReqDto`, `AdminReviewDecisionResDto`, `ZoneTitleService`, `IdempotencyService`, `ConflictException`, `ObjectOptimisticLockingFailureException`, `ObjectMapper`, `ArrayList` if not already, `EquippedTitleResDto`); new constructor fields:

```java
  private final ZoneTitleService zoneTitleService;
  private final IdempotencyService idempotencyService;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
```

```java
  private static final String APPROVE_ENDPOINT = "zone-event-review-approve";

  /** 제출 단위 승인: SUCCESS·앨범 공개만 하고 보상은 지급하지 않는다. 칭호 누적 집계는 트리거하되 자동 장착은 하지 않는다. */
  @Transactional
  public AdminReviewDecisionResDto approve(
      AuthenticatedUser user, UUID participationId, ReviewApproveReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = participationId + ":" + request.submissionId() + ":" + request.expectedRevision();
    java.util.Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, APPROVE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminReviewDecisionResDto.class);
    }

    ZoneEventParticipation participation = requireUnderReview(participationId);
    ZoneEventSubmission submission = requireCurrentSubmission(participation, request.submissionId());
    if (!submission.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }

    participation.stampReview(user.id());
    participation.markSuccess();
    submission.approve(user.id());
    flushSubmission(submission);

    List<EquippedTitleResDto> titles =
        new ArrayList<>(zoneTitleService.awardTitles(participation.getUserId(), participation.getEvent().getZoneId(), false));
    AdminReviewDecisionResDto result = AdminReviewDecisionResDto.of(participation, submission, titles);
    idempotencyService.save(idempotencyKey, APPROVE_ENDPOINT, fingerprint, result);
    return result;
  }

  private ZoneEventParticipation requireUnderReview(UUID participationId) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (participation.getStatus() != ParticipationStatus.UNDER_REVIEW) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }

  private ZoneEventSubmission requireCurrentSubmission(ZoneEventParticipation participation, UUID submissionId) {
    ZoneEventSubmission submission =
        submissionRepository
            .findById(submissionId)
            .filter(s -> s.getParticipation().getId().equals(participation.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.submission.not_found"));
    if (!submission.getId().equals(participation.getCurrentSubmissionId())) {
      throw new ConflictException("error.zone_event.review.stale_submission");
    }
    return submission;
  }

  private void flushSubmission(ZoneEventSubmission submission) {
    try {
      submissionRepository.saveAndFlush(submission);
    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize idempotent response.", e);
    }
  }
```

Add message keys to the four `messages*.properties` files:
- `error.zone_event.submission.not_found` — ko: `제출 이력을 찾을 수 없습니다.`, en: `Submission not found.`, ja: `提出履歴が見つかりません。`, zh: `未找到提交记录。`
- `error.zone_event.review.stale_submission` — ko: `참여자가 재제출하여 더 이상 유효하지 않은 제출입니다.`, en: `This submission was superseded by a resubmission.`, ja: `再提出により無効になった提出です。`, zh: `该提交已被重新提交取代。`
- `error.zone_event.review.stale_revision` — ko: `다른 처리로 인해 검수 정보가 변경되었습니다. 다시 시도해 주세요.`, en: `The review record changed due to another operation. Please retry.`, ja: `他の処理により審査情報が変更されました。再試行してください。`, zh: `审核信息因其他操作已发生变化，请重试。`

- [ ] **Step 5: Add the controller endpoint**

Add to `AdminZoneEventReviewController.java` (new imports: `ReviewApproveReqDto`, `AdminReviewDecisionResDto`, `PostMapping`, `RequestBody`, `RequestHeader`, `jakarta.validation.Valid`):

```java
  @PostMapping("/{participationId}/approve")
  public ResponseEntity<ApiResponse<AdminReviewDecisionResDto>> approve(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReviewApproveReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "검수 승인",
            adminZoneEventReviewService.approve(user, participationId, request, idempotencyKey)));
  }
```

- [ ] **Step 6: Add a controller test case** (append to `AdminZoneEventReviewControllerTest.java`)

```java
  @Test
  @DisplayName("승인 200")
  void approve() throws Exception {
    UUID pid = UUID.randomUUID();
    when(adminZoneEventReviewService.approve(any(), eq(pid), any(), any()))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.AdminReviewDecisionResDto(
                pid.toString(), "SUCCESS", UUID.randomUUID().toString(), 1, "SUCCESS", List.of()));
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/approve", pid)
                .contentType("application/json")
                .content(
                    "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCESS"));
  }

  @Test
  @DisplayName("승인 요청에 submissionId·expectedRevision이 없으면 400")
  void approveValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/approve", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
```

(add `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;` and `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;`)

- [ ] **Step 7: Run tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest" --tests "*AdminZoneEventReviewControllerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(zoneevent): add submission-scoped approve with expectedRevision + Idempotency-Key"
```

---

## Task 8: Reject (`POST /admin/zone-event-reviews/{participationId}/reject`)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewRejectReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventReviewService.java` (add `reject`)
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventReviewController.java` (add endpoint)
- Test: append to both test files

**Interfaces:**
- Produces: `AdminZoneEventReviewService.reject(AuthenticatedUser user, UUID participationId, ReviewRejectReqDto request, String idempotencyKey) -> void`. Reuses `requireUnderReview`/`requireCurrentSubmission`/`flushSubmission` from Task 7 — no new constructor deps.

- [ ] **Step 1: Write the failing tests** (append to `AdminZoneEventReviewServiceTest.java`)

```java
  @Test
  @DisplayName("반려하면 FAIL이 되고 사유가 남으며 제출도 REJECTED가 된다(보상 없음, 재제출 가능)")
  void rejectMarksFail() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    reviewService.reject(
        operator, p.getId(), new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", submission.getRevision()), null);

    ZoneEventParticipation after = participationRepository.findById(p.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(ParticipationStatus.FAIL);
    assertThat(after.getFailReason()).isEqualTo("NOT_ON_SITE");
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.REJECTED);
  }

  @Test
  @DisplayName("만료된 expectedRevision으로 반려하면 409다")
  void rejectStaleRevisionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    assertThatThrownBy(
            () ->
                reviewService.reject(
                    operator, p.getId(),
                    new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", submission.getRevision() + 1), null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 반려를 재전송하면 두 번째 요청은 다시 처리하지 않는다(제출 상태가 한 번만 바뀐다)")
  void rejectIsIdempotent() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission = submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-" + UUID.randomUUID();

    reviewService.reject(
        operator, p.getId(), new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", submission.getRevision()), key);
    reviewService.reject(
        operator, p.getId(), new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", submission.getRevision()), key);

    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getRevision())
        .isEqualTo(submission.getRevision() + 1); // 딱 한 번만 처리됨
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Write the request DTO**

`src/main/java/com/butingbe/domain/zoneevent/dto/request/ReviewRejectReqDto.java`:

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 검수 반려 요청. */
public record ReviewRejectReqDto(
    @NotNull UUID submissionId, @NotBlank String reason, @NotNull Long expectedRevision) {}
```

- [ ] **Step 4: Implement `reject(...)`**

Add to `AdminZoneEventReviewService.java`:

```java
  private static final String REJECT_ENDPOINT = "zone-event-review-reject";

  /** 제출 단위 반려: 같은 참여 건은 재제출로 재시도할 수 있다. */
  @Transactional
  public void reject(
      AuthenticatedUser user, UUID participationId, ReviewRejectReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = participationId + ":" + request.submissionId() + ":" + request.expectedRevision();
    if (idempotencyService.findReplay(idempotencyKey, REJECT_ENDPOINT, fingerprint).isPresent()) {
      return;
    }

    ZoneEventParticipation participation = requireUnderReview(participationId);
    ZoneEventSubmission submission = requireCurrentSubmission(participation, request.submissionId());
    if (!submission.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }

    participation.stampReview(user.id());
    participation.markFail(request.reason());
    submission.reject(user.id(), request.reason());
    flushSubmission(submission);

    idempotencyService.save(idempotencyKey, REJECT_ENDPOINT, fingerprint, null);
  }
```

- [ ] **Step 5: Add the controller endpoint**

Add to `AdminZoneEventReviewController.java` (new imports: `ReviewRejectReqDto`):

```java
  @PostMapping("/{participationId}/reject")
  public ResponseEntity<ApiResponse<Void>> reject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReviewRejectReqDto request) {
    adminZoneEventReviewService.reject(user, participationId, request, idempotencyKey);
    return ResponseEntity.ok(ApiResponse.success("검수 반려", null));
  }
```

- [ ] **Step 6: Add controller test cases** (append to `AdminZoneEventReviewControllerTest.java`)

```java
  @Test
  @DisplayName("반려 200 / 사유 없으면 400")
  void reject() throws Exception {
    UUID pid = UUID.randomUUID();
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/reject", pid)
                .contentType("application/json")
                .content(
                    "{\"submissionId\":\""
                        + UUID.randomUUID()
                        + "\",\"reason\":\"NOT_ON_SITE\",\"expectedRevision\":0}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/reject", pid)
                .contentType("application/json")
                .content("{\"submissionId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":0}"))
        .andExpect(status().isBadRequest());
  }
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --no-daemon -g "C:\gradle-home" --tests "*AdminZoneEventReviewServiceTest" --tests "*AdminZoneEventReviewControllerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(zoneevent): add submission-scoped reject with expectedRevision + Idempotency-Key"
```

---

## Task 9: OpenAPI documentation

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml`

- [ ] **Step 1: Remove the stale legacy paths and their now-inapplicable pieces**

Delete the `GET /api/v1/admin/zone-event-participations/review-queue`, `POST /api/v1/admin/zone-event-participations/{participationId}/approve`, and `POST /api/v1/admin/zone-event-participations/{participationId}/reject` path entries (currently at lines ~5166-5287 — re-locate by searching for these exact path strings, since earlier tasks in this plan don't touch this file and line numbers are stable until this task runs). Keep `revoke`/`unhide`. Also remove `ReviewQueuePageEnvelope`/`ReviewQueueItemResDto`-shaped schemas from `components.schemas` if nothing else references them (`grep -n "ReviewQueuePage\|ReviewQueueItem" src/main/resources/static/docs/openapi3.yaml` after deleting the paths — confirm zero remaining refs before deleting the schema blocks) — but **keep** `SubmitResultEnvelope`/`SubmitResultResDto`-shaped schemas, since `ZoneEventSubmitService.submit(...)`'s auto-approve response still uses that shape (`GET/POST` submit endpoint documented elsewhere in this file).

- [ ] **Step 2: Add `GET /api/v1/admin/zone-event-participations`**

Follow the existing `GET /api/v1/users/me/zone-event-participations` (history) path as the closest template for filter-parameter style, but adapt to page/size (not cursor) and the admin auth pattern from the deleted `review-queue` entry (`tags: [Zone Event]`, `security: [opaqueToken: []]`, `401`/`403` via `ApiErrorResponse`). Add query params `roundId` (uuid), `eventId` (uuid), `zoneId` (string, enum of `ChatZone` values — copy the enum list from an existing `zone` param elsewhere in this file, e.g. the history endpoint's `zone` param), `userId` (uuid), `status` (string, enum of `ParticipationStatus` values: `JOINED, SUBMITTED, UNDER_REVIEW, SUCCESS, FAIL, CANCELLED, REVOKED`), `keyword` (string), `page` (integer, default 1), `size` (integer, default 20, max 50). Response `200 → AdminParticipationPageEnvelope` wrapping `AdminParticipationPage` (`items: AdminParticipationListItem[]`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`) matching the field names in `AdminParticipationPageResDto`/`AdminParticipationListItemResDto` exactly (Task 4). Define both schemas under `components.schemas`, and the `<Name>`+`<Name>Envelope` pairing per this repo's convention.

- [ ] **Step 3: Add the `/admin/zone-event-reviews` paths**

- `GET /api/v1/admin/zone-event-reviews` — query params `roundId`, `eventId`, `zoneId`, `page`, `size` (same style as above, no `status`/`keyword`/`userId` — this queue is hardcoded to `UNDER_REVIEW`, note that in the `description`). Response `200 → AdminReviewQueuePageEnvelope` wrapping `AdminReviewQueuePage` (fields matching `AdminReviewQueuePageResDto`/`AdminReviewQueueItemResDto` from Task 5).
- `GET /api/v1/admin/zone-event-reviews/{participationId}` — path param `participationId` (uuid). Response `200 → AdminReviewDetailEnvelope` wrapping `AdminReviewDetail` (fields matching `AdminReviewDetailResDto`/`AdminSubmissionDetailResDto` from Task 6 — define `AdminSubmissionDetail` as a nested schema reused for both `currentSubmission` and `submissionHistory[]`), `404` (`ApiErrorResponse`).
- `POST /api/v1/admin/zone-event-reviews/{participationId}/approve` — path param `participationId`; header param `Idempotency-Key` (string, optional, `in: header`); `requestBody → ReviewApproveRequest` (`submissionId: uuid`, `expectedRevision: integer(int64)`, both required, matching `ReviewApproveReqDto` from Task 7); response `200 → AdminReviewDecisionEnvelope` wrapping `AdminReviewDecision` (fields matching `AdminReviewDecisionResDto`, `newlyAwardedTitles` as an array of the existing equipped-title schema — find it via `grep -n "EquippedTitle" src/main/resources/static/docs/openapi3.yaml` and reuse that `$ref`); `403`/`404`/`409` (`ApiErrorResponse`).
- `POST /api/v1/admin/zone-event-reviews/{participationId}/reject` — same path/header params; `requestBody → ReviewRejectRequest` (`submissionId: uuid`, `reason: string` required, `expectedRevision: integer(int64)` required, matching `ReviewRejectReqDto` from Task 8); response `200` with no data (`ApiResponse` envelope with `data: null`, matching the style of the deleted legacy reject's `200` response before this task removed it); `400`/`403`/`404`/`409`.

Use `ROLE_ADMIN/MANAGER.` as the leading `summary` phrase for every one of these five new/changed paths, matching [[openapi-docs-convention]].

- [ ] **Step 4: Validate the YAML parses**

```bash
mkdir -p /tmp/yamlcheck && cd /tmp/yamlcheck && npm install js-yaml --no-save
node -e "const y=require('js-yaml').load(require('fs').readFileSync('C:/Users/조준연/Desktop/bu-ting-backend/src/main/resources/static/docs/openapi3.yaml','utf8')); console.log('paths:', Object.keys(y.paths).filter(p=>p.includes('zone-event-review')||p.includes('zone-event-participations')));"
```

Expected: prints the five/four expected paths, no exception.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(zoneevent): openapi3.yaml에 참여 목록·검수 큐/상세/승인/반려 문서화, 구 검수 경로 제거"
```

---

## Task 10: Full verification

- [ ] **Step 1: Sync/create the ASCII-path clone** (per [[gradle-test-korean-path-fix]])

```bash
git clone --branch feature/241-zone-event-review-api "C:/Users/조준연/Desktop/bu-ting-backend" /c/dev/bu-ting-backend-241 2>/dev/null || (cd /c/dev/bu-ting-backend-241 && git fetch origin feature/241-zone-event-review-api && git reset --hard origin/feature/241-zone-event-review-api)
```

- [ ] **Step 2: Run full `check`**

```bash
cd /c/dev/bu-ting-backend-241 && ./gradlew check --no-daemon -g "C:\gradle-home"
```

- [ ] **Step 3: Fix any Spotless/coverage failures**

If Spotless fails: `./gradlew spotlessApply --no-daemon -g "C:\gradle-home"`, re-stage, re-commit.
If Jacoco coverage fails: open `build/reports/jacoco/test/html/com.butingbe.domain.zoneevent.service/<Class>.java.html` (and the `.controller`/`.dto`/`.entity` equivalents) for each new class, grep the HTML for `class="nc"` (not-covered) or `class="pc"` (partially covered) to find the exact uncovered line/branch, and add a test hitting it — likely candidates given this plan's design: the `ObjectOptimisticLockingFailureException` catch branch in `flushSubmission` (see the note at the end of Task 7 Step 1 for how to force it), the `keywordUserIds.isEmpty()` early-return branch in `AdminReviewService.list`, and the `historyDtos.isEmpty()` fallback branch in `detail`.

- [ ] **Step 4: Re-run `check` until green**

```bash
cd /c/dev/bu-ting-backend-241 && ./gradlew check --no-daemon -g "C:\gradle-home"
```

Expected: `BUILD SUCCESSFUL`, 100% line coverage, Spotless clean.

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin feature/241-zone-event-review-api
gh pr create --base dev --title "feat(zoneevent): 전체 참여·사진 인증 검수 API (제출 단위 승인/반려)" --body "$(cat <<'EOF'
## Summary
- GET /admin/zone-event-participations: 전체 참여 목록 (roundId/eventId/zoneId/userId/status/keyword/page/size)
- GET /admin/zone-event-reviews: 검수 큐(UNDER_REVIEW, roundId/eventId/zoneId/page/size)
- GET /admin/zone-event-reviews/{participationId}: 상세(사용자 표시 정보/현재 제출/전체 제출 이력/타겟·보상 스냅샷)
- POST /admin/zone-event-reviews/{participationId}/approve|reject: submissionId+expectedRevision 기반 제출 단위 처리, Idempotency-Key 재전송 지원
- 승인은 SUCCESS·앨범 공개만 하고 보상은 지급하지 않는다(칭호 누적 집계는 트리거하되 자동 장착은 하지 않음)
- 구 /admin/zone-event-participations/review-queue, approve, reject(#215, 즉시 보상 지급)는 새 정책과 상충하여 제거

## Test plan
- [x] `./gradlew check` green on the ASCII-path clone (100% coverage, Spotless clean)
- [x] openapi3.yaml validated to parse and cover all new/changed paths

Closes #241

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: Note the auto-merge behavior**

Per [[pr-automerge-workflow]], this PR may auto-merge into `dev` once CI's `check` job goes green, unattended. Before any follow-up push to this branch, re-check `gh api repos/B-TING/bu-ting-backend/pulls/<n> -q "{state,merged,head_sha:.head.sha}"` first.

---

## Self-Review Notes (for whoever executes this plan)

- **Spec coverage:** all 8 checklist items from issue #241 are covered — list (Task 4), queue (Task 5), detail (Task 6), approve (Task 7), reject (Task 8), concurrency (Tasks 7-8), operator/reviewer identity from `AuthenticatedUser` server-side (Tasks 7-8), title trigger without auto-equip (Task 1 + Task 7), Idempotency-Key (Task 2 + Tasks 7-8).
- **Reward snapshot design decision:** the issue asks for "당시 ... 보상 스냅샷" in the detail response. This plan exposes `ZoneEvent.baseReward` directly rather than adding a new snapshot column to `zone_event_submission`, because `ZoneEvent.baseReward` is already documented as fixed at event-creation time and only editable while `SCHEDULED` (before any participation/submission can exist) — see `ZoneEvent.java`'s class javadoc and `applyScheduledOnly`. If this assumption turns out wrong in review (e.g. product wants the reward frozen per-submission even across an edited-while-SCHEDULED-then-reactivated event), add a `base_reward_snapshot JSONB` column to `zone_event_submission` in Task 6 instead of reading `event.getBaseReward()`.
- **Type consistency check:** `AdminReviewDecisionResDto`, `AdminReviewDetailResDto`, `AdminSubmissionDetailResDto`, `AdminReviewQueueItemResDto`/`PageResDto`, `AdminParticipationListItemResDto`/`PageResDto` field names are used consistently across their `of(...)` factory (Tasks 4-8) and their OpenAPI schema (Task 9) — double check field-for-field when writing Task 9, since that's the one place this plan describes the schema in prose rather than exact code.
