# Admin Auth Target API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a dedicated admin API for zone-event auth targets (list/create/patch/replace/cancel) so operators can edit latitude, longitude, and radius independently (issue #239 — GitHub issue `B-TING/bu-ting-backend#239`).

**Architecture:** New `AdminZoneEventTargetController` + `AdminZoneEventTargetService` under `com.butingbe.domain.zoneevent`, reusing the existing `ZoneEventAuthTarget` entity (already has `sourceLatitude`/`sourceLongitude` vs `latitude`/`longitude` columns — no migration needed). Place-master lookups go through a new read-only `PlaceService.getPlaceSummary(contentId)` method backed by the Tour API's `detailCommon2` endpoint (title + mapx/mapy), never writing to place data. History of prior settings is recorded via the existing `ZoneEventAuditLog` free-form `detail` JSON column (`before`/`after` snapshots) — no new table.

**Tech Stack:** Spring Boot, Spring Data JPA, Bean Validation, JUnit 5 + AssertJ + Mockito, Testcontainers Postgres (`AbstractContainerTest`), MockMvc standalone setup for controller tests.

## Global Constraints

- radiusM range: 30–500 (matches existing `AuthTargetReqDto` policy cap).
- latitude range: -90.0–90.0; longitude range: -180.0–180.0 (matches `PlaceLocationSearchReqDto` convention).
- Every new/changed error path must reuse or add a `error.zone_event.*` key in all four locale files: `messages.properties`, `messages_en.properties`, `messages_ja.properties`, `messages_zh.properties`.
- Every new controller endpoint must get a Korean-language entry in `src/main/resources/static/docs/openapi3.yaml`, matching sibling `Zone Event` tag admin entries (`security: - opaqueToken: []`, `ApiErrorResponse` refs).
- Never write to place-master data — `PlaceService.getPlaceSummary` is read-only against the Tour API.
- Run `./gradlew test` via the ASCII-path clone workaround before declaring done (Korean user-profile path breaks the Gradle worker JVM otherwise).

---

### Task 1: `PlaceService.getPlaceSummary` (read-only place lookup for snapshotting)

**Files:**
- Create: `src/main/java/com/butingbe/domain/place/dto/response/PlaceSummaryResDto.java`
- Modify: `src/main/java/com/butingbe/domain/place/service/PlaceService.java`
- Modify: `src/main/java/com/butingbe/domain/place/service/TourApiPlaceService.java`
- Test: `src/test/java/com/butingbe/domain/place/service/TourApiPlaceServiceTest.java`

**Interfaces:**
- Produces: `PlaceSummaryResDto(String contentId, String title, Double latitude, Double longitude)` — a record with a `latitude`/`longitude` pair parsed from Tour API's `mapy`/`mapx` (note the swap: `mapx` = longitude, `mapy` = latitude, matching `TourApiPlaceService.locationBias`'s existing parsing at line ~495).
- Produces: `PlaceService.getPlaceSummary(String contentId)` returns `null` when the Tour API has no matching item (caller decides how to surface that — do not throw from this method).

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/com/butingbe/domain/place/service/TourApiPlaceServiceTest.java` (same file/class, new `@Test` methods):

```java
  @Test
  @DisplayName("detailCommon2로 제목·원본 좌표 요약을 가져온다")
  void getPlaceSummaryReturnsTitleAndCoordinates() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(builder.build(), "https://tour.example.com", "SERVICE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/detailCommon2"
                    + "?MobileOS=WEB"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&contentId=126081"
                    + "&defaultYN=Y"
                    + "&addrinfoYN=Y"
                    + "&mapinfoYN=Y"
                    + "&serviceKey=SERVICE_KEY"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": {
                        "item": [
                          {
                            "contentid": "126081",
                            "title": "광안대교",
                            "addr1": "부산광역시 수영구",
                            "mapx": "129.1181",
                            "mapy": "35.1532"
                          }
                        ]
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceSummaryResDto summary = placeService.getPlaceSummary("126081");

    assertThat(summary.contentId()).isEqualTo("126081");
    assertThat(summary.title()).isEqualTo("광안대교");
    assertThat(summary.latitude()).isEqualTo(35.1532);
    assertThat(summary.longitude()).isEqualTo(129.1181);
  }

  @Test
  @DisplayName("존재하지 않는 contentId는 null을 반환한다")
  void getPlaceSummaryReturnsNullWhenNotFound() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(builder.build(), "https://tour.example.com", "SERVICE_KEY");

    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/detailCommon2")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": { "items": "" }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThat(placeService.getPlaceSummary("GHOST")).isNull();
  }
```

Add the import `com.butingbe.domain.place.dto.response.PlaceSummaryResDto` to the test file's import block.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew compileTestJava` (it will fail to compile — `PlaceSummaryResDto` and `getPlaceSummary` don't exist yet).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/butingbe/domain/place/dto/response/PlaceSummaryResDto.java`:

```java
package com.butingbe.domain.place.dto.response;

/** 관광지 마스터 데이터 읽기 전용 요약(제목 + 원본 좌표). 인증 타겟 스냅샷 용도. */
public record PlaceSummaryResDto(String contentId, String title, Double latitude, Double longitude) {}
```

In `PlaceService.java`, add to the interface:

```java
  /** 관광지 마스터 데이터를 읽기 전용으로 조회한다(제목 + 원본 좌표). 없으면 null. */
  PlaceSummaryResDto getPlaceSummary(String contentId);
```

(add `import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;` — the file already does `import com.butingbe.domain.place.dto.response.PlaceDetailResDto;` so add alongside it.)

In `TourApiPlaceService.java`, add after `getPlaceDetail`:

```java
  @Override
  public PlaceSummaryResDto getPlaceSummary(String contentId) {
    if (!StringUtils.hasText(serviceKey)) {
      throw new IllegalStateException("Tour API service key is not configured.");
    }
    if (!StringUtils.hasText(contentId)) {
      throw new IllegalArgumentException("contentId is required.");
    }
    Optional<TourCommonItem> item = tourCommonInfo(contentId);
    if (item.isEmpty()) {
      return null;
    }
    TourCommonItem common = item.get();
    return new PlaceSummaryResDto(
        common.contentid(), common.title(), parseDouble(common.mapy()), parseDouble(common.mapx()));
  }
```

Add the import `com.butingbe.domain.place.dto.response.PlaceSummaryResDto` (the wildcard `import com.butingbe.domain.place.dto.response.*;` at the top of `TourApiPlaceService.java` already covers this — no import change needed there).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.butingbe.domain.place.service.TourApiPlaceServiceTest" --no-daemon -g "C:\\gradle-home"` (from the ASCII-path clone — see Global Constraints).
Expected: PASS, including all pre-existing tests in that file.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/place/dto/response/PlaceSummaryResDto.java src/main/java/com/butingbe/domain/place/service/PlaceService.java src/main/java/com/butingbe/domain/place/service/TourApiPlaceService.java src/test/java/com/butingbe/domain/place/service/TourApiPlaceServiceTest.java
git commit -m "feat(place): add read-only place summary lookup for admin target snapshots"
```

---

### Task 2: `ZoneEventAuthTarget` entity — guard `cancel()`, add `markReplaced()`

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/entity/ZoneEventAuthTarget.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/entity/ZoneEventAuthTargetTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ZoneEventAuthTarget.cancel()` now throws `ConflictException("error.zone_event.invalid_state")` if `status != ACTIVE` (previously unconditional — safe, no existing caller in main code relies on cancelling a non-ACTIVE target). `ZoneEventAuthTarget.markReplaced()` — new method, same guard, sets `status = REPLACED`.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/com/butingbe/domain/zoneevent/entity/ZoneEventAuthTargetTest.java`:

```java
  @Test
  @DisplayName("ACTIVE가 아닌 타겟은 취소할 수 없다")
  void cancelNonActiveTargetConflicts() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();
    target.cancel();

    assertThatThrownBy(target::cancel)
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("긴급 교체되면 REPLACED 상태가 된다")
  void markReplacedSetsReplacedStatus() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();

    target.markReplaced();

    assertThat(target.getStatus()).isEqualTo(ZoneEventTargetStatus.REPLACED);
  }

  @Test
  @DisplayName("ACTIVE가 아닌 타겟은 교체 표시할 수 없다")
  void markReplacedNonActiveTargetConflicts() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();
    target.cancel();

    assertThatThrownBy(target::markReplaced)
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }
```

Add `import static org.assertj.core.api.Assertions.assertThatThrownBy;` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew compileTestJava` — fails: `markReplaced()` doesn't exist.

- [ ] **Step 3: Write minimal implementation**

In `ZoneEventAuthTarget.java`, add the import `com.butingbe.global.error.exception.ConflictException;` and replace:

```java
  /** 관리자가 이 타겟을 취소한다. 기존 제출 이력은 보존된다. */
  public void cancel() {
    this.status = ZoneEventTargetStatus.CANCELLED;
  }
```

with:

```java
  /** 관리자가 이 타겟을 취소한다(ACTIVE만 가능). 기존 제출 이력은 보존된다. */
  public void cancel() {
    if (status != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = ZoneEventTargetStatus.CANCELLED;
  }

  /** 다른 contentId로 긴급 교체되어 REPLACED 상태가 된다(ACTIVE만 가능). 새 ACTIVE 타겟은 서비스 계층에서 별도로 만든다. */
  public void markReplaced() {
    if (status != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = ZoneEventTargetStatus.REPLACED;
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.entity.ZoneEventAuthTargetTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS (6 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/entity/ZoneEventAuthTarget.java src/test/java/com/butingbe/domain/zoneevent/entity/ZoneEventAuthTargetTest.java
git commit -m "feat(zoneevent): guard auth target cancel and add markReplaced transition"
```

---

### Task 3: Repository lookups for scoped/duplicate target queries

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuthTargetRepository.java`

**Interfaces:**
- Produces: `Optional<ZoneEventAuthTarget> findByIdAndEvent_Id(UUID id, UUID eventId)`, `Optional<ZoneEventAuthTarget> findByEvent_IdAndPlaceContentIdAndStatus(UUID eventId, String placeContentId, ZoneEventTargetStatus status)`.

No dedicated repository test file exists for this repository in the codebase today (`AdminZoneEventServiceTest` exercises it indirectly) — these two methods will be exercised by Task 6's service-layer tests. Skip a standalone repository test to match existing convention.

- [ ] **Step 1: Add the methods**

```java
  /** eventId 범위로 스코프된 타겟 조회(다른 이벤트의 타겟 접근 방지). */
  Optional<ZoneEventAuthTarget> findByIdAndEvent_Id(UUID id, UUID eventId);

  /** 같은 이벤트에서 같은 관광지 contentId로 이미 등록된 타겟이 있는지(중복 등록 방지). */
  Optional<ZoneEventAuthTarget> findByEvent_IdAndPlaceContentIdAndStatus(
      UUID eventId, String placeContentId, ZoneEventTargetStatus status);
```

- [ ] **Step 2: Run to verify it compiles**

Run: `./gradlew compileJava --no-daemon -g "C:\\gradle-home"`
Expected: BUILD SUCCESSFUL (Spring Data derives the query at runtime; compile just checks method syntax).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventAuthTargetRepository.java
git commit -m "feat(zoneevent): add scoped and duplicate-check auth target queries"
```

---

### Task 4: Request/response DTOs for the admin target API

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetCreateReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetPatchReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetReplaceReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminAuthTargetResDto.java`

**Interfaces:**
- Produces: the four types below, used by Task 6 (service) and Task 7 (controller).

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 추가. PLACE는 placeContentId/contentTypeId 필수, OBJECT는 landmarkId/placeName 필수. */
public record AdminAuthTargetCreateReqDto(
    @NotNull String targetKind,
    String landmarkId,
    String placeContentId,
    String contentTypeId,
    String placeName,
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @NotNull @Min(30) @Max(500) Integer radiusM) {}
```

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 부분 수정. null 필드는 변경하지 않는다. latitude/longitude는 쌍으로만 허용. */
public record AdminAuthTargetPatchReqDto(
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @Min(30) @Max(500) Integer radiusM,
    String reason,
    @NotNull Long expectedRevision) {}
```

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 긴급 교체. 기존 타겟은 REPLACED로, 새 contentId 기준 타겟이 ACTIVE로 생성된다. */
public record AdminAuthTargetReplaceReqDto(
    @NotNull String placeContentId,
    @NotNull String contentTypeId,
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @NotNull @Min(30) @Max(500) Integer radiusM,
    String reason) {}
```

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;

/** 운영용 인증 타겟 전체 상세. 원본 좌표와 관리자 수정 좌표를 함께 내려준다. */
public record AdminAuthTargetResDto(
    String targetId,
    String eventId,
    String targetKind,
    String landmarkId,
    String placeContentId,
    String contentTypeId,
    String placeName,
    String guideText,
    String exampleFileKey,
    Double sourceLatitude,
    Double sourceLongitude,
    Double latitude,
    Double longitude,
    boolean coordinatesOverridden,
    Integer radiusM,
    String status,
    Long revision) {

  public static AdminAuthTargetResDto from(ZoneEventAuthTarget target) {
    return new AdminAuthTargetResDto(
        target.getId().toString(),
        target.getEvent().getId().toString(),
        target.getTargetKind().name(),
        target.getLandmarkId(),
        target.getPlaceContentId(),
        target.getContentTypeId(),
        target.getPlaceName(),
        target.getGuideText(),
        target.getExampleFileKey(),
        target.getSourceLatitude(),
        target.getSourceLongitude(),
        target.getLatitude(),
        target.getLongitude(),
        target.isCoordinatesOverridden(),
        target.getRadiusM(),
        target.getStatus().name(),
        target.getRevision());
  }
}
```

- [ ] **Step 1: Create all four files** with the exact content above.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileJava --no-daemon -g "C:\\gradle-home"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetCreateReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetPatchReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminAuthTargetReplaceReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminAuthTargetResDto.java
git commit -m "feat(zoneevent): add DTOs for the dedicated admin auth target API"
```

---

### Task 5: New error message keys (4 locales)

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_en.properties`
- Modify: `src/main/resources/messages_ja.properties`
- Modify: `src/main/resources/messages_zh.properties`

**Interfaces:**
- Produces: four new message keys, consumed by Task 6's exceptions: `error.zone_event.place_not_found`, `error.zone_event.target.invalid_kind`, `error.zone_event.target.invalid_coordinates`, `error.zone_event.target.duplicate`.

- [ ] **Step 1: Add to `messages.properties`** (after the `error.zone_event.target_not_found` line):

```properties
error.zone_event.place_not_found=관광지 정보를 찾을 수 없습니다.
error.zone_event.target.invalid_kind=선택한 타겟 종류에 필요한 값이 누락되었습니다.
error.zone_event.target.invalid_coordinates=위도와 경도는 함께 입력해야 합니다.
error.zone_event.target.duplicate=이미 등록된 장소입니다.
```

- [ ] **Step 2: Add to `messages_en.properties`** (same position):

```properties
error.zone_event.place_not_found=Place information not found.
error.zone_event.target.invalid_kind=Required fields for the selected target kind are missing.
error.zone_event.target.invalid_coordinates=Latitude and longitude must be provided together.
error.zone_event.target.duplicate=This place is already registered.
```

- [ ] **Step 3: Add to `messages_ja.properties`** (same position):

```properties
error.zone_event.place_not_found=観光地情報が見つかりません。
error.zone_event.target.invalid_kind=選択したターゲット種類に必要な値が不足しています。
error.zone_event.target.invalid_coordinates=緯度と経度は同時に入力する必要があります。
error.zone_event.target.duplicate=すでに登録された場所です。
```

- [ ] **Step 4: Add to `messages_zh.properties`** (same position):

```properties
error.zone_event.place_not_found=未找到景点信息。
error.zone_event.target.invalid_kind=所选目标类型缺少必填项。
error.zone_event.target.invalid_coordinates=纬度和经度必须同时提供。
error.zone_event.target.duplicate=该地点已注册。
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_en.properties src/main/resources/messages_ja.properties src/main/resources/messages_zh.properties
git commit -m "feat(zoneevent): add i18n messages for admin auth target API errors"
```

---

### Task 6: `AdminZoneEventTargetService` (list/create/patch/replace/cancel)

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetServiceTest.java`

**Interfaces:**
- Consumes: `ZoneEventRepository.findById`, `ZoneEventAuthTargetRepository.{findByEvent_Id, findByIdAndEvent_Id, findByEvent_IdAndPlaceContentIdAndStatus, save}`, `ZoneEventAuditLogRepository.save`, `OperatorAuthorization.requireOperator`, `PlaceService.getPlaceSummary` (Task 1), `ZoneEventAuthTarget.{cancel, markReplaced, update}` (Task 2), `AdminAuthTargetCreateReqDto`/`AdminAuthTargetPatchReqDto`/`AdminAuthTargetReplaceReqDto`/`AdminAuthTargetResDto` (Task 4).
- Produces:
  - `List<AdminAuthTargetResDto> list(AuthenticatedUser user, UUID eventId)`
  - `AdminAuthTargetResDto create(AuthenticatedUser user, UUID eventId, AdminAuthTargetCreateReqDto request)`
  - `AdminAuthTargetResDto patch(AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetPatchReqDto request)`
  - `AdminAuthTargetResDto replace(AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetReplaceReqDto request)`
  - `AdminAuthTargetResDto cancel(AuthenticatedUser user, UUID eventId, UUID targetId)`
  - Audit log rows: `targetType="TARGET"`, `targetId=<target's own id>`, actions `CREATE_TARGET`/`PATCH_TARGET`/`REPLACE_TARGET`/`CANCEL_TARGET`, `detail` always includes `"eventId"`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetServiceTest.java`:

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;
import com.butingbe.domain.place.service.PlaceService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventTargetServiceTest extends com.butingbe.support.AbstractContainerTest {

  @Autowired private AdminZoneEventTargetService targetService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;
  @MockitoBean private PlaceService placeService;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private UUID eventId;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("MISSION").name("미션").requiresUpload(false).build());
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("광안대교 야경")
                .startsAt(OffsetDateTime.now())
                .durationMinutes(120)
                .status(ZoneEventStatus.SCHEDULED)
                .baseReward(new com.butingbe.domain.zoneevent.entity.RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    eventId = event.getId();
    operator =
        AuthenticatedUser.from(
            userRepository.save(
                User.builder()
                    .email("admin-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Admin"))
                    .nickname("admin")
                    .role(UserRole.ADMIN)
                    .build()));
    normalUser =
        AuthenticatedUser.from(
            userRepository.save(
                User.builder()
                    .email("user-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "User"))
                    .nickname("user")
                    .role(UserRole.USER)
                    .build()));
  }

  private AdminAuthTargetCreateReqDto placeCreateReq(String contentId, Double lat, Double lng) {
    return new AdminAuthTargetCreateReqDto(
        "PLACE", null, contentId, "12", null, "가이드", null, lat, lng, 100);
  }

  @Test
  @DisplayName("PLACE 타겟 생성 시 좌표를 생략하면 원본 좌표를 그대로 쓴다")
  void createPlaceTargetDefaultsToSourceCoordinates() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThat(created.placeName()).isEqualTo("광안대교");
    assertThat(created.sourceLatitude()).isEqualTo(35.1532);
    assertThat(created.latitude()).isEqualTo(35.1532);
    assertThat(created.coordinatesOverridden()).isFalse();
    assertThat(created.status()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("PLACE 타겟 생성 시 좌표를 직접 주면 원본과 분리 저장되고 override로 표시된다")
  void createPlaceTargetWithOverrideCoordinates() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", 35.16, 129.12));

    assertThat(created.sourceLatitude()).isEqualTo(35.1532);
    assertThat(created.latitude()).isEqualTo(35.16);
    assertThat(created.coordinatesOverridden()).isTrue();
  }

  @Test
  @DisplayName("좌표를 하나만 주면 400이다")
  void createWithOnlyOneCoordinateRejected() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("126081", 35.16, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_coordinates");
  }

  @Test
  @DisplayName("존재하지 않는 placeContentId는 400이다")
  void createWithUnknownPlaceRejected() {
    when(placeService.getPlaceSummary(eq("GHOST"))).thenReturn(null);

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("GHOST", null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.place_not_found");
  }

  @Test
  @DisplayName("같은 이벤트에 같은 contentId를 두 번 등록하면 409다")
  void createDuplicatePlaceContentIdConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("126081", null, null)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  @DisplayName("OBJECT 타겟은 landmarkId·placeName·좌표가 모두 있어야 한다")
  void createObjectTargetRequiresExplicitFields() {
    AdminAuthTargetCreateReqDto missingLandmark =
        new AdminAuthTargetCreateReqDto(
            "OBJECT", null, null, null, "표지판", null, null, 35.1, 129.1, 100);
    assertThatThrownBy(() -> targetService.create(operator, eventId, missingLandmark))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_kind");

    AdminAuthTargetCreateReqDto ok =
        new AdminAuthTargetCreateReqDto(
            "OBJECT", "signpost-1", null, null, "표지판", null, null, 35.1, 129.1, 100);
    AdminAuthTargetResDto created = targetService.create(operator, eventId, ok);
    assertThat(created.coordinatesOverridden()).isFalse();
    assertThat(created.placeContentId()).isNull();
  }

  @Test
  @DisplayName("운영자가 아니면 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(
            () -> targetService.create(normalUser, eventId, placeCreateReq("126081", null, null)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("목록은 상태와 무관하게 이벤트의 모든 타겟을 반환한다")
  void listReturnsAllStatuses() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    targetService.cancel(operator, eventId, UUID.fromString(created.targetId()));

    assertThat(targetService.list(operator, eventId)).hasSize(1);
    assertThat(targetService.list(operator, eventId).get(0).status()).isEqualTo("CANCELLED");
  }

  @Test
  @DisplayName("수정은 좌표·반경·가이드를 바꾸고 이전값을 감사 로그에 남긴다")
  void patchUpdatesFieldsAndAudits() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());

    AdminAuthTargetResDto patched =
        targetService.patch(
            operator,
            eventId,
            targetId,
            new AdminAuthTargetPatchReqDto(
                "새 가이드", null, 35.2, 129.2, 200, "반경 확대", created.revision()));

    assertThat(patched.latitude()).isEqualTo(35.2);
    assertThat(patched.radiusM()).isEqualTo(200);
    assertThat(patched.coordinatesOverridden()).isTrue();
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("TARGET", targetId))
        .filteredOn(a -> a.getAction().equals("PATCH_TARGET"))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.getDetail()).containsEntry("reason", "반경 확대");
              assertThat(a.getDetail()).containsKey("before");
              assertThat(a.getDetail()).containsKey("after");
            });
  }

  @Test
  @DisplayName("좌표를 하나만 수정하면 400이다")
  void patchWithOnlyOneCoordinateRejected() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    UUID.fromString(created.targetId()),
                    new AdminAuthTargetPatchReqDto(
                        null, null, 35.2, null, null, null, created.revision())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_coordinates");
  }

  @Test
  @DisplayName("expectedRevision이 다르면 409다")
  void patchWithStaleRevisionConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    UUID.fromString(created.targetId()),
                    new AdminAuthTargetPatchReqDto(
                        null, null, null, null, 200, null, created.revision() + 1)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("CANCELLED 타겟은 수정할 수 없다")
  void patchNonActiveTargetConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());
    targetService.cancel(operator, eventId, targetId);

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    targetId,
                    new AdminAuthTargetPatchReqDto(null, null, null, null, 200, null, 0L)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("교체하면 기존 타겟은 REPLACED, 새 타겟은 ACTIVE가 된다")
  void replaceMarksOldReplacedAndCreatesNewActive() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    when(placeService.getPlaceSummary(eq("999999")))
        .thenReturn(new PlaceSummaryResDto("999999", "센텀시티", 35.17, 129.13));
    AdminAuthTargetResDto original =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    AdminAuthTargetResDto replacement =
        targetService.replace(
            operator,
            eventId,
            UUID.fromString(original.targetId()),
            new AdminAuthTargetReplaceReqDto(
                "999999", "12", "우천 대체", null, null, null, 120, "우천으로 긴급 교체"));

    assertThat(replacement.placeName()).isEqualTo("센텀시티");
    assertThat(replacement.status()).isEqualTo("ACTIVE");
    assertThat(targetService.list(operator, eventId))
        .anySatisfy(
            t -> {
              assertThat(t.targetId()).isEqualTo(original.targetId());
              assertThat(t.status()).isEqualTo("REPLACED");
            });
  }

  @Test
  @DisplayName("취소하면 CANCELLED가 되고, 그 타겟이 유일했다면 신규 참여가 막힌다(기존 join 로직으로 확인)")
  void cancelOnlyTargetBlocksNewJoins() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    targetService.cancel(operator, eventId, UUID.fromString(created.targetId()));

    ZoneEvent event = zoneEventRepository.findById(eventId).orElseThrow();
    event.activate();
    zoneEventRepository.saveAndFlush(event);

    assertThatThrownBy(
            () ->
                participationService.join(
                    operator, eventId, 35.1532, 129.1181))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }

  @Autowired private ZoneEventParticipationService participationService;

  @Test
  @DisplayName("이미 CANCELLED인 타겟을 다시 취소하면 409다")
  void cancelAlreadyCancelledConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());
    targetService.cancel(operator, eventId, targetId);

    assertThatThrownBy(() -> targetService.cancel(operator, eventId, targetId))
        .isInstanceOf(ConflictException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew compileTestJava --no-daemon -g "C:\\gradle-home"` — fails: `AdminZoneEventTargetService` doesn't exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetService.java`:

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;
import com.butingbe.domain.place.service.PlaceService;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인증 타겟(선택 장소) 전용 운영 API. 이벤트 CRUD와 분리해, 좌표·반경·예시 이미지를 개별 타겟 단위로 관리한다. 모든 메서드는 ROLE_ADMIN/MANAGER만
 * 호출할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AdminZoneEventTargetService {

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final PlaceService placeService;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public List<AdminAuthTargetResDto> list(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    findEvent(eventId);
    return authTargetRepository.findByEvent_Id(eventId).stream()
        .map(AdminAuthTargetResDto::from)
        .toList();
  }

  @Transactional
  public AdminAuthTargetResDto create(
      AuthenticatedUser user, UUID eventId, AdminAuthTargetCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    ZoneEventTargetKind kind = parseKind(request.targetKind());

    String placeName;
    String placeContentId = null;
    String contentTypeId = null;
    Double sourceLatitude = null;
    Double sourceLongitude = null;

    if (kind == ZoneEventTargetKind.PLACE) {
      if (!StringUtils.hasText(request.placeContentId())
          || !StringUtils.hasText(request.contentTypeId())) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
      }
      placeContentId = request.placeContentId();
      contentTypeId = request.contentTypeId();
      requireNoDuplicate(eventId, placeContentId, null);

      PlaceSummaryResDto summary = placeService.getPlaceSummary(placeContentId);
      if (summary == null || summary.latitude() == null || summary.longitude() == null) {
        throw new IllegalArgumentException("error.zone_event.place_not_found");
      }
      sourceLatitude = summary.latitude();
      sourceLongitude = summary.longitude();
      placeName = StringUtils.hasText(request.placeName()) ? request.placeName() : summary.title();
    } else {
      if (!StringUtils.hasText(request.landmarkId()) || !StringUtils.hasText(request.placeName())) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
      }
      placeName = request.placeName();
    }

    double[] coordinates =
        resolveCoordinates(
            request.latitude(), request.longitude(), sourceLatitude, sourceLongitude);

    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(kind)
                .landmarkId(request.landmarkId())
                .placeContentId(placeContentId)
                .contentTypeId(contentTypeId)
                .placeName(placeName)
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .sourceLatitude(sourceLatitude)
                .sourceLongitude(sourceLongitude)
                .latitude(coordinates[0])
                .longitude(coordinates[1])
                .radiusM(request.radiusM())
                .build());

    audit(user, "CREATE_TARGET", target.getId(), Map.of("eventId", eventId.toString()));
    return AdminAuthTargetResDto.from(target);
  }

  @Transactional
  public AdminAuthTargetResDto patch(
      AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetPatchReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventAuthTarget target = requireTarget(eventId, targetId);
    if (target.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (!target.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if ((request.latitude() == null) != (request.longitude() == null)) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
    }

    Map<String, Object> before = snapshot(target);
    target.update(
        null, request.guideText(), request.exampleFileKey(),
        request.latitude(), request.longitude(), request.radiusM());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("eventId", eventId.toString());
    if (request.reason() != null) {
      detail.put("reason", request.reason());
    }
    detail.put("before", before);
    detail.put("after", snapshot(target));
    audit(user, "PATCH_TARGET", target.getId(), detail);
    return AdminAuthTargetResDto.from(target);
  }

  @Transactional
  public AdminAuthTargetResDto replace(
      AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetReplaceReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    ZoneEventAuthTarget old = requireTarget(eventId, targetId);
    if (old.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    requireNoDuplicate(eventId, request.placeContentId(), old.getId());

    PlaceSummaryResDto summary = placeService.getPlaceSummary(request.placeContentId());
    if (summary == null || summary.latitude() == null || summary.longitude() == null) {
      throw new IllegalArgumentException("error.zone_event.place_not_found");
    }
    double[] coordinates =
        resolveCoordinates(
            request.latitude(), request.longitude(), summary.latitude(), summary.longitude());

    old.markReplaced();

    ZoneEventAuthTarget replacement =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeContentId(request.placeContentId())
                .contentTypeId(request.contentTypeId())
                .placeName(summary.title())
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .sourceLatitude(summary.latitude())
                .sourceLongitude(summary.longitude())
                .latitude(coordinates[0])
                .longitude(coordinates[1])
                .radiusM(request.radiusM())
                .build());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("eventId", eventId.toString());
    detail.put("oldTargetId", old.getId().toString());
    if (request.reason() != null) {
      detail.put("reason", request.reason());
    }
    audit(user, "REPLACE_TARGET", replacement.getId(), detail);
    return AdminAuthTargetResDto.from(replacement);
  }

  @Transactional
  public AdminAuthTargetResDto cancel(AuthenticatedUser user, UUID eventId, UUID targetId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventAuthTarget target = requireTarget(eventId, targetId);
    target.cancel();
    audit(user, "CANCEL_TARGET", target.getId(), Map.of("eventId", eventId.toString()));
    return AdminAuthTargetResDto.from(target);
  }

  private void requireNoDuplicate(UUID eventId, String placeContentId, UUID excludeTargetId) {
    authTargetRepository
        .findByEvent_IdAndPlaceContentIdAndStatus(
            eventId, placeContentId, ZoneEventTargetStatus.ACTIVE)
        .filter(existing -> excludeTargetId == null || !existing.getId().equals(excludeTargetId))
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException("error.zone_event.target.duplicate");
            });
  }

  private double[] resolveCoordinates(
      Double requestedLatitude, Double requestedLongitude, Double sourceLatitude, Double sourceLongitude) {
    if (requestedLatitude == null && requestedLongitude == null) {
      if (sourceLatitude == null || sourceLongitude == null) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
      }
      return new double[] {sourceLatitude, sourceLongitude};
    }
    if (requestedLatitude == null || requestedLongitude == null) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
    }
    return new double[] {requestedLatitude, requestedLongitude};
  }

  private ZoneEventTargetKind parseKind(String kind) {
    try {
      return ZoneEventTargetKind.valueOf(kind.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
    }
  }

  private ZoneEvent findEvent(UUID eventId) {
    return zoneEventRepository
        .findById(eventId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private ZoneEventAuthTarget requireTarget(UUID eventId, UUID targetId) {
    return authTargetRepository
        .findByIdAndEvent_Id(targetId, eventId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.target_not_found"));
  }

  private Map<String, Object> snapshot(ZoneEventAuthTarget target) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("latitude", target.getLatitude());
    map.put("longitude", target.getLongitude());
    map.put("radiusM", target.getRadiusM());
    map.put("guideText", target.getGuideText());
    map.put("exampleFileKey", target.getExampleFileKey());
    return map;
  }

  private void audit(AuthenticatedUser user, String action, UUID targetId, Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType("TARGET")
            .targetId(targetId)
            .detail(detail)
            .build());
  }
}
```

**Note for the implementer:** `ZoneEventTargetKind` for OBJECT never has a "source" (no Tour API record), so `resolveCoordinates` correctly rejects an OBJECT create with both coordinates omitted (source is `null`/`null`, hits the `sourceLatitude == null` branch → `invalid_coordinates`). This matches the plan's intended behavior even though the test above only exercises the "OBJECT with explicit coordinates" path — no extra code needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.service.AdminZoneEventTargetServiceTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all 14 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventTargetServiceTest.java
git commit -m "feat(zoneevent): add AdminZoneEventTargetService for auth target CRUD"
```

---

### Task 7: `AdminZoneEventTargetController`

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetController.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetControllerTest.java`

**Interfaces:**
- Consumes: `AdminZoneEventTargetService` (Task 6), `AdminAuthTargetCreateReqDto`/`AdminAuthTargetPatchReqDto`/`AdminAuthTargetReplaceReqDto`/`AdminAuthTargetResDto` (Task 4).
- Produces: routes `GET/POST /admin/zone-events/{eventId}/targets`, `PATCH /admin/zone-events/{eventId}/targets/{targetId}`, `POST /admin/zone-events/{eventId}/targets/{targetId}/replace`, `POST /admin/zone-events/{eventId}/targets/{targetId}/cancel`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetControllerTest.java`:

```java
package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventTargetService;
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
class AdminZoneEventTargetControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID TARGET_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventTargetService targetService;
  @InjectMocks private AdminZoneEventTargetController controller;

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
  @DisplayName("타겟 생성은 201을 반환한다")
  void create() throws Exception {
    when(targetService.create(any(), eq(EVENT_ID), any())).thenReturn(target("ACTIVE"));

    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets", EVENT_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "targetKind":"PLACE","placeContentId":"126081","contentTypeId":"12",
                      "latitude":35.1532,"longitude":129.1181,"radiusM":80
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("반경이 범위를 벗어나면 400이다")
  void createInvalidRadius() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets", EVENT_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "targetKind":"PLACE","placeContentId":"126081","contentTypeId":"12",
                      "latitude":35.1532,"longitude":129.1181,"radiusM":10
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("목록은 200을 반환한다")
  void list() throws Exception {
    when(targetService.list(any(), eq(EVENT_ID))).thenReturn(List.of(target("ACTIVE")));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}/targets", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
  }

  @Test
  @DisplayName("수정은 200을 반환한다")
  void patchTarget() throws Exception {
    when(targetService.patch(any(), eq(EVENT_ID), eq(TARGET_ID), any()))
        .thenReturn(target("ACTIVE"));

    mockMvc
        .perform(
            patch("/admin/zone-events/{eventId}/targets/{targetId}", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content("{\"radiusM\":200,\"expectedRevision\":0}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("expectedRevision이 빠지면 400이다")
  void patchWithoutExpectedRevisionIsBadRequest() throws Exception {
    mockMvc
        .perform(
            patch("/admin/zone-events/{eventId}/targets/{targetId}", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content("{\"radiusM\":200}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("교체·취소는 200을 반환한다")
  void replaceAndCancel() throws Exception {
    when(targetService.replace(any(), eq(EVENT_ID), eq(TARGET_ID), any()))
        .thenReturn(target("ACTIVE"));
    when(targetService.cancel(any(), eq(EVENT_ID), eq(TARGET_ID))).thenReturn(target("CANCELLED"));

    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets/{targetId}/replace", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content(
                    "{\"placeContentId\":\"999999\",\"contentTypeId\":\"12\",\"radiusM\":100}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/admin/zone-events/{eventId}/targets/{targetId}/cancel", EVENT_ID, TARGET_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
  }

  @Test
  @DisplayName("운영 권한이 없으면 403이다")
  void forbidden() throws Exception {
    when(targetService.list(any(), eq(EVENT_ID)))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}/targets", EVENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("운영 권한이 없습니다."));
  }

  private AdminAuthTargetResDto target(String status) {
    return new AdminAuthTargetResDto(
        TARGET_ID.toString(),
        EVENT_ID.toString(),
        "PLACE",
        null,
        "126081",
        "12",
        "광안대교",
        "가이드",
        null,
        35.1532,
        129.1181,
        35.1532,
        129.1181,
        false,
        80,
        status,
        0L);
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
        return new AuthenticatedUser(USER_ID, "admin@example.com", "admin", List.of());
      }
    };
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew compileTestJava --no-daemon -g "C:\\gradle-home"` — fails: `AdminZoneEventTargetController` doesn't exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetController.java`:

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventTargetService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 타겟(선택 장소) 운영 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-events/{eventId}/targets")
@RequiredArgsConstructor
public class AdminZoneEventTargetController {

  private final AdminZoneEventTargetService adminZoneEventTargetService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminAuthTargetResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("인증 타겟 목록", adminZoneEventTargetService.list(user, eventId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestBody @Valid AdminAuthTargetCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "인증 타겟 추가", adminZoneEventTargetService.create(user, eventId, request)));
  }

  @PatchMapping("/{targetId}")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> patch(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId,
      @RequestBody @Valid AdminAuthTargetPatchReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 수정", adminZoneEventTargetService.patch(user, eventId, targetId, request)));
  }

  @PostMapping("/{targetId}/replace")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> replace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId,
      @RequestBody @Valid AdminAuthTargetReplaceReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 교체", adminZoneEventTargetService.replace(user, eventId, targetId, request)));
  }

  @PostMapping("/{targetId}/cancel")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> cancel(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 취소", adminZoneEventTargetService.cancel(user, eventId, targetId)));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.butingbe.domain.zoneevent.controller.AdminZoneEventTargetControllerTest" --no-daemon -g "C:\\gradle-home"`
Expected: PASS, all 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetController.java src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventTargetControllerTest.java
git commit -m "feat(zoneevent): add AdminZoneEventTargetController"
```

---

### Task 8: OpenAPI documentation

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml`

**Interfaces:**
- Consumes: nothing (pure documentation; must match the routes/DTOs from Tasks 4 and 7 exactly).

- [ ] **Step 1: Add path entries**

Insert a new `/api/v1/admin/zone-events/{eventId}/targets` block right after the existing `/api/v1/admin/zone-events/{eventId}/cancel` block (around line 4902 in the current file — find the blank line after that block's closing before the next top-level path key) with `get`/`post`, and a `/api/v1/admin/zone-events/{eventId}/targets/{targetId}` block with `patch`, and `.../replace` and `.../cancel` blocks with `post`. Use tag `Zone Event`, `security: [opaqueToken: []]` on every operation, matching the style read from lines 4683-4902 exactly:

```yaml
  /api/v1/admin/zone-events/{eventId}/targets:
    get:
      tags:
        - Zone Event
      summary: "인증 타겟 목록"
      description: "ROLE_ADMIN/MANAGER. 상태와 무관하게 이벤트의 모든 타겟을 반환합니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - name: eventId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminAuthTargetListEnvelope"
        "401":
          description: "Unauthorized"
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
          description: "이벤트를 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
    post:
      tags:
        - Zone Event
      summary: "인증 타겟 추가"
      description: "ROLE_ADMIN/MANAGER. PLACE는 placeContentId로 관광지 마스터를 조회해 이름·원본 좌표를 스냅샷합니다(마스터 데이터는 수정하지 않음)."
      security:
        - opaqueToken: [ ]
      parameters:
        - name: eventId
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
              $ref: "#/components/schemas/AdminAuthTargetCreateRequest"
      responses:
        "201":
          description: "Created"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminAuthTargetEnvelope"
        "400":
          description: "유효성 실패 또는 관광지를 찾을 수 없음"
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
          description: "이벤트를 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "같은 이벤트에 같은 관광지가 이미 등록됨"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-events/{eventId}/targets/{targetId}:
    patch:
      tags:
        - Zone Event
      summary: "인증 타겟 수정"
      description: "ROLE_ADMIN/MANAGER. latitude/longitude/radiusM/guideText/exampleFileKey를 수정합니다. 좌표는 쌍으로만 허용합니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - name: eventId
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: targetId
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
              $ref: "#/components/schemas/AdminAuthTargetPatchRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminAuthTargetEnvelope"
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
          description: "타겟을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "ACTIVE 상태가 아니거나 expectedRevision 불일치"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-events/{eventId}/targets/{targetId}/replace:
    post:
      tags:
        - Zone Event
      summary: "인증 타겟 긴급 교체"
      description: "ROLE_ADMIN/MANAGER. 다른 contentId로 교체합니다. 기존 타겟은 REPLACED, 새 타겟은 ACTIVE가 됩니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - name: eventId
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: targetId
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
              $ref: "#/components/schemas/AdminAuthTargetReplaceRequest"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminAuthTargetEnvelope"
        "400":
          description: "유효성 실패 또는 관광지를 찾을 수 없음"
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
          description: "타겟을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "ACTIVE 상태가 아니거나 같은 관광지가 이미 등록됨"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
  /api/v1/admin/zone-events/{eventId}/targets/{targetId}/cancel:
    post:
      tags:
        - Zone Event
      summary: "인증 타겟 취소"
      description: "ROLE_ADMIN/MANAGER. 이 타겟만 취소하며, 기존 제출 이력은 보존됩니다. 이벤트에 ACTIVE 타겟이 하나도 남지 않으면 신규 참여가 막힙니다."
      security:
        - opaqueToken: [ ]
      parameters:
        - name: eventId
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: targetId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/AdminAuthTargetEnvelope"
        "403":
          description: "운영 권한 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "404":
          description: "타겟을 찾을 수 없음"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
        "409":
          description: "ACTIVE 상태가 아님"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiErrorResponse"
```

- [ ] **Step 2: Add schema entries**

Insert into `components.schemas`, right after the existing `AdminZoneEventUpdateRequest` schema (around line 9982, before `RejectRequest`):

```yaml
    AdminAuthTargetDetail:
      type: object
      properties:
        targetId:
          type: string
          format: uuid
        eventId:
          type: string
          format: uuid
        targetKind:
          type: string
          enum:
            - PLACE
            - OBJECT
        landmarkId:
          type: string
          nullable: true
        placeContentId:
          type: string
          nullable: true
        contentTypeId:
          type: string
          nullable: true
        placeName:
          type: string
        guideText:
          type: string
          nullable: true
        exampleFileKey:
          type: string
          nullable: true
        sourceLatitude:
          type: number
          format: double
          nullable: true
          description: "관광지 마스터 원본 좌표(관리자가 수정해도 바뀌지 않음)"
        sourceLongitude:
          type: number
          format: double
          nullable: true
        latitude:
          type: number
          format: double
          description: "실제 인증에 쓰는 중심 좌표"
        longitude:
          type: number
          format: double
        coordinatesOverridden:
          type: boolean
          description: "관리자가 원본 좌표를 수정했는지"
        radiusM:
          type: integer
          description: "30~500"
        status:
          type: string
          enum:
            - ACTIVE
            - REPLACED
            - CANCELLED
        revision:
          type: integer
          format: int64

    AdminAuthTargetEnvelope:
      type: object
      properties:
        success:
          type: boolean
          example: true
        message:
          type: string
          example: "인증 타겟"
        data:
          $ref: "#/components/schemas/AdminAuthTargetDetail"

    AdminAuthTargetListEnvelope:
      type: object
      properties:
        success:
          type: boolean
          example: true
        message:
          type: string
          example: "인증 타겟 목록"
        data:
          type: array
          items:
            $ref: "#/components/schemas/AdminAuthTargetDetail"

    AdminAuthTargetCreateRequest:
      type: object
      properties:
        targetKind:
          type: string
          enum:
            - PLACE
            - OBJECT
        landmarkId:
          type: string
          nullable: true
          description: "OBJECT일 때 필수"
        placeContentId:
          type: string
          nullable: true
          description: "PLACE일 때 필수. 관광지 마스터 contentId"
        contentTypeId:
          type: string
          nullable: true
          description: "PLACE일 때 필수"
        placeName:
          type: string
          nullable: true
          description: "OBJECT일 때 필수. PLACE는 생략 시 관광지 마스터 제목을 사용"
        guideText:
          type: string
          nullable: true
        exampleFileKey:
          type: string
          nullable: true
        latitude:
          type: number
          format: double
          nullable: true
          description: "생략하면(둘 다 생략 시) 원본 좌표를 사용. 하나만 주면 400"
        longitude:
          type: number
          format: double
          nullable: true
        radiusM:
          type: integer
          description: "30~500"
      example:
        targetKind: "PLACE"
        placeContentId: "126081"
        contentTypeId: "12"
        latitude: 35.1532
        longitude: 129.1181
        radiusM: 80
        guideText: "풍경과 손이 함께 보이게 촬영"
        exampleFileKey: "uploads/images/example.jpg"

    AdminAuthTargetPatchRequest:
      type: object
      properties:
        guideText:
          type: string
          nullable: true
        exampleFileKey:
          type: string
          nullable: true
        latitude:
          type: number
          format: double
          nullable: true
        longitude:
          type: number
          format: double
          nullable: true
        radiusM:
          type: integer
          nullable: true
          description: "30~500"
        reason:
          type: string
          nullable: true
        expectedRevision:
          type: integer
          format: int64

    AdminAuthTargetReplaceRequest:
      type: object
      properties:
        placeContentId:
          type: string
        contentTypeId:
          type: string
        guideText:
          type: string
          nullable: true
        exampleFileKey:
          type: string
          nullable: true
        latitude:
          type: number
          format: double
          nullable: true
        longitude:
          type: number
          format: double
          nullable: true
        radiusM:
          type: integer
          description: "30~500"
        reason:
          type: string
          nullable: true

```

- [ ] **Step 3: Validate the YAML parses**

Run (Node is available; this sandbox has no Python — matches the existing convention for validating this file):

```bash
cd "C:/Users/조준연/Desktop/bu-ting-backend" && npm install js-yaml --no-save --silent 2>&1 | tail -5 && node -e "
const yaml = require('js-yaml');
const fs = require('fs');
const doc = yaml.load(fs.readFileSync('src/main/resources/static/docs/openapi3.yaml', 'utf8'));
const paths = [
  '/api/v1/admin/zone-events/{eventId}/targets',
  '/api/v1/admin/zone-events/{eventId}/targets/{targetId}',
  '/api/v1/admin/zone-events/{eventId}/targets/{targetId}/replace',
  '/api/v1/admin/zone-events/{eventId}/targets/{targetId}/cancel',
];
for (const p of paths) {
  if (!doc.paths[p]) throw new Error('Missing path: ' + p);
}
const schemas = [
  'AdminAuthTargetDetail', 'AdminAuthTargetEnvelope', 'AdminAuthTargetListEnvelope',
  'AdminAuthTargetCreateRequest', 'AdminAuthTargetPatchRequest', 'AdminAuthTargetReplaceRequest',
];
for (const s of schemas) {
  if (!doc.components.schemas[s]) throw new Error('Missing schema: ' + s);
}
console.log('OK: all paths and schemas present, YAML parses cleanly.');
"
```

Expected: `OK: all paths and schemas present, YAML parses cleanly.`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(zoneevent): openapi3.yaml에 인증 타겟 운영 API 문서화"
```

---

### Task 9: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Per the Korean-path workaround (see Global Constraints), from a fresh ASCII-path clone:

```bash
git clone --branch <this-branch-name> "C:/Users/조준연/Desktop/bu-ting-backend" /c/dev/bu-ting-backend-test
cd /c/dev/bu-ting-backend-test
./gradlew test --no-daemon -g "C:\\gradle-home"
```

Expected: BUILD SUCCESSFUL, 0 failures (includes the pre-existing suite plus every test added in Tasks 1, 2, 6, 7).

- [ ] **Step 2: Re-check the OpenAPI validation script from Task 8, Step 3** if any manual edits were made after that task.

- [ ] **Step 3: Confirm scope boundaries**

Re-read the issue's "완료 조건" checklist and confirm each item maps to a task:
- `sourceLatitude/sourceLongitude` + `latitude/longitude` + `coordinatesOverridden` in every response → Task 4 (`AdminAuthTargetResDto`).
- No writes to place-master data → Task 1 (`getPlaceSummary` is a GET-only Tour API call).
- Tests added/passing → Tasks 1, 2, 6, 7 plus this task's full-suite run.

No commit for this task — it's a verification checkpoint before opening the PR.
