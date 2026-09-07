# 회차·슬롯 관리 API 및 자동 시작/종료 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이슈 #238 — 관리자 회차(라운드)·구역 슬롯(zone-event) API를 새 상태 모델(DRAFT→SCHEDULED→ACTIVE→CLOSED, +CANCELLED)로 다시 구현하고, 서버 시간 기준 자동 시작/종료를 스케줄러와 요청 처리 시점 양쪽에서 일관되게 적용한다.

**Architecture:** 기존 `ZoneEventRound`/`ZoneEventRoundSlot`/`AdminRoundConsoleService`/`ZoneEventRoundScheduler`/`RoundStatusQueryService`/`AdminZoneEventService` 위에서, 상태 enum을 확장하고 새 `RoundTransitionService`를 도입해 스케줄러와 조회 경로가 같은 전환 로직을 공유하게 한다. 회차 생성은 시간·이름만 받는 초안(DRAFT)이고, 구역 슬롯은 `POST /admin/zone-events`로 개별 추가하며 이때 `ZoneEventRoundSlot`과 연결한다(기존에 끊어져 있던 연결을 이번에 고친다).

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL(Flyway), JUnit5 + AssertJ + Testcontainers(`AbstractContainerTest`), MockMvc(standalone, 컨트롤러 테스트).

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-09-08-zone-event-round-lifecycle-design.md` (모든 결정의 근거).
- 검증 명령: `./gradlew check` — 한글 경로에서 Gradle 데몬이 깨지므로, 반드시 ASCII 전용 경로(예: `C:\dev\bu-ting-backend`)로 클론하거나 워크트리를 만들어 그 경로에서 실행한다(`GRADLE_USER_HOME`도 ASCII 경로로 지정).
- 신규 엔드포인트는 모두 `openapi3.yaml`에 기존 형제 항목과 같은 한국어 서술 스타일로 문서화한다. 삭제되는 엔드포인트 문서는 제거한다.
- 모든 관리자 API는 `OperatorAuthorization.requireOperator(user)`로 시작한다(기존 패턴 유지).
- 상태를 바꾸는 모든 동작은 `ZoneEventAuditLog`에 기록한다(자동 전환 제외).
- 커밋은 각 태스크가 끝날 때마다 한다(테스트 통과 확인 후).

---

## Task 1: 마이그레이션 — 회차 상태 모델 확장 및 컬럼 추가

**Files:**
- Create: `src/main/resources/db/migration/V43__zone_event_round_lifecycle.sql`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundMigrationTest.java:40` (상태 값 `OPEN`→`ACTIVE`)

**Interfaces:**
- Produces: `zone_event_round.status` 허용값이 `DRAFT,SCHEDULED,ACTIVE,CLOSED,CANCELLED,SETTLED`가 됨. `zone_event_round.excellence_reward`(jsonb, nullable), `zone_event_round.cancel_reason`(varchar(300), nullable) 컬럼 추가. `round_no`는 `NOT NULL`로 바뀌고 일반 유니크 인덱스가 됨(기존 partial index 대체).

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- Round lifecycle: DRAFT/CANCELLED added, OPEN renamed to ACTIVE.
-- Round-level default excellence reward + cancel reason. roundNo now client-supplied and required.

ALTER TABLE zone_event_round DROP CONSTRAINT ck_zone_event_round_status;
ALTER TABLE zone_event_round
    ADD CONSTRAINT ck_zone_event_round_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'SETTLED'));

UPDATE zone_event_round SET status = 'ACTIVE' WHERE status = 'OPEN';

ALTER TABLE zone_event_round
    ADD COLUMN excellence_reward JSONB,
    ADD COLUMN cancel_reason VARCHAR(300);

DROP INDEX uk_zone_event_round_no;
DELETE FROM zone_event_round WHERE round_no IS NULL;
ALTER TABLE zone_event_round ALTER COLUMN round_no SET NOT NULL;
CREATE UNIQUE INDEX uk_zone_event_round_no ON zone_event_round (round_no);
```

- [ ] **Step 2: `ZoneEventRoundMigrationTest`의 상태값을 새 모델에 맞춘다**

`insertRound(connection, "OPEN")` 호출을 `insertRound(connection, "ACTIVE")`로 바꾼다(40번째 줄 근처, 유효한 상태값으로 성공 케이스를 유지하기 위함). `"WRONG"`으로 실패하는 케이스는 그대로 둔다.

- [ ] **Step 3: 마이그레이션 테스트만 먼저 실행해 통과를 확인**

Run (ASCII 경로 클론에서): `gradlew.bat test --tests "com.butingbe.domain.zoneevent.repository.ZoneEventRoundMigrationTest"`
Expected: PASS (`ck_zone_event_round_status`가 존재하고 `ACTIVE`는 통과, `WRONG`은 SQLException).

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/db/migration/V43__zone_event_round_lifecycle.sql src/test/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundMigrationTest.java
git commit -m "feat(zoneevent): 회차 상태에 DRAFT/CANCELLED 추가, OPEN을 ACTIVE로 변경"
```

---

## Task 2: `RoundStatus` enum 확장 및 `ZoneEventRound` 엔티티 재작성

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/entity/RoundStatus.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/entity/ZoneEventRound.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/entity/ZoneEventRoundTest.java`

**Interfaces:**
- Consumes: `RewardSnapshot` (기존, `com.butingbe.domain.zoneevent.entity.RewardSnapshot`)
- Produces: `ZoneEventRound.builder()`가 이제 `roundNo(필수 개념상), name, startsAt, endsAt, timezone, roundType, excellenceReward`를 받고 기본 상태는 `DRAFT`. 새 메서드: `confirmSchedule()`, `cancel(String reason)`, `applyEditable(String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, RoundType roundType)`, `activate()`(기존 `open()` 대체), `getExcellenceReward()`, `getCancelReason()`.

- [ ] **Step 1: `RoundStatus`에 `DRAFT`, `CANCELLED` 추가하고 `OPEN`을 `ACTIVE`로 변경**

```java
package com.butingbe.domain.zoneevent.entity;

/** 회차 수명 주기. DRAFT → SCHEDULED → ACTIVE → CLOSED(→SETTLED), 또는 CANCELLED로 종료. */
public enum RoundStatus {
  DRAFT,
  SCHEDULED,
  ACTIVE,
  CLOSED,
  CANCELLED,
  SETTLED
}
```

- [ ] **Step 2: 실패하는 엔티티 테스트 작성(기존 `ZoneEventRoundTest.java` 내용을 아래로 교체)**

```java
package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ZoneEventRoundTest {

  private ZoneEventRound draft() {
    return ZoneEventRound.builder()
        .roundNo(1)
        .name("부산 바다 인증의 날")
        .startsAt(OffsetDateTime.now())
        .endsAt(OffsetDateTime.now().plusDays(1))
        .timezone("Asia/Seoul")
        .roundType(RoundType.REGULAR)
        .build();
  }

  @Test
  void 생성하면_DRAFT다() {
    assertThat(draft().getStatus()).isEqualTo(RoundStatus.DRAFT);
  }

  @Test
  void DRAFT에서_확정하면_SCHEDULED다() {
    ZoneEventRound round = draft();
    round.confirmSchedule();
    assertThat(round.getStatus()).isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  void SCHEDULED가_아니면_확정할_수_없다() {
    ZoneEventRound round = draft();
    assertThatThrownBy(round::activate).isInstanceOf(ConflictException.class);
  }

  @Test
  void SCHEDULED에서_activate하면_ACTIVE다() {
    ZoneEventRound round = draft();
    round.confirmSchedule();
    round.activate();
    assertThat(round.getStatus()).isEqualTo(RoundStatus.ACTIVE);
  }

  @Test
  void ACTIVE가_아니면_close할_수_없다() {
    ZoneEventRound round = draft();
    assertThatThrownBy(round::close).isInstanceOf(ConflictException.class);
  }

  @Test
  void DRAFT_SCHEDULED_ACTIVE에서_취소할_수_있고_CLOSED_이후엔_안된다() {
    ZoneEventRound round = draft();
    round.cancel("우천으로 인한 취소");
    assertThat(round.getStatus()).isEqualTo(RoundStatus.CANCELLED);
    assertThat(round.getCancelReason()).isEqualTo("우천으로 인한 취소");

    ZoneEventRound closed = draft();
    closed.confirmSchedule();
    closed.activate();
    closed.close();
    assertThatThrownBy(() -> closed.cancel("사유")).isInstanceOf(ConflictException.class);
  }

  @Test
  void DRAFT_SCHEDULED에서만_메타데이터를_수정할_수_있다() {
    ZoneEventRound round = draft();
    round.applyEditable("새 이름", null, null, null, null);
    assertThat(round.getName()).isEqualTo("새 이름");

    round.confirmSchedule();
    round.activate();
    assertThatThrownBy(() -> round.applyEditable("또 다른 이름", null, null, null, null))
        .isInstanceOf(ConflictException.class);
  }
}
```

- [ ] **Step 3: 테스트 실행해 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — `confirmSchedule`, `cancel`, `applyEditable`, `getCancelReason` 등이 없어 컴파일 에러.

- [ ] **Step 4: `ZoneEventRound` 엔티티를 아래 내용으로 교체**

```java
package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
import com.butingbe.global.error.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 이벤트가 동시에 열리는 운영 단위. v1은 1일(KST 10:00 → 익일 10:00). */
@Entity
@Table(name = "zone_event_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventRound extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "round_id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "round_type", nullable = false, length = 20)
  private RoundType roundType;

  /** 관리자 페이지에 노출할 회차 번호. 관리자가 생성 시점에 직접 지정한다(중복 시 409). */
  @Column(name = "round_no", nullable = false)
  private Integer roundNo;

  @Column(length = 255)
  private String name;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  @Column(nullable = false, length = 40)
  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RoundStatus status;

  @Column(name = "closed_at")
  private OffsetDateTime closedAt;

  @Column(name = "settled_at")
  private OffsetDateTime settledAt;

  @Column(name = "cancel_reason", length = 300)
  private String cancelReason;

  /** 회차 공통 기본 우수 보상(TOP N 포함). 구역 슬롯 생성 시 개별로 안 넘기면 이 값을 물려받는다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "excellence_reward", columnDefinition = "jsonb")
  private RewardSnapshot excellenceReward;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private ZoneEventRound(
      RoundType roundType,
      Integer roundNo,
      String name,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String timezone,
      RoundStatus status,
      RewardSnapshot excellenceReward) {
    this.roundType = roundType == null ? RoundType.REGULAR : roundType;
    this.roundNo = roundNo;
    this.name = name;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone == null ? "Asia/Seoul" : timezone;
    this.status = status == null ? RoundStatus.DRAFT : status;
    this.excellenceReward = excellenceReward;
  }

  /** DRAFT → SCHEDULED. DRAFT가 아니면 409. */
  public void confirmSchedule() {
    requireStatus(RoundStatus.DRAFT);
    this.status = RoundStatus.SCHEDULED;
  }

  /** SCHEDULED → ACTIVE. SCHEDULED가 아니면 409. */
  public void activate() {
    requireStatus(RoundStatus.SCHEDULED);
    this.status = RoundStatus.ACTIVE;
  }

  /** ACTIVE → CLOSED. ACTIVE가 아니면 409. */
  public void close() {
    requireStatus(RoundStatus.ACTIVE);
    this.status = RoundStatus.CLOSED;
    this.closedAt = OffsetDateTime.now();
  }

  /** DRAFT/SCHEDULED/ACTIVE → CANCELLED. 그 외 상태면 409. */
  public void cancel(String reason) {
    if (status != RoundStatus.DRAFT && status != RoundStatus.SCHEDULED && status != RoundStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = RoundStatus.CANCELLED;
    this.cancelReason = reason;
  }

  /** DRAFT/SCHEDULED에서만 메타데이터 수정 가능. null은 미변경. 그 외 상태면 409. */
  public void applyEditable(
      String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, RoundType roundType) {
    if (status != RoundStatus.DRAFT && status != RoundStatus.SCHEDULED) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (name != null) {
      this.name = name;
    }
    if (startsAt != null) {
      this.startsAt = startsAt;
    }
    if (endsAt != null) {
      this.endsAt = endsAt;
    }
    if (timezone != null) {
      this.timezone = timezone;
    }
    if (roundType != null) {
      this.roundType = roundType;
    }
  }

  /** 정산 완료 표식. 멱등: 이미 SETTLED면 그대로 둔다. */
  public void settle(OffsetDateTime at) {
    if (status != RoundStatus.SETTLED) {
      this.status = RoundStatus.SETTLED;
      this.settledAt = at;
    }
  }

  private void requireStatus(RoundStatus expected) {
    if (status != expected) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
  }
}
```

주의: 기존 `open()`/`assignRoundNo()` 메서드는 삭제됐다. `assignRoundNo`를 참조하는 다른 코드가 있는지 `grep -rn "assignRoundNo\|\.open()" src/main src/test`로 확인하고 있으면 이번 태스크에서 함께 제거한다(이후 태스크에서 `AdminRoundConsoleService`/스케줄러/테스트를 다시 쓰면서 자연히 없어질 것이다).

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.entity.ZoneEventRoundTest"`
Expected: PASS (7개 테스트 모두).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/entity/RoundStatus.java src/main/java/com/butingbe/domain/zoneevent/entity/ZoneEventRound.java src/test/java/com/butingbe/domain/zoneevent/entity/ZoneEventRoundTest.java
git commit -m "feat(zoneevent): ZoneEventRound에 DRAFT/CANCELLED 상태와 확정·취소·수정 메서드 추가"
```

---

## Task 3: 리포지토리 확장 (`existsByRoundNo`, 검색용 Specification, 겹침 조회)

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundSlotRepository.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRepository.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundRepositoryTest.java`

**Interfaces:**
- Produces: `ZoneEventRoundRepository extends JpaSpecificationExecutor<ZoneEventRound>`에 `boolean existsByRoundNo(Integer roundNo)` 추가. `ZoneEventRoundSlotRepository`에 `Optional<ZoneEventRoundSlot> findByRound_IdAndZoneId(UUID roundId, String zoneId)` 추가. `ZoneEventRepository`에 `List<ZoneEvent> findByZoneIdAndStatusIn(String zoneId, List<ZoneEventStatus> statuses)` 추가.

- [ ] **Step 1: `ZoneEventRoundRepositoryTest`에 실패하는 테스트 추가**

기존 파일 마지막(클래스 닫는 `}` 이전)에 아래 테스트를 추가한다(기존 파일 구조를 유지하기 위해 먼저 `Read`로 임포트/클래스 선언을 확인한 뒤 맞춰 추가할 것):

```java
  @Test
  void 회차번호_존재여부를_확인한다() {
    repository.save(
        ZoneEventRound.builder()
            .roundNo(7)
            .startsAt(OffsetDateTime.now())
            .endsAt(OffsetDateTime.now().plusDays(1))
            .build());

    assertThat(repository.existsByRoundNo(7)).isTrue();
    assertThat(repository.existsByRoundNo(8)).isFalse();
  }
```

(임포트가 이미 `OffsetDateTime`, `assertThat`, `ZoneEventRound`를 포함하고 있는지 확인하고 없으면 추가한다.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — `existsByRoundNo`가 없음.

- [ ] **Step 3: 세 리포지토리에 메서드 추가**

`ZoneEventRoundRepository.java`에서 인터페이스 선언을 `JpaSpecificationExecutor`도 상속하도록 바꾸고 메서드를 추가한다:

```java
public interface ZoneEventRoundRepository
    extends JpaRepository<ZoneEventRound, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ZoneEventRound> {

  // ... 기존 메서드 유지 ...

  boolean existsByRoundNo(Integer roundNo);
}
```

`ZoneEventRoundSlotRepository.java`에 추가:

```java
  java.util.Optional<ZoneEventRoundSlot> findByRound_IdAndZoneId(UUID roundId, String zoneId);
```

`ZoneEventRepository.java`에 추가:

```java
  List<ZoneEvent> findByZoneIdAndStatusIn(String zoneId, List<ZoneEventStatus> statuses);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundRepository.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundSlotRepository.java src/main/java/com/butingbe/domain/zoneevent/repository/ZoneEventRepository.java src/test/java/com/butingbe/domain/zoneevent/repository/ZoneEventRoundRepositoryTest.java
git commit -m "feat(zoneevent): 회차번호 중복 확인·슬롯 단건 조회·구역 겹침 조회 리포지토리 메서드 추가"
```

---

## Task 4: `RoundTransitionService` 도입 — 자동 전환 로직 단일화

**Files:**
- Create: `src/main/java/com/butingbe/domain/zoneevent/service/RoundTransitionService.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventRoundScheduler.java`
- Test: Create `src/test/java/com/butingbe/domain/zoneevent/service/RoundTransitionServiceTest.java`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventRoundSchedulerTest.java` (상태값 `OPEN`→`ACTIVE`, `savedRound`에 `roundNo` 추가)

**Interfaces:**
- Produces: `RoundTransitionService.sync(ZoneEventRound round, OffsetDateTime now)` — SCHEDULED→ACTIVE(startsAt 도달) 또는 ACTIVE→CLOSED(endsAt 도달)를 멱등하게 반영하고 연결된 `ZoneEvent`들도 함께 전환한다. `void syncAll(OffsetDateTime now)` — 전체 회차 스캔(스케줄러용).
- Consumes: `ZoneEventRoundRepository`, `ZoneEventRoundSlotRepository`, `ZoneEventRepository` (모두 기존).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoundTransitionServiceTest extends AbstractContainerTest {

  @Autowired private RoundTransitionService transitionService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;

  private ZoneEventType type;
  private static int roundNoSeq = 1000;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
  }

  @Test
  @DisplayName("시작 시각이 지난 SCHEDULED 회차는 ACTIVE로, 연결 이벤트도 ACTIVE로 바뀐다")
  void syncActivates() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    ZoneEvent event = savedEvent(ZoneEventStatus.SCHEDULED);
    savedSlot(round, "SUYEONG_NAMGU", event.getId());

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.ACTIVE);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("종료 시각이 지난 ACTIVE 회차는 CLOSED로, 연결 이벤트도 CLOSED로 바뀐다")
  void syncCloses() {
    ZoneEventRound round = savedRound(RoundStatus.ACTIVE, -2, -1);
    ZoneEvent event = savedEvent(ZoneEventStatus.ACTIVE);
    savedSlot(round, "YEONGDO", event.getId());

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.CLOSED);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
  }

  @Test
  @DisplayName("아직 시작 전이면 아무것도 바뀌지 않는다(멱등)")
  void syncNoopWhenNotDue() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, 1, 2);

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("syncAll은 대상 회차 전체를 훑어 전환한다")
  void syncAllScansEverything() {
    savedRound(RoundStatus.SCHEDULED, -1, 1);
    savedRound(RoundStatus.ACTIVE, -2, -1);

    transitionService.syncAll(OffsetDateTime.now());

    assertThat(roundRepository.findAll())
        .extracting(ZoneEventRound::getStatus)
        .containsExactlyInAnyOrder(RoundStatus.ACTIVE, RoundStatus.CLOSED);
  }

  private ZoneEventRound savedRound(RoundStatus status, int startsDaysOffset, int endsDaysOffset) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(roundNoSeq++)
            .startsAt(OffsetDateTime.now().plusDays(startsDaysOffset))
            .endsAt(OffsetDateTime.now().plusDays(endsDaysOffset))
            .status(status)
            .build());
  }

  private ZoneEvent savedEvent(ZoneEventStatus status) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("이벤트")
            .startsAt(OffsetDateTime.now())
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEventRoundSlot savedSlot(ZoneEventRound round, String zoneId, java.util.UUID eventId) {
    return slotRepository.save(
        ZoneEventRoundSlot.builder().round(round).slotKind(SlotKind.AUTH).zoneId(zoneId).eventId(eventId).build());
  }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — `RoundTransitionService` 클래스 없음.

- [ ] **Step 3: `RoundTransitionService` 구현**

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차·슬롯 이벤트의 서버 시간 기준 자동 전환을 한 곳에서 처리한다.
 *
 * <p>스케줄러(주기 실행)와 요청 처리 경로(조회 직전 동기화) 양쪽이 이 서비스를 호출해, Job이 지연돼도 응답 시점엔 항상 최신 상태를 보장한다(멱등).
 */
@Service
@RequiredArgsConstructor
public class RoundTransitionService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventRepository zoneEventRepository;

  /** 주어진 시각 기준으로 이 회차 하나를 필요하면 전환한다. 조건이 안 맞으면 아무 것도 하지 않는다. */
  @Transactional
  public void sync(ZoneEventRound round, OffsetDateTime now) {
    if (round.getStatus() == RoundStatus.SCHEDULED && !round.getStartsAt().isAfter(now)) {
      round.activate();
      transitionSlotEvents(round, ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
    } else if (round.getStatus() == RoundStatus.ACTIVE && !round.getEndsAt().isAfter(now)) {
      round.close();
      transitionSlotEvents(round, ZoneEventStatus.ACTIVE, ZoneEventStatus.CLOSED);
    }
  }

  /** 전체 회차를 훑어 대상이 되는 것만 전환한다(스케줄러 진입점). */
  @Transactional
  public void syncAll(OffsetDateTime now) {
    for (ZoneEventRound round : roundRepository.findByStatusAndStartsAtLessThanEqual(RoundStatus.SCHEDULED, now)) {
      sync(round, now);
    }
    for (ZoneEventRound round : roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.ACTIVE, now)) {
      sync(round, now);
    }
  }

  private void transitionSlotEvents(ZoneEventRound round, ZoneEventStatus from, ZoneEventStatus to) {
    List<UUID> eventIds =
        slotRepository.findByRound_Id(round.getId()).stream()
            .map(ZoneEventRoundSlot::getEventId)
            .filter(id -> id != null)
            .toList();
    if (eventIds.isEmpty()) {
      return;
    }
    for (ZoneEvent event : zoneEventRepository.findAllById(eventIds)) {
      if (event.getStatus() != from) {
        continue;
      }
      if (to == ZoneEventStatus.ACTIVE) {
        event.activate();
      } else {
        event.close();
      }
    }
  }
}
```

- [ ] **Step 4: `RoundTransitionServiceTest` 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.RoundTransitionServiceTest"`
Expected: PASS.

- [ ] **Step 5: `ZoneEventRoundScheduler`가 새 서비스에 위임하도록 재작성**

```java
package com.butingbe.domain.zoneevent.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차를 시각에 맞춰 열고 닫는 주기 작업. 실제 전환 로직은 {@link RoundTransitionService}에 있고, 여기서는 주기 실행만 담당한다.
 *
 * <p>정산(TOP_LIKE, SETTLED 전환)은 이 스케줄러가 아니라 정산 잡(후속 이슈)이 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventRoundScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final RoundTransitionService transitionService;

  @Scheduled(
      fixedDelayString = "${zone-event.round.scheduler.delay-ms:60000}",
      initialDelayString = "${zone-event.round.scheduler.initial-delay-ms:60000}")
  @Transactional
  public void advanceRounds() {
    advance(OffsetDateTime.now(java.time.ZoneId.of(SEOUL_ZONE)));
  }

  /** 주어진 시각 기준으로 회차를 열고 닫는다. 테스트에서 시각을 주입한다. */
  @Transactional
  public void advance(OffsetDateTime now) {
    transitionService.syncAll(now);
  }
}
```

- [ ] **Step 6: `ZoneEventRoundSchedulerTest`를 새 상태명·생성자에 맞게 갱신**

파일 전체에서 `RoundStatus.OPEN`을 `RoundStatus.ACTIVE`로 바꾸고, `savedRound` 헬퍼가 `roundNo`를 함께 채우도록 고친다(엔티티가 이제 `roundNo` 필수이므로):

```java
  private static int roundNoSeq = 2000;

  private ZoneEventRound savedRound(RoundStatus status, int startsDaysOffset, int endsDaysOffset) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(roundNoSeq++)
            .startsAt(OffsetDateTime.now().plusDays(startsDaysOffset))
            .endsAt(OffsetDateTime.now().plusDays(endsDaysOffset))
            .status(status)
            .build());
  }
```

(다른 단언문의 `RoundStatus.OPEN` 참조도 전부 `RoundStatus.ACTIVE`로 바꾼다. `@DisplayName`의 "OPEN" 문구도 "ACTIVE"로 바꾼다.)

- [ ] **Step 7: 전체 스케줄러 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.ZoneEventRoundSchedulerTest"`
Expected: PASS.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/RoundTransitionService.java src/main/java/com/butingbe/domain/zoneevent/service/ZoneEventRoundScheduler.java src/test/java/com/butingbe/domain/zoneevent/service/RoundTransitionServiceTest.java src/test/java/com/butingbe/domain/zoneevent/service/ZoneEventRoundSchedulerTest.java
git commit -m "refactor(zoneevent): 회차 자동 전환 로직을 RoundTransitionService로 일원화"
```

---

## Task 5: `RoundStatusQueryService` — 요청 시점 동기화 + ACTIVE 명칭 반영

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/RoundStatusQueryService.java`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/service/RoundStatusQueryServiceTest.java`

**Interfaces:**
- Consumes: `RoundTransitionService.sync(round, now)` (Task 4)

- [ ] **Step 1: 기존 테스트에서 `RoundStatus.OPEN`을 쓰는 부분을 `RoundStatus.ACTIVE`로, "OPEN" 라벨 문자열을 "ACTIVE"로 바꾸고, roundNo를 채우도록 수정**

`RoundStatusQueryServiceTest.java`를 열어 회차 생성 헬퍼에 `roundNo(...)`를 추가하고, `current().status()` 또는 응답 문자열 단언에서 `"OPEN"`을 기대하던 부분을 `"ACTIVE"`로 바꾼다. 스케줄러 지연 시나리오(시작 시각은 지났지만 아직 DB엔 `SCHEDULED`로 남아있는 회차)에 대해 아래 테스트를 추가한다:

```java
  @Test
  @DisplayName("시작 시각이 지났지만 아직 SCHEDULED인 회차도 조회 시점에 ACTIVE로 동기화된다")
  void syncsOnRead() {
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(999)
                .startsAt(OffsetDateTime.now().minusMinutes(5))
                .endsAt(OffsetDateTime.now().plusHours(1))
                .status(RoundStatus.SCHEDULED)
                .build());

    RoundStatusResDto result = queryService.current();

    assertThat(result.roundId()).isEqualTo(round.getId().toString());
    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.ACTIVE);
  }
```

(파일 상단 필드명이 `queryService`가 아니라면 기존 필드명을 그대로 쓴다 — Read로 먼저 확인.)

- [ ] **Step 2: 컴파일/실행해 실패 확인 (동기화 로직이 아직 없으므로 회차가 SCHEDULED로 남아 실패)**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.RoundStatusQueryServiceTest"`
Expected: FAIL (새 테스트에서 상태가 여전히 SCHEDULED).

- [ ] **Step 3: 서비스에 동기화 호출 추가**

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.dto.response.RoundStatusResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 회차 현황(FR-EVT-03). 지금 열린 회차가 있으면 6구역을 ACTIVE/REST로, 없으면 다음 예정 회차의 구역을 UPCOMING으로 보여준다. 조회 전에
 * 후보 회차를 {@link RoundTransitionService}로 동기화해 스케줄러 지연에도 최신 상태를 반영한다.
 */
@Service
@RequiredArgsConstructor
public class RoundStatusQueryService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final RoundTransitionService transitionService;

  @Transactional
  public RoundStatusResDto current() {
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    roundRepository
        .findFirstByStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(RoundStatus.SCHEDULED, now.minusDays(2))
        .ifPresent(round -> transitionService.sync(round, now));
    roundRepository.findFirstByStatusOrderByStartsAtDesc(RoundStatus.ACTIVE).ifPresent(round -> transitionService.sync(round, now));

    return roundRepository
        .findFirstByStatusOrderByStartsAtDesc(RoundStatus.ACTIVE)
        .map(round -> statusOf(round, "OPEN"))
        .or(
            () ->
                roundRepository
                    .findFirstByStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(RoundStatus.SCHEDULED, now)
                    .map(round -> statusOf(round, "UPCOMING")))
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private RoundStatusResDto statusOf(ZoneEventRound round, String openLabel) {
    Map<String, ZoneEventRoundSlot> slotsByZone =
        slotRepository.findByRound_Id(round.getId()).stream()
            .collect(Collectors.toMap(ZoneEventRoundSlot::getZoneId, Function.identity(), (a, b) -> a));
    List<RoundStatusResDto.ZoneSlot> zones =
        java.util.Arrays.stream(ChatZone.values())
            .map(
                zone -> {
                  ZoneEventRoundSlot slot = slotsByZone.get(zone.name());
                  if (slot == null) {
                    return new RoundStatusResDto.ZoneSlot(zone.name(), "REST", null);
                  }
                  UUID eventId = slot.getEventId();
                  return new RoundStatusResDto.ZoneSlot(zone.name(), openLabel, eventId == null ? null : eventId.toString());
                })
            .toList();
    return new RoundStatusResDto(
        round.getId().toString(), round.getStatus(), round.getStartsAt(), round.getEndsAt(), zones);
  }
}
```

주의: "시작 시각이 지났지만 아직 SCHEDULED로 DB에 남아있는 회차"를 찾으려면 `findFirstByStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc`만으로는 부족하다(그 메서드는 `startsAt >= now`인 것만 찾음). 이 태스크에서 실제로 필요한 건 "SCHEDULED이면서 startsAt이 과거이거나 가까운 미래인 것"을 찾아 동기화하는 것이므로, `ZoneEventRoundRepository`에 아래 메서드를 추가하고 위 구현에서 그것을 쓴다:

```java
  List<ZoneEventRound> findByStatusAndStartsAtLessThanEqual(RoundStatus status, OffsetDateTime at); // 이미 존재함(Task 없이 재사용)
```

이미 `ZoneEventRoundRepository`에 `findByStatusAndStartsAtLessThanEqual`이 있으므로(스케줄러가 쓰던 것), `current()` 맨 앞에서 아래처럼 동기화한다(위 임시 구현을 이 버전으로 교체):

```java
  @Transactional
  public RoundStatusResDto current() {
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    for (ZoneEventRound due : roundRepository.findByStatusAndStartsAtLessThanEqual(RoundStatus.SCHEDULED, now)) {
      transitionService.sync(due, now);
    }
    for (ZoneEventRound due : roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.ACTIVE, now)) {
      transitionService.sync(due, now);
    }

    return roundRepository
        .findFirstByStatusOrderByStartsAtDesc(RoundStatus.ACTIVE)
        .map(round -> statusOf(round, "OPEN"))
        .or(
            () ->
                roundRepository
                    .findFirstByStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(RoundStatus.SCHEDULED, now)
                    .map(round -> statusOf(round, "UPCOMING")))
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }
```

(위의 "주의" 블록이 최종 구현이다 — Step 3의 첫 코드 블록 대신 이 버전을 적용한다.)

주의: `statusOf(round, "OPEN")`의 `"OPEN"`은 `RoundStatus` enum이 아니라 API 응답의 구역별 `slotStatus` 표시 라벨이다(`"OPEN"`/`"REST"`/`"UPCOMING"`). `RoundStatus.OPEN`이 `RoundStatus.ACTIVE`로 이름이 바뀐 것과는 무관하니 이 문자열 리터럴은 그대로 `"OPEN"`으로 둔다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.RoundStatusQueryServiceTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/RoundStatusQueryService.java src/test/java/com/butingbe/domain/zoneevent/service/RoundStatusQueryServiceTest.java
git commit -m "fix(zoneevent): 유저 회차 현황 조회 시점에도 자동 전환을 동기화(Job 지연 방어)"
```

---

## Task 6: 회차 DTO 재작성 (`RoundCreateReqDto`, 신규 `RoundPatchReqDto`/`RoundCancelReqDto`, `AdminRoundResDto`)

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundCreateReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundPatchReqDto.java`
- Create: `src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundCancelReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminRoundResDto.java`

**Interfaces:**
- Produces: `RoundCreateReqDto(RoundType roundType, @NotNull Integer roundNo, String name, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt, String timezone, RewardSnapshotReqDto excellenceReward)`. `RoundPatchReqDto(@NotNull Long expectedRevision, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, RoundType roundType)`. `RoundCancelReqDto(@NotBlank String reason, @NotNull Long expectedRevision)`. `AdminRoundResDto`에 `revision, cancelReason, excellenceReward, settled` 필드와 슬롯별 집계(`participantCount, successCount, underReviewCount`) 추가.

- [ ] **Step 1: `RoundCreateReqDto` 교체**

```java
package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RoundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** 회차 초안(DRAFT) 생성. 구역 슬롯은 이후 POST /admin/zone-events로 개별 추가한다. */
public record RoundCreateReqDto(
    RoundType roundType,
    @NotNull Integer roundNo,
    String name,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime endsAt,
    String timezone,
    @Valid RewardSnapshotReqDto excellenceReward) {}
```

- [ ] **Step 2: `RoundPatchReqDto` 신규 작성**

```java
package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RoundType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** 회차 메타데이터 수정. null 필드는 변경하지 않는다. DRAFT/SCHEDULED 상태에서만 허용된다. */
public record RoundPatchReqDto(
    @NotNull Long expectedRevision,
    String name,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String timezone,
    RoundType roundType) {}
```

- [ ] **Step 3: `RoundCancelReqDto` 신규 작성**

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 회차 긴급 취소. 연결된 구역 슬롯도 함께 취소되지만 기존 참여·검수·보상 이력은 유지된다. */
public record RoundCancelReqDto(@NotBlank String reason, @NotNull Long expectedRevision) {}
```

- [ ] **Step 4: `AdminRoundResDto` 교체**

```java
package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import java.time.OffsetDateTime;
import java.util.List;

/** 운영 회차 상세: 회차 + 슬롯(집계 포함) + 예비 타겟. */
public record AdminRoundResDto(
    String roundId,
    Integer roundNo,
    String name,
    RoundType roundType,
    RoundStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String timezone,
    OffsetDateTime closedAt,
    OffsetDateTime settledAt,
    boolean settled,
    String cancelReason,
    RewardSnapshot excellenceReward,
    Long revision,
    List<Slot> slots,
    List<Backup> backups) {

  public record Slot(
      String slotId,
      String slotKind,
      String zoneId,
      String eventId,
      long participantCount,
      long successCount,
      long underReviewCount) {}

  public record Backup(String targetId, String placeName, Double latitude, Double longitude) {}

  public static AdminRoundResDto of(
      ZoneEventRound round,
      List<ZoneEventRoundSlot> slots,
      List<ZoneEventBackupTarget> backups,
      java.util.Map<String, long[]> countsByEventId) {
    return new AdminRoundResDto(
        round.getId().toString(),
        round.getRoundNo(),
        round.getName(),
        round.getRoundType(),
        round.getStatus(),
        round.getStartsAt(),
        round.getEndsAt(),
        round.getTimezone(),
        round.getClosedAt(),
        round.getSettledAt(),
        round.getStatus() == RoundStatus.SETTLED,
        round.getCancelReason(),
        round.getExcellenceReward(),
        round.getRevision(),
        slots.stream()
            .map(
                s -> {
                  String eventId = s.getEventId() == null ? null : s.getEventId().toString();
                  long[] counts = eventId == null ? new long[] {0, 0, 0} : countsByEventId.getOrDefault(eventId, new long[] {0, 0, 0});
                  return new Slot(
                      s.getId().toString(), s.getSlotKind().name(), s.getZoneId(), eventId, counts[0], counts[1], counts[2]);
                })
            .toList(),
        backups.stream()
            .map(b -> new Backup(b.getId().toString(), b.getPlaceName(), b.getLatitude(), b.getLongitude()))
            .toList());
  }
}
```

`countsByEventId`는 `eventId(String) -> [참여수, 성공수, 검수대기수]` 맵으로, 이를 채우는 책임은 Task 8의 `AdminRoundConsoleService.detailOf()`가 진다(이 태스크에서는 DTO 계약만 정의).

- [ ] **Step 5: 컴파일 확인(아직 참조하는 서비스/테스트가 안 고쳐졌으므로 에러가 나는 게 정상)**

Run: `gradlew.bat compileJava`
Expected: FAIL — `AdminRoundResDto.of(...)` 호출부(`AdminRoundConsoleService`)와 `RoundCreateReqDto` 생성자 호출부(테스트들)가 옛 시그니처를 쓰고 있어 컴파일 에러. 이 에러들은 Task 7~9에서 해소한다. 지금은 새 DTO 파일 자체에 문법 오류가 없는지만 `javac`로 눈으로 확인한다.

- [ ] **Step 6: 커밋 (컴파일이 깨진 중간 상태이므로 다음 태스크와 함께 묶어도 되지만, 리뷰 가능성을 위해 우선 분리 커밋)**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundCreateReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundPatchReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/request/RoundCancelReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminRoundResDto.java
git commit -m "feat(zoneevent): 회차 생성/수정/취소 DTO와 상세 응답에 상태·집계 필드 추가 (WIP, 다음 커밋에서 컴파일 복구)"
```

---

## Task 7: `AdminZoneEventService`에 회차 슬롯 연동 + 겹침/4구역 검증 + 슬롯코드 발급 + 보상 상속

이 태스크를 Task 6 직후, Task 8(라운드 콘솔 서비스) 이전에 두는 이유: `AdminRoundConsoleService.schedule()`이 "정확히 4개 구역"을 검증하려면 `AdminZoneEventService.create()`가 먼저 `ZoneEventRoundSlot`을 올바르게 채워야 한다.

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventService.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminZoneEventUpdateReqDto.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventResDto.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventServiceTest.java`

**Interfaces:**
- Consumes: `ZoneEventRoundRepository.findById`, `ZoneEventRoundSlotRepository.findByRound_IdAndZoneId/save`, `ZoneEventRepository.findByRoundId/findByZoneIdAndStatusIn`(Task 3)
- Produces: `AdminZoneEventResDto`에 `slotCode`, `revision` 필드 추가. `AdminZoneEventUpdateReqDto`에 `reason`(선택), `expectedRevision`(`@NotNull Long`) 필드 추가.

- [ ] **Step 1: 먼저 `AdminZoneEventServiceTest`를 열어 기존 구조·헬퍼(특히 회차 생성 헬퍼, `type` 필드)를 확인한다.** (Read 도구로 전체 파일을 읽을 것 — 이 플랜에서 전체를 재현하지 않는다.)

- [ ] **Step 2: 실패하는 테스트 추가** (기존 파일의 `@BeforeEach`/헬퍼 이름에 맞춰 아래를 이식— `roundRepository`가 이미 주입돼 있지 않다면 필드로 추가)

```java
  @Autowired private com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository roundRepository;
  @Autowired private com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository slotRepository;

  private ZoneEventRound draftRound(int roundNo) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(roundNo)
            .startsAt(OffsetDateTime.now())
            .endsAt(OffsetDateTime.now().plusDays(1))
            .excellenceReward(new RewardSnapshot(null, null, 3, "COUPON_CAFE"))
            .build());
  }

  @Test
  @DisplayName("회차에 슬롯을 만들면 slotCode가 {roundNo}-A부터 순서대로 발급되고 슬롯이 연결된다")
  void createAssignsSlotCodeAndLinksSlot() {
    ZoneEventRound round = draftRound(11);

    AdminZoneEventResDto first = service.create(operator, createReq(round.getId(), "YEONGDO"));
    AdminZoneEventResDto second = service.create(operator, createReq(round.getId(), "OLD_DOWNTOWN"));

    assertThat(first.slotCode()).isEqualTo("11-A");
    assertThat(second.slotCode()).isEqualTo("11-B");
    assertThat(slotRepository.findByRound_IdAndZoneId(round.getId(), "YEONGDO"))
        .isPresent()
        .get()
        .extracting(s -> s.getEventId().toString())
        .isEqualTo(first.eventId());
  }

  @Test
  @DisplayName("같은 회차에 같은 구역을 두 번 넣으면 409")
  void duplicateZoneInRoundConflicts() {
    ZoneEventRound round = draftRound(12);
    service.create(operator, createReq(round.getId(), "YEONGDO"));

    assertThatThrownBy(() -> service.create(operator, createReq(round.getId(), "YEONGDO")))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("한 회차에 5번째 구역을 넣으면 409")
  void fifthZoneInRoundConflicts() {
    ZoneEventRound round = draftRound(13);
    service.create(operator, createReq(round.getId(), "YEONGDO"));
    service.create(operator, createReq(round.getId(), "OLD_DOWNTOWN"));
    service.create(operator, createReq(round.getId(), "SUYEONG_NAMGU"));
    service.create(operator, createReq(round.getId(), "WESTERN_BUSAN"));

    assertThatThrownBy(() -> service.create(operator, createReq(round.getId(), "CENTRAL_NORTH")))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("같은 구역·겹치는 시간대에 SCHEDULED/ACTIVE 이벤트가 있으면 409")
  void overlappingZoneTimeConflicts() {
    OffsetDateTime start = OffsetDateTime.now().plusDays(5);
    service.create(
        operator,
        new AdminZoneEventCreateReqDto(
            "YEONGDO", type.getTypeCode(), "1차", null, start, 120, null, 1,
            new RewardSnapshotReqDto(50, null, null, null), null, null));

    AdminZoneEventCreateReqDto overlapping =
        new AdminZoneEventCreateReqDto(
            "YEONGDO", type.getTypeCode(), "2차", null, start.plusMinutes(60), 120, null, 1,
            new RewardSnapshotReqDto(50, null, null, null), null, null);

    assertThatThrownBy(() -> service.create(operator, overlapping))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("excellenceReward를 안 넘기면 회차 기본값을 물려받는다")
  void inheritsRoundExcellenceReward() {
    ZoneEventRound round = draftRound(14);

    AdminZoneEventResDto created = service.create(operator, createReq(round.getId(), "YEONGDO"));

    assertThat(created.excellenceReward().topN()).isEqualTo(3);
    assertThat(created.excellenceReward().prizeRewardCode()).isEqualTo("COUPON_CAFE");
  }

  @Test
  @DisplayName("expectedRevision이 다르면 수정 시 409")
  void updateWithStaleRevisionConflicts() {
    ZoneEventRound round = draftRound(15);
    AdminZoneEventResDto created = service.create(operator, createReq(round.getId(), "YEONGDO"));

    AdminZoneEventUpdateReqDto staleUpdate =
        new AdminZoneEventUpdateReqDto(
            "새 제목", null, null, null, null, null, null, null, null, null, "사유", created.revision() + 1);

    assertThatThrownBy(
            () -> service.update(operator, UUID.fromString(created.eventId()), staleUpdate))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  private AdminZoneEventCreateReqDto createReq(java.util.UUID roundId, String zoneId) {
    return new AdminZoneEventCreateReqDto(
        zoneId, type.getTypeCode(), "미션", null, OffsetDateTime.now().plusDays(1), 120, roundId, 1,
        new RewardSnapshotReqDto(50, null, null, null), null, null);
  }
```

(테스트 파일에 이미 `type`, `operator`, `service`라는 이름의 필드가 없다면 실제 파일의 이름으로 바꿔 쓴다 — Step 1에서 확인한 이름을 그대로 쓸 것. `AdminZoneEventUpdateReqDto` 생성자 인자 순서는 Step 4의 최종 정의를 따른다.)

- [ ] **Step 3: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — `slotCode()`, `revision()`, `expectedRevision` 필드/파라미터가 아직 없음.

- [ ] **Step 4: `AdminZoneEventUpdateReqDto`에 `reason`, `expectedRevision` 추가**

```java
package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 이벤트 부분 수정. null 필드는 변경하지 않는다.
 *
 * <p>{@code zoneId/typeCode/startsAt/baseReward}는 SCHEDULED 상태에서만 바꿀 수 있고, ACTIVE에서 시도하면 409다.
 * {@code expectedRevision}이 현재 값과 다르면 409다.
 */
public record AdminZoneEventUpdateReqDto(
    String title,
    String description,
    Integer durationMinutes,
    Integer successLimitPerUser,
    RewardSnapshotReqDto excellenceReward,
    String zoneId,
    String typeCode,
    OffsetDateTime startsAt,
    RewardSnapshotReqDto baseReward,
    AuthTargetPatchReqDto authTarget,
    String reason,
    @NotNull Long expectedRevision) {

  /** ACTIVE에서 바꿀 수 없는 필드가 요청에 들어 있는지. */
  public boolean touchesScheduledOnlyFields() {
    return zoneId != null || typeCode != null || startsAt != null || baseReward != null;
  }

  /** 시간·구역이 바뀌어 겹침 재검증이 필요한지. */
  public boolean touchesTimeOrZone() {
    return zoneId != null || startsAt != null || durationMinutes != null;
  }

  public record AuthTargetPatchReqDto(
      String placeName, String guideText, String exampleFileKey, Double latitude, Double longitude, Integer radiusM) {}
}
```

- [ ] **Step 5: `AdminZoneEventResDto`에 `slotCode`, `revision` 추가**

`record` 선언에 `String slotCode, Long revision`을 필드 목록 끝에 추가하고, `of(...)` 팩토리에서 `event.getSlotCode(), event.getRevision()`을 넘긴다:

```java
public record AdminZoneEventResDto(
    String eventId,
    String zoneId,
    String typeCode,
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer durationMinutes,
    String status,
    UUID roundId,
    Integer successLimitPerUser,
    RewardSnapshot baseReward,
    RewardSnapshot excellenceReward,
    AdminAuthTarget authTarget,
    long joinedCount,
    long successCount,
    String slotCode,
    Long revision) {

  // AdminAuthTarget 레코드는 그대로 유지

  public static AdminZoneEventResDto of(
      ZoneEvent event, ZoneEventAuthTarget target, long joinedCount, long successCount) {
    return new AdminZoneEventResDto(
        event.getId().toString(),
        event.getZoneId(),
        event.getType().getTypeCode(),
        event.getTitle(),
        event.getDescription(),
        event.getStartsAt(),
        event.endsAt(),
        event.getDurationMinutes(),
        event.getStatus().name(),
        event.getRoundId(),
        event.getSuccessLimitPerUser(),
        event.getBaseReward(),
        event.getExcellenceReward(),
        AdminAuthTarget.from(target),
        joinedCount,
        successCount,
        event.getSlotCode(),
        event.getRevision());
  }
}
```

- [ ] **Step 6: `AdminZoneEventService.create()`/`update()` 재작성**

`create()` 메서드를 아래 로직으로 교체(클래스 상단에 `ZoneEventRoundRepository roundRepository`, `ZoneEventRoundSlotRepository slotRepository` 필드 주입 추가):

```java
  private static final List<Character> SLOT_LETTERS = List.of('A', 'B', 'C', 'D');

  @Transactional
  public AdminZoneEventResDto create(AuthenticatedUser user, AdminZoneEventCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    String zoneId = parseZone(request.zoneId());
    ZoneEventType type = requireType(request.typeCode());
    validateRewardCodes(request.baseReward(), request.excellenceReward());

    OffsetDateTime endsAt = request.startsAt().plusMinutes(request.durationMinutes());
    requireNoOverlap(zoneId, request.startsAt(), endsAt, null);

    ZoneEventRound round = null;
    String slotCode = null;
    if (request.roundId() != null) {
      round =
          roundRepository
              .findById(request.roundId())
              .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
      if (round.getStatus() != com.butingbe.domain.zoneevent.entity.RoundStatus.DRAFT) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      if (slotRepository.findByRound_IdAndZoneId(round.getId(), zoneId).isPresent()) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      List<ZoneEvent> existing = zoneEventRepository.findByRoundId(round.getId());
      if (existing.size() >= 4) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      slotCode = round.getRoundNo() + "-" + SLOT_LETTERS.get(existing.size());
    }

    RewardSnapshotReqDto excellence =
        request.excellenceReward() != null
            ? request.excellenceReward()
            : (round != null && round.getExcellenceReward() != null
                ? toReqDto(round.getExcellenceReward())
                : null);

    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .roundId(request.roundId())
                .slotCode(slotCode)
                .title(request.title())
                .description(request.description())
                .startsAt(request.startsAt())
                .durationMinutes(request.durationMinutes())
                .status(ZoneEventStatus.SCHEDULED)
                .baseReward(request.baseReward().toSnapshot())
                .excellenceReward(excellence == null ? null : excellence.toSnapshot())
                .successLimitPerUser(request.successLimitPerUser())
                .build());

    if (round != null) {
      slotRepository
          .save(
              com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot.builder()
                  .round(round)
                  .slotKind(com.butingbe.domain.zoneevent.entity.SlotKind.AUTH)
                  .zoneId(zoneId)
                  .build())
          .assignEvent(event.getId());
    }

    ZoneEventAuthTarget target = null;
    if (Boolean.TRUE.equals(type.getRequiresUpload())) {
      if (request.authTarget() == null) {
        throw new IllegalArgumentException("error.zone_event.media.invalid");
      }
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    } else if (request.authTarget() != null) {
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    }
    return AdminZoneEventResDto.of(event, target, 0, 0);
  }

  private RewardSnapshotReqDto toReqDto(RewardSnapshot snapshot) {
    return new RewardSnapshotReqDto(
        snapshot.points(), snapshot.badgeCode(), snapshot.topN(), snapshot.prizeRewardCode());
  }

  private void requireNoOverlap(String zoneId, OffsetDateTime startsAt, OffsetDateTime endsAt, UUID excludeEventId) {
    List<ZoneEventStatus> blocking = List.of(ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
    for (ZoneEvent existing : zoneEventRepository.findByZoneIdAndStatusIn(zoneId, blocking)) {
      if (excludeEventId != null && existing.getId().equals(excludeEventId)) {
        continue;
      }
      boolean overlaps = existing.getStartsAt().isBefore(endsAt) && startsAt.isBefore(existing.endsAt());
      if (overlaps) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
    }
  }
```

`update()` 메서드 맨 앞부분(operator 체크 다음)에 revision 체크와 시간/구역 겹침 재검증을 추가:

```java
  @Transactional
  public AdminZoneEventResDto update(
      AuthenticatedUser user, UUID eventId, AdminZoneEventUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);

    if (!event.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (event.getStatus() == ZoneEventStatus.ACTIVE && request.touchesScheduledOnlyFields()) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (request.touchesTimeOrZone()) {
      String zoneId = request.zoneId() == null ? event.getZoneId() : parseZone(request.zoneId());
      OffsetDateTime startsAt = request.startsAt() == null ? event.getStartsAt() : request.startsAt();
      int duration = request.durationMinutes() == null ? event.getDurationMinutes() : request.durationMinutes();
      requireNoOverlap(zoneId, startsAt, startsAt.plusMinutes(duration), event.getId());
    }

    // ... 기존 로직(applyEditable 이하) 그대로 ...
```

(이 아래 기존 본문은 그대로 두되, `zoneId` 파싱하는 줄이 중복되지 않도록 기존 `String zoneId = request.zoneId() == null ? null : parseZone(request.zoneId());` 줄과 겹치지 않게 주의 — 겹침 검증용 지역변수는 별도 스코프에 두거나 이름을 `overlapZoneId`처럼 바꿔서 기존 변수와 충돌을 피한다.)

- [ ] **Step 7: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.AdminZoneEventServiceTest"`
Expected: PASS (기존 테스트 포함 전체).

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventService.java src/main/java/com/butingbe/domain/zoneevent/dto/request/AdminZoneEventUpdateReqDto.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventResDto.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventServiceTest.java
git commit -m "feat(zoneevent): 구역 슬롯 생성 시 회차 슬롯 연동·4구역 제한·시간 겹침 방지·slotCode 자동 발급·보상 상속"
```

---

## Task 8: `AdminZoneEventController` — activate/close 제거, 목록 page/size+roundId로 전환

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventController.java`
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventService.java` (activate/close 제거, list 재작성)
- Modify: `src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventPageResDto.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventControllerTest.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventServiceTest.java` (list 관련 테스트 갱신, activate/close 테스트 삭제)

**Interfaces:**
- Produces: `AdminZoneEventPageResDto(List<AdminZoneEventResDto> items, int page, int size, long totalElements, int totalPages)`. `AdminZoneEventService.list(user, roundId, zone, status, from, to, page, size)`.

- [ ] **Step 1: `AdminZoneEventControllerTest`를 열어 기존 activate/close 테스트와 list 테스트를 확인한다.** (Read로 전체 확인.)

- [ ] **Step 2: `AdminZoneEventPageResDto` 교체**

```java
package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 운영 이벤트 목록 페이지 응답. */
public record AdminZoneEventPageResDto(
    List<AdminZoneEventResDto> items, int page, int size, long totalElements, int totalPages) {}
```

- [ ] **Step 3: 컨트롤러 테스트에서 `activate`/`close` 관련 테스트 삭제, `list` 테스트를 page/size+roundId로 교체**

기존 `activate`/`close` 관련 `@Test` 메서드를 삭제한다. 목록 테스트를 아래처럼 바꾼다:

```java
  @Test
  @DisplayName("이벤트 목록 200 (roundId/page/size)")
  void list() throws Exception {
    when(adminZoneEventService.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AdminZoneEventPageResDto(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/admin/zone-events")
                .param("roundId", "44444444-0000-0000-0000-000000000001")
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0));
  }
```

(파일 상단 정적 임포트에 `anyInt`가 없으면 추가한다. `activate`/`close`를 호출하던 MockMvc `post(".../activate")` 등의 테스트 메서드는 전부 삭제한다.)

- [ ] **Step 4: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — 새 `list` 시그니처와 `AdminZoneEventPageResDto` 필드가 아직 서비스/컨트롤러에 없음.

- [ ] **Step 5: `AdminZoneEventService`에서 `activate()`/`close()` 삭제, `list()` 재작성**

`activate`, `close` 메서드 전체를 삭제한다. `list`를 아래로 교체(클래스 상단 `import` 정리 — `Base64`, `Cursor` record, `encodeCursor`, `decodeCursor` 관련 코드도 이제 안 쓰이면 삭제):

```java
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  @Transactional(readOnly = true)
  public AdminZoneEventPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      String zone,
      String status,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    String zoneId = zone == null || zone.isBlank() ? null : parseZone(zone);
    ZoneEventStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    int pageNumber = page == null || page < 0 ? 0 : page;

    Specification<ZoneEvent> spec = buildListSpec(roundId, zoneId, statusFilter, from, to);
    org.springframework.data.domain.Page<ZoneEvent> result =
        zoneEventRepository.findAll(
            spec,
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"))));

    List<AdminZoneEventResDto> items = result.getContent().stream().map(this::toDetail).toList();
    return new AdminZoneEventPageResDto(items, pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
  }
```

`buildListSpec`을 `cursor` 파라미터 없이 `roundId` 필터를 추가하는 버전으로 교체:

```java
  private Specification<ZoneEvent> buildListSpec(
      UUID roundId, String zoneId, ZoneEventStatus status, OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (roundId != null) {
        predicates.add(cb.equal(root.get("roundId"), roundId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("zoneId"), zoneId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
```

`Cursor` record, `encodeCursor`, `decodeCursor`, 관련 `Base64`/`StandardCharsets` import는 삭제한다.

- [ ] **Step 6: 컨트롤러에서 `/activate`, `/close` 엔드포인트 삭제, `list`를 page/size+roundId로 교체**

```java
  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "이벤트 목록 조회", adminZoneEventService.list(user, roundId, zone, status, from, to, page, size)));
  }
```

`activate`/`close` `@PostMapping` 메서드 두 개를 삭제한다.

- [ ] **Step 7: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.controller.AdminZoneEventControllerTest" --tests "com.butingbe.domain.zoneevent.service.AdminZoneEventServiceTest"`
Expected: PASS.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventController.java src/main/java/com/butingbe/domain/zoneevent/service/AdminZoneEventService.java src/main/java/com/butingbe/domain/zoneevent/dto/response/AdminZoneEventPageResDto.java src/test/java/com/butingbe/domain/zoneevent/controller/AdminZoneEventControllerTest.java src/test/java/com/butingbe/domain/zoneevent/service/AdminZoneEventServiceTest.java
git commit -m "refactor(zoneevent): 이벤트 목록을 page/size+roundId로 전환하고 수동 activate/close 엔드포인트 제거"
```

---

## Task 9: `AdminRoundConsoleService` 재작성 — 초안 생성·목록·상세·PATCH·schedule·cancel, open/close 삭제

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/service/AdminRoundConsoleService.java`
- Test: `src/test/java/com/butingbe/domain/zoneevent/service/AdminRoundConsoleServiceTest.java`

**Interfaces:**
- Consumes: `RoundTransitionService.sync`(Task 4), `ZoneEventRoundRepository.existsByRoundNo`(Task 3), `ZoneEventAuthTargetRepository.findFirstByEvent_IdAndStatusOrderByCreatedAtAsc`(기존)
- Produces: `AdminRoundConsoleService.createRound/listRounds(반환:Page)/roundDetail/patch/schedule/cancel/reassignSlot/addBackupTarget/swapTarget/settle/settlementReport`. `open()`/`close()` 삭제.

- [ ] **Step 1: 기존 `AdminRoundConsoleServiceTest`를 아래 내용으로 전체 교체**

```java
package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RewardSnapshotReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SlotReassignReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminRoundConsoleServiceTest extends AbstractContainerTest {

  @Autowired private AdminRoundConsoleService consoleService;
  @Autowired private AdminZoneEventService adminZoneEventService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventSettlementReportRepository settlementReportRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private ZoneEventType type;
  private static int roundNoSeq = 3000;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    operator =
        new AuthenticatedUser(
            savedUser().getId(), "op@example.com", "op", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    normalUser = AuthenticatedUser.from(savedUser());
  }

  @Test
  @DisplayName("회차를 생성하면 DRAFT 상태이고 감사 로그가 남는다")
  void createRound() {
    AdminRoundResDto round =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null, nextRoundNo(), "부산 바다 인증의 날", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), "Asia/Seoul", null));

    assertThat(round.status()).isEqualTo(RoundStatus.DRAFT);
    assertThat(round.slots()).isEmpty();
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("ROUND", UUID.fromString(round.roundId())))
        .anyMatch(a -> a.getAction().equals("CREATE_ROUND"));
  }

  @Test
  @DisplayName("회차 번호가 중복되면 409")
  void duplicateRoundNoConflicts() {
    int roundNo = nextRoundNo();
    consoleService.createRound(
        operator, new RoundCreateReqDto(null, roundNo, "1회차", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null, null));

    assertThatThrownBy(
            () ->
                consoleService.createRound(
                    operator,
                    new RoundCreateReqDto(null, roundNo, "중복", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null, null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("운영자가 아니면 생성·조회는 403이다")
  void forbidden() {
    RoundCreateReqDto req =
        new RoundCreateReqDto(null, nextRoundNo(), null, OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null, null);
    assertThatThrownBy(() -> consoleService.createRound(normalUser, req)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("목록 조회는 status/from/to/keyword로 필터링되고 시작 시각이 지난 회차는 조회 중 자동 전환된다")
  void listRoundsFiltersAndSyncs() {
    int dueRoundNo = nextRoundNo();
    roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(dueRoundNo)
            .name("자동전환 대상")
            .startsAt(OffsetDateTime.now().minusMinutes(5))
            .endsAt(OffsetDateTime.now().plusHours(1))
            .status(RoundStatus.SCHEDULED)
            .build());
    createDraft();

    List<AdminRoundResDto> draftOnly =
        consoleService.listRounds(operator, "DRAFT", null, null, null, 0, 20);
    assertThat(draftOnly).allMatch(r -> r.status() == RoundStatus.DRAFT);

    List<AdminRoundResDto> keywordMatch =
        consoleService.listRounds(operator, null, null, null, "자동전환", 0, 20);
    assertThat(keywordMatch).hasSize(1);
    assertThat(roundRepository.findAll())
        .filteredOn(r -> r.getRoundNo().equals(dueRoundNo))
        .extracting(ZoneEventRound::getStatus)
        .containsExactly(RoundStatus.ACTIVE);
  }

  @Test
  @DisplayName("4개 서로 다른 구역 + 활성 타겟이 갖춰지면 schedule로 확정된다")
  void scheduleSucceedsWithFourZonesAndActiveTargets() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    createFourZoneEventsWithTargets(roundId);

    AdminRoundResDto scheduled = consoleService.schedule(operator, roundId);

    assertThat(scheduled.status()).isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("구역이 4개 미만이면 schedule은 400 계열 예외다")
  void scheduleFailsWithFewerThanFourZones() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    adminZoneEventService.create(operator, zoneEventReq(roundId, "YEONGDO"));

    assertThatThrownBy(() -> consoleService.schedule(operator, roundId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("구역은 4개지만 활성 타겟이 없는 슬롯이 있으면 schedule은 400 계열 예외다")
  void scheduleFailsWithoutActiveTarget() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    for (String zone : List.of("YEONGDO", "OLD_DOWNTOWN", "SUYEONG_NAMGU")) {
      ZoneEvent event =
          zoneEventRepository.findById(
                  UUID.fromString(adminZoneEventService.create(operator, zoneEventReq(roundId, zone)).eventId()))
              .orElseThrow();
      authTargetRepository.save(
          ZoneEventAuthTarget.builder()
              .event(event)
              .targetKind(ZoneEventTargetKind.PLACE)
              .placeName("장소")
              .latitude(35.1)
              .longitude(129.1)
              .radiusM(100)
              .build());
    }
    adminZoneEventService.create(operator, zoneEventReq(roundId, "WESTERN_BUSAN")); // 타겟 없음

    assertThatThrownBy(() -> consoleService.schedule(operator, roundId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("PATCH는 expectedRevision이 다르면 409, DRAFT/SCHEDULED에서만 허용된다")
  void patchRequiresMatchingRevisionAndAllowedState() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());

    assertThatThrownBy(
            () ->
                consoleService.patch(
                    operator, roundId, new RoundPatchReqDto(created.revision() + 1, "새 이름", null, null, null, null)))
        .isInstanceOf(ConflictException.class);

    AdminRoundResDto patched =
        consoleService.patch(operator, roundId, new RoundPatchReqDto(created.revision(), "새 이름", null, null, null, null));
    assertThat(patched.name()).isEqualTo("새 이름");
  }

  @Test
  @DisplayName("cancel은 이력을 유지한 채 회차와 슬롯을 CANCELLED로 만든다")
  void cancelPreservesHistory() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    AdminRoundResDto afterCreate = adminZoneEventServiceCreateAndReturnRound(roundId, "YEONGDO");
    UUID eventId = UUID.fromString(afterCreate.slots().get(0).eventId());
    ZoneEventParticipation joined = joined(zoneEventRepository.findById(eventId).orElseThrow());

    AdminRoundResDto cancelled =
        consoleService.cancel(operator, roundId, new RoundCancelReqDto("우천", created.revision()));

    assertThat(cancelled.status()).isEqualTo(RoundStatus.CANCELLED);
    assertThat(cancelled.cancelReason()).isEqualTo("우천");
    assertThat(zoneEventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(ZoneEventStatus.CANCELLED);
    assertThat(participationRepository.findById(joined.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
  }

  @Test
  @DisplayName("이미 CLOSED인 회차는 cancel할 수 없다")
  void cancelClosedRoundConflicts() {
    ZoneEventRound round = closedRound();
    assertThatThrownBy(
            () -> consoleService.cancel(operator, round.getId(), new RoundCancelReqDto("사유", round.getRevision())))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("open/close 수동 엔드포인트는 더 이상 존재하지 않는다(컴파일 타임 보증)")
  void openCloseMethodsRemoved() {
    for (var method : consoleService.getClass().getMethods()) {
      assertThat(method.getName()).isNotIn("open", "close");
    }
  }

  @Test
  @DisplayName("정산은 미완료 참여를 만료하고 TOP_LIKE 보상을 지급하며 리포트를 저장한다(멱등)")
  void settle() {
    ZoneEventRound round = closedRound();
    rewardCatalogRepository.save(
        RewardCatalog.builder().rewardType(RewardType.COUPON).code("COUPON_CAFE").name("카페 쿠폰").stock(5).validDays(30).build());
    ZoneEvent event = eventWithExcellence(round.getId());
    ZoneEventParticipation winner = success(event, 10);
    ZoneEventParticipation joinedP = joined(event);

    Map<String, Object> report = consoleService.settle(operator, round.getId());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.SETTLED);
    assertThat(participationRepository.findById(joinedP.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(userCouponRepository.findAll()).hasSize(1);
    assertThat(settlementReportRepository.findById(round.getId())).isPresent();
    assertThat(winner.getLikeCount()).isEqualTo(10);

    Map<String, Object> again = consoleService.settle(operator, round.getId());
    assertThat(userCouponRepository.findAll()).hasSize(1);
    assertThat(again.get("roundId")).isEqualTo(round.getId().toString());
  }

  @Test
  @DisplayName("정산 리포트가 없으면 404, 있으면 저장된 리포트를 돌려준다")
  void settlementReport() {
    ZoneEventRound round = closedRound();
    assertThatThrownBy(() -> consoleService.settlementReport(operator, round.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    consoleService.settle(operator, round.getId());
    assertThat(consoleService.settlementReport(operator, round.getId()).get("roundId")).isEqualTo(round.getId().toString());
  }

  private int nextRoundNo() {
    return roundNoSeq++;
  }

  private AdminRoundResDto createDraft() {
    return consoleService.createRound(
        operator,
        new RoundCreateReqDto(
            null, nextRoundNo(), "테스트 회차", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null,
            new RewardSnapshotReqDto(null, null, 3, "COUPON_CAFE")));
  }

  private void createFourZoneEventsWithTargets(UUID roundId) {
    for (String zone : List.of("YEONGDO", "OLD_DOWNTOWN", "SUYEONG_NAMGU", "WESTERN_BUSAN")) {
      ZoneEvent event =
          zoneEventRepository.findById(
                  UUID.fromString(adminZoneEventService.create(operator, zoneEventReq(roundId, zone)).eventId()))
              .orElseThrow();
      authTargetRepository.save(
          ZoneEventAuthTarget.builder()
              .event(event)
              .targetKind(ZoneEventTargetKind.PLACE)
              .placeName("장소")
              .latitude(35.1)
              .longitude(129.1)
              .radiusM(100)
              .build());
    }
  }

  private AdminRoundResDto adminZoneEventServiceCreateAndReturnRound(UUID roundId, String zone) {
    adminZoneEventService.create(operator, zoneEventReq(roundId, zone));
    return consoleService.roundDetail(operator, roundId);
  }

  private AdminZoneEventCreateReqDto zoneEventReq(UUID roundId, String zoneId) {
    return new AdminZoneEventCreateReqDto(
        zoneId, type.getTypeCode(), "미션", null, OffsetDateTime.now().plusDays(1), 120, roundId, 1,
        new RewardSnapshotReqDto(50, null, null, null), null, null);
  }

  private ZoneEventRound closedRound() {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(nextRoundNo())
            .startsAt(OffsetDateTime.now().minusHours(2))
            .endsAt(OffsetDateTime.now().minusHours(1))
            .status(RoundStatus.CLOSED)
            .build());
  }

  private ZoneEvent eventWithExcellence(UUID roundId) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .roundId(roundId)
            .title("이벤트")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(120)
            .status(ZoneEventStatus.CLOSED)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .excellenceReward(new RewardSnapshot(null, null, 1, "COUPON_CAFE"))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEventParticipation success(ZoneEvent event, long likeCount) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser().getId())
            .status(ParticipationStatus.JOINED)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    p.submit("m.jpg", "후기", 35.1, 129.1, OffsetDateTime.now());
    p.markSuccess();
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    return participationRepository.save(p);
  }

  private ZoneEventParticipation joined(ZoneEvent event) {
    return participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser().getId())
            .status(ParticipationStatus.JOINED)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build());
  }

  private User savedUser() {
    return userRepository.save(
        User.builder()
            .email("u-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname("tester")
            .role(UserRole.USER)
            .build());
  }
}
```

(`reassignSlot`/`addBackupTarget`/`swapTarget` 관련 테스트는 이번 이슈 범위 밖 기능이지만 존재는 유지해야 하므로, 삭제하지 말고 위 파일에 없는 옛 테스트 3개(`슬롯 교체`, `예비 타겟 등록 및 우천 교체`, `이벤트 미배정 슬롯...`)는 새 `draftRound`+`adminZoneEventService.create` 조합으로 이식해 추가한다 — 슬롯이 이제 라운드 생성이 아니라 이벤트 생성으로 만들어지므로 `round(RoundStatus.OPEN)` 같은 옛 헬퍼 대신 `createDraft()` + `adminZoneEventService.create(...)`를 쓴다.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — `consoleService.schedule/patch/cancel`가 아직 없음, `RoundCreateReqDto`/`AdminRoundResDto` 생성자 시그니처 불일치.

- [ ] **Step 3: `AdminRoundConsoleService` 재작성**

```java
package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.reward.dto.response.SettlementReportResDto;
import com.butingbe.domain.reward.service.RewardSettlementService;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SlotReassignReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventSettlementReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventBackupTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 콘솔: 회차 캘린더·슬롯 배정·예비/우천 타겟·확정/취소·정산 재실행.
 *
 * <p>ACTIVE/CLOSED는 자동 전환({@link RoundTransitionService})으로만 도달하며, 이 서비스는 더 이상 수동 open/close를 제공하지
 * 않는다. 모든 메서드는 ROLE_ADMIN/MANAGER만 호출할 수 있고, 상태를 바꾸는 행위는 감사 로그에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminRoundConsoleService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventBackupTargetRepository backupTargetRepository;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventSettlementReportRepository settlementReportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final RoundSlotSuggestionService suggestionService;
  private final RoundTransitionService transitionService;
  private final RewardSettlementService settlementService;

  @Transactional
  public AdminRoundResDto createRound(AuthenticatedUser user, RoundCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    if (roundRepository.existsByRoundNo(request.roundNo())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundType(request.roundType())
                .roundNo(request.roundNo())
                .name(request.name())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .timezone(request.timezone())
                .excellenceReward(request.excellenceReward() == null ? null : request.excellenceReward().toSnapshot())
                .build());
    audit(user, "CREATE_ROUND", "ROUND", round.getId(), Map.of("roundNo", round.getRoundNo()));
    return detailOf(round);
  }

  @Transactional
  public List<AdminRoundResDto> listRounds(
      AuthenticatedUser user, String status, OffsetDateTime from, OffsetDateTime to, String keyword, Integer page, Integer size) {
    operatorAuthorization.requireOperator(user);
    RoundStatus statusFilter = status == null || status.isBlank() ? null : RoundStatus.valueOf(status.trim());
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    int pageNumber = page == null || page < 0 ? 0 : page;

    Specification<ZoneEventRound> spec = buildRoundSpec(statusFilter, from, to, keyword);
    Page<ZoneEventRound> result =
        roundRepository.findAll(spec, PageRequest.of(pageNumber, pageSize, org.springframework.data.domain.Sort.by("startsAt").descending()));

    OffsetDateTime now = OffsetDateTime.now();
    for (ZoneEventRound round : result.getContent()) {
      transitionService.sync(round, now);
    }
    return result.getContent().stream().map(this::detailOf).toList();
  }

  @Transactional
  public AdminRoundResDto roundDetail(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    transitionService.sync(round, OffsetDateTime.now());
    return detailOf(round);
  }

  @Transactional(readOnly = true)
  public SlotSuggestionResDto suggestSlots(AuthenticatedUser user, int authSlots) {
    operatorAuthorization.requireOperator(user);
    return suggestionService.suggest(OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul")), authSlots);
  }

  @Transactional
  public AdminRoundResDto patch(AuthenticatedUser user, UUID roundId, RoundPatchReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    requireMatchingRevision(round.getRevision(), request.expectedRevision());
    round.applyEditable(request.name(), request.startsAt(), request.endsAt(), request.timezone(), request.roundType());
    audit(user, "PATCH_ROUND", "ROUND", roundId, null);
    return detailOf(round);
  }

  /** DRAFT → SCHEDULED. 정확히 4개 서로 다른 구역, 각 슬롯 ACTIVE 타겟 1개 이상이어야 한다. */
  @Transactional
  public AdminRoundResDto schedule(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    List<ZoneEvent> events = zoneEventRepository.findByRoundId(roundId);
    Set<String> distinctZones = new HashSet<>();
    for (ZoneEvent event : events) {
      distinctZones.add(event.getZoneId());
    }
    if (events.size() != 4 || distinctZones.size() != 4) {
      throw new IllegalArgumentException("error.zone_event.round_slots_incomplete");
    }
    for (ZoneEvent event : events) {
      boolean hasActiveTarget =
          authTargetRepository.findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(event.getId(), ZoneEventTargetStatus.ACTIVE).isPresent();
      if (!hasActiveTarget) {
        throw new IllegalArgumentException("error.zone_event.round_target_missing");
      }
    }
    round.confirmSchedule();
    audit(user, "SCHEDULE_ROUND", "ROUND", roundId, null);
    return detailOf(round);
  }

  /** DRAFT/SCHEDULED/ACTIVE → CANCELLED. 연결된 구역 슬롯도 함께 취소하되 기존 이력은 보존한다. */
  @Transactional
  public AdminRoundResDto cancel(AuthenticatedUser user, UUID roundId, RoundCancelReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    requireMatchingRevision(round.getRevision(), request.expectedRevision());
    round.cancel(request.reason());
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      if (event.getStatus() == ZoneEventStatus.SCHEDULED || event.getStatus() == ZoneEventStatus.ACTIVE) {
        event.markCancelled();
        for (ZoneEventParticipation open :
            participationRepository.findByEvent_IdAndStatusIn(
                event.getId(), List.of(ParticipationStatus.JOINED, ParticipationStatus.SUBMITTED, ParticipationStatus.UNDER_REVIEW))) {
          open.cancel("ROUND_CANCELLED");
        }
      }
    }
    audit(user, "CANCEL_ROUND", "ROUND", roundId, Map.of("reason", request.reason()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundResDto reassignSlot(AuthenticatedUser user, UUID roundId, SlotReassignReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventRoundSlot slot =
        slotRepository
            .findById(request.slotId())
            .filter(s -> s.getRound().getId().equals(roundId))
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    slot.reassignZone(parseZone(request.zoneId()));
    audit(user, "REASSIGN_SLOT", "SLOT", slot.getId(), Map.of("zone", request.zoneId()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundResDto addBackupTarget(AuthenticatedUser user, UUID roundId, BackupTargetReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventBackupTarget target =
        backupTargetRepository.save(
            ZoneEventBackupTarget.builder()
                .round(round)
                .targetKind(request.targetKind())
                .landmarkId(request.landmarkId())
                .placeName(request.placeName())
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .radiusM(request.radiusM())
                .build());
    audit(user, "ADD_BACKUP_TARGET", "ROUND", roundId, Map.of("targetId", target.getId().toString()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundResDto swapTarget(AuthenticatedUser user, UUID roundId, SwapTargetReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventBackupTarget backup =
        backupTargetRepository
            .findById(request.backupTargetId())
            .filter(b -> b.getRound().getId().equals(roundId))
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    ZoneEventAuthTarget target =
        authTargetRepository
            .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(request.eventId(), ZoneEventTargetStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.target_not_found"));
    target.update(backup.getPlaceName(), backup.getGuideText(), backup.getExampleFileKey(), backup.getLatitude(), backup.getLongitude(), backup.getRadiusM());
    audit(user, "SWAP_TARGET", "EVENT", request.eventId(), Map.of("backupTargetId", request.backupTargetId().toString()));
    return detailOf(round);
  }

  @Transactional
  public Map<String, Object> settle(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = roundRepository.findWithLockById(roundId).orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    if (round.getStatus() == RoundStatus.SETTLED) {
      return settlementReport(user, roundId);
    }
    expireOpenParticipations(roundId);
    SettlementReportResDto prizeReport = settlementService.settleTopLike(roundId);
    OffsetDateTime now = OffsetDateTime.now();
    round.settle(now);
    Map<String, Object> report = assembleReport(roundId, now, prizeReport);
    settlementReportRepository.save(ZoneEventSettlementReport.builder().roundId(roundId).report(report).build());
    audit(user, "SETTLE_ROUND", "ROUND", roundId, null);
    return report;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> settlementReport(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    return settlementReportRepository.findById(roundId).map(ZoneEventSettlementReport::getReport)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private void requireMatchingRevision(Long actual, Long expected) {
    if (!actual.equals(expected)) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
  }

  private void expireOpenParticipations(UUID roundId) {
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      for (ZoneEventParticipation p : participationRepository.findByEvent_IdAndStatusIn(event.getId(), List.of(ParticipationStatus.JOINED))) {
        p.cancel("EXPIRED");
      }
    }
  }

  private Map<String, Object> assembleReport(UUID roundId, OffsetDateTime settledAt, SettlementReportResDto prizeReport) {
    Map<String, List<Map<String, Object>>> prizesByEvent = new LinkedHashMap<>();
    for (SettlementReportResDto.EventPrizes ep : prizeReport.events()) {
      List<Map<String, Object>> prizes = new ArrayList<>();
      for (SettlementReportResDto.Prize prize : ep.prizes()) {
        prizes.add(Map.of("userId", prize.userId(), "participationId", prize.participationId(), "rewardCode", prize.rewardCode(), "status", prize.status()));
      }
      prizesByEvent.put(ep.eventId(), prizes);
    }

    List<Map<String, Object>> events = new ArrayList<>();
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      long participants = participationRepository.countByEvent_Id(event.getId());
      long success = participationRepository.countByEvent_IdAndStatus(event.getId(), ParticipationStatus.SUCCESS);
      List<ZoneEventParticipation> top = participationRepository.findTopPublicSuccessByEvent(event.getId(), PageRequest.of(0, 1));
      Map<String, Object> eventReport = new LinkedHashMap<>();
      eventReport.put("eventId", event.getId().toString());
      eventReport.put("zoneId", event.getZoneId());
      eventReport.put("participants", participants);
      eventReport.put("success", success);
      eventReport.put("successRate", participants == 0 ? 0.0 : (double) success / participants);
      eventReport.put("topContentParticipationId", top.isEmpty() ? null : top.get(0).getId().toString());
      eventReport.put("prizes", prizesByEvent.getOrDefault(event.getId().toString(), List.of()));
      events.add(eventReport);
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("roundId", roundId.toString());
    report.put("settledAt", settledAt.toString());
    report.put("events", events);
    return report;
  }

  private AdminRoundResDto detailOf(ZoneEventRound round) {
    List<ZoneEventRoundSlot> slots = slotRepository.findByRound_Id(round.getId());
    Map<String, long[]> countsByEventId = new HashMap<>();
    for (ZoneEventRoundSlot slot : slots) {
      if (slot.getEventId() == null) {
        continue;
      }
      String eventId = slot.getEventId().toString();
      long participants = participationRepository.countByEvent_Id(slot.getEventId());
      long success = participationRepository.countByEvent_IdAndStatus(slot.getEventId(), ParticipationStatus.SUCCESS);
      long underReview = participationRepository.countByEvent_IdAndStatus(slot.getEventId(), ParticipationStatus.UNDER_REVIEW);
      countsByEventId.put(eventId, new long[] {participants, success, underReview});
    }
    return AdminRoundResDto.of(round, slots, backupTargetRepository.findByRound_Id(round.getId()), countsByEventId);
  }

  private Specification<ZoneEventRound> buildRoundSpec(RoundStatus status, OffsetDateTime from, OffsetDateTime to, String keyword) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
      }
      if (keyword != null && !keyword.isBlank()) {
        predicates.add(cb.like(root.get("name"), "%" + keyword + "%"));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private ZoneEventRound requireRound(UUID roundId) {
    return roundRepository.findById(roundId).orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }

  private void audit(AuthenticatedUser user, String action, String targetType, UUID targetId, Map<String, Object> detail) {
    auditLogRepository.save(ZoneEventAuditLog.builder().actorId(user.id()).action(action).targetType(targetType).targetId(targetId).detail(detail).build());
  }
}
```

`ZoneEventParticipation.countByEvent_IdAndStatus(UUID, ParticipationStatus)`가 `UNDER_REVIEW`에 대해서도 동작하는지(이미 SUCCESS에 대해 쓰이던 메서드 재사용) 확인 — 시그니처는 이미 범용이므로 그대로 재사용 가능하다.

주의: `countByEvent_Id(UUID)`, `countByEvent_IdAndStatus(UUID, ParticipationStatus)`는 기존에 `ZoneEventParticipationRepository`에 이미 있던 메서드다(원래 코드에서 `long joined = participationRepository.countByEvent_Id(...)` 식으로 쓰였음) — 새 메서드 추가가 필요 없다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.service.AdminRoundConsoleServiceTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/service/AdminRoundConsoleService.java src/test/java/com/butingbe/domain/zoneevent/service/AdminRoundConsoleServiceTest.java
git commit -m "feat(zoneevent): 회차 콘솔에 DRAFT 생성·PATCH·schedule·cancel 도입, 수동 open/close 제거"
```

---

## Task 10: `AdminRoundController` 재작성 — 엔드포인트 추가/삭제

**Files:**
- Modify: `src/main/java/com/butingbe/domain/zoneevent/controller/AdminRoundController.java`
- Modify: `src/test/java/com/butingbe/domain/zoneevent/controller/AdminRoundControllerTest.java`

**Interfaces:**
- Consumes: `AdminRoundConsoleService.createRound/listRounds/roundDetail/patch/schedule/cancel/reassignSlot/addBackupTarget/swapTarget/settle/settlementReport` (Task 9)

- [ ] **Step 1: 컨트롤러 테스트를 새 계약에 맞춰 전체 교체**

```java
package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.service.AdminRoundConsoleService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
class AdminRoundControllerTest {

  private static final UUID ROUND = UUID.fromString("44444444-0000-0000-0000-000000000001");

  @Mock private AdminRoundConsoleService consoleService;
  @InjectMocks private AdminRoundController controller;

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
            .setControllerAdvice(new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  private AdminRoundResDto round() {
    return new AdminRoundResDto(
        ROUND.toString(), 1, "테스트 회차", RoundType.REGULAR, RoundStatus.DRAFT,
        OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), "Asia/Seoul",
        null, null, false, null, null, 0L, List.of(), List.of());
  }

  @Test
  @DisplayName("회차 초안 생성 201")
  void create() throws Exception {
    when(consoleService.createRound(any(), any())).thenReturn(round());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content(
                    "{\"roundNo\":1,\"name\":\"테스트 회차\",\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.roundId").value(ROUND.toString()))
        .andExpect(jsonPath("$.data.status").value("DRAFT"));
  }

  @Test
  @DisplayName("roundNo 없이 생성하면 400")
  void createInvalid() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content("{\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("목록·상세·제안 200")
  void reads() throws Exception {
    when(consoleService.listRounds(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(round()));
    when(consoleService.roundDetail(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.suggestSlots(any(), anyInt()))
        .thenReturn(new SlotSuggestionResDto(List.of("YEONGDO"), List.of("YEONGDO: 직전 2회차 미오픈")));

    mockMvc
        .perform(get("/admin/zone-event-rounds").param("status", "DRAFT").param("page", "0").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].roundId").value(ROUND.toString()));
    mockMvc.perform(get("/admin/zone-event-rounds/{id}", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(get("/admin/zone-event-rounds/suggest-slots").param("authSlots", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slots[0]").value("YEONGDO"));
  }

  @Test
  @DisplayName("PATCH·schedule·cancel")
  void mutations() throws Exception {
    when(consoleService.patch(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.schedule(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.cancel(any(), eq(ROUND), any())).thenReturn(round());

    mockMvc
        .perform(
            patch("/admin/zone-event-rounds/{id}", ROUND)
                .contentType("application/json")
                .content("{\"expectedRevision\":0,\"name\":\"새 이름\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/admin/zone-event-rounds/{id}/schedule", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/cancel", ROUND)
                .contentType("application/json")
                .content("{\"reason\":\"우천\",\"expectedRevision\":0}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("슬롯 교체·예비 타겟·우천 교체")
  void slotMutations() throws Exception {
    when(consoleService.reassignSlot(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.addBackupTarget(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.swapTarget(any(), eq(ROUND), any())).thenReturn(round());

    mockMvc
        .perform(
            patch("/admin/zone-event-rounds/{id}/slots", ROUND)
                .contentType("application/json")
                .content("{\"slotId\":\"55555555-0000-0000-0000-000000000001\",\"zoneId\":\"YEONGDO\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/backup-targets", ROUND)
                .contentType("application/json")
                .content("{\"targetKind\":\"PLACE\",\"placeName\":\"대체지\",\"latitude\":35.1,\"longitude\":129.1,\"radiusM\":80}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/swap-target", ROUND)
                .contentType("application/json")
                .content("{\"eventId\":\"66666666-0000-0000-0000-000000000001\",\"backupTargetId\":\"77777777-0000-0000-0000-000000000001\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("정산·리포트")
  void settlement() throws Exception {
    when(consoleService.settle(any(), eq(ROUND))).thenReturn(Map.of("roundId", ROUND.toString(), "events", List.of()));
    when(consoleService.settlementReport(any(), eq(ROUND))).thenReturn(Map.of("roundId", ROUND.toString()));

    mockMvc.perform(post("/admin/zone-event-rounds/{id}/settle", ROUND)).andExpect(status().isOk());
    mockMvc.perform(get("/admin/zone-event-rounds/{id}/settlement-report", ROUND)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("open/close 엔드포인트는 더 이상 존재하지 않는다")
  void openCloseRemoved() throws Exception {
    mockMvc.perform(post("/admin/zone-event-rounds/{id}/open", ROUND)).andExpect(status().isNotFound());
    mockMvc.perform(post("/admin/zone-event-rounds/{id}/close", ROUND)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("운영 권한 없으면 403")
  void forbidden() throws Exception {
    when(consoleService.roundDetail(any(), eq(ROUND))).thenThrow(new ForbiddenException("error.operator.forbidden"));
    mockMvc.perform(get("/admin/zone-event-rounds/{id}", ROUND)).andExpect(status().isForbidden());
  }

  private HandlerMethodArgumentResolver authenticatedUserResolver() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return new AuthenticatedUser(ROUND, "op@example.com", "op", List.of());
      }
    };
  }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `gradlew.bat compileTestJava`
Expected: FAIL — 컨트롤러에 `patch/schedule/cancel` 없음.

- [ ] **Step 3: 컨트롤러 재작성**

```java
package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SlotReassignReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.service.AdminRoundConsoleService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영 회차 콘솔. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-rounds")
@RequiredArgsConstructor
public class AdminRoundController {

  private final AdminRoundConsoleService consoleService;

  @PostMapping
  public ResponseEntity<ApiResponse<AdminRoundResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody @Valid RoundCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("회차 초안 생성", consoleService.createRound(user, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminRoundResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(ApiResponse.success("회차 목록 조회", consoleService.listRounds(user, status, from, to, keyword, page, size)));
  }

  @GetMapping("/suggest-slots")
  public ResponseEntity<ApiResponse<SlotSuggestionResDto>> suggest(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam(defaultValue = "6") int authSlots) {
    return ResponseEntity.ok(ApiResponse.success("슬롯 배정 제안", consoleService.suggestSlots(user, authSlots)));
  }

  @GetMapping("/{roundId}")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("회차 상세 조회", consoleService.roundDetail(user, roundId)));
  }

  @PatchMapping("/{roundId}")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> patch(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId, @RequestBody @Valid RoundPatchReqDto request) {
    return ResponseEntity.ok(ApiResponse.success("회차 수정", consoleService.patch(user, roundId, request)));
  }

  @PostMapping("/{roundId}/schedule")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> schedule(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("회차 확정", consoleService.schedule(user, roundId)));
  }

  @PostMapping("/{roundId}/cancel")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> cancel(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId, @RequestBody @Valid RoundCancelReqDto request) {
    return ResponseEntity.ok(ApiResponse.success("회차 긴급 취소", consoleService.cancel(user, roundId, request)));
  }

  @PatchMapping("/{roundId}/slots")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> reassignSlot(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId, @RequestBody @Valid SlotReassignReqDto request) {
    return ResponseEntity.ok(ApiResponse.success("슬롯 교체", consoleService.reassignSlot(user, roundId, request)));
  }

  @PostMapping("/{roundId}/backup-targets")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> addBackupTarget(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId, @RequestBody @Valid BackupTargetReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("예비 타겟 등록", consoleService.addBackupTarget(user, roundId, request)));
  }

  @PostMapping("/{roundId}/swap-target")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> swapTarget(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId, @RequestBody @Valid SwapTargetReqDto request) {
    return ResponseEntity.ok(ApiResponse.success("우천 타겟 교체", consoleService.swapTarget(user, roundId, request)));
  }

  @PostMapping("/{roundId}/settle")
  public ResponseEntity<ApiResponse<Map<String, Object>>> settle(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("회차 정산", consoleService.settle(user, roundId)));
  }

  @GetMapping("/{roundId}/settlement-report")
  public ResponseEntity<ApiResponse<Map<String, Object>>> settlementReport(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("정산 리포트", consoleService.settlementReport(user, roundId)));
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.controller.AdminRoundControllerTest"`
Expected: PASS (`openCloseRemoved` 테스트는 standalone MockMvc가 없는 경로에 404를 주는지 확인 — Spring MVC standalone 셋업에서 매핑 안 된 경로는 기본적으로 404를 반환한다).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/butingbe/domain/zoneevent/controller/AdminRoundController.java src/test/java/com/butingbe/domain/zoneevent/controller/AdminRoundControllerTest.java
git commit -m "feat(zoneevent): 회차 컨트롤러에 PATCH·schedule·cancel 추가, open/close 제거"
```

---

## Task 11: 전체 빌드 검증 (`./gradlew check`) 및 잔여 컴파일 오류 정리

**Files:** 프로젝트 전역(컴파일 에러가 나는 파일들 — 특히 `AdminZoneEventControllerTest`, `AdminZoneEventServiceTest`의 `AdminZoneEventUpdateReqDto`/`AdminZoneEventCreateReqDto` 생성자 호출부, `RoundCreateReqDto`를 참조하는 다른 테스트가 있다면 그것들)

- [ ] **Step 1: ASCII 경로에 클론/워크트리가 준비돼 있는지 확인**

기존 메모(한글 경로 Gradle 문제)에 따라, `C:\dev\bu-ting-backend` 같은 ASCII 경로에 이 브랜치를 체크아웃하고 `GRADLE_USER_HOME`도 ASCII 경로로 지정한 뒤 그 경로에서 아래 명령들을 실행한다.

- [ ] **Step 2: 전체 컴파일**

Run: `gradlew.bat compileTestJava`
Expected: PASS. 실패하면 에러 메시지에 나온 파일을 열어, 이전 태스크에서 바뀐 시그니처(`RoundCreateReqDto`, `AdminZoneEventUpdateReqDto`, `AdminZoneEventCreateReqDto` 생성자 인자 개수/순서, `AdminZoneEventResDto`/`AdminRoundResDto` 필드)에 맞춰 호출부를 고친다. 특히 `AdminZoneEventCreateReqDto`는 이번 계획에서 필드를 바꾸지 않았지만, `RewardSnapshotReqDto` 뒤에 `authTarget` 인자가 있는 기존 순서를 유지했는지 확인한다.

- [ ] **Step 3: zoneevent 도메인 전체 테스트 실행**

Run: `gradlew.bat test --tests "com.butingbe.domain.zoneevent.*"`
Expected: PASS 전체.

- [ ] **Step 4: 전체 `check` 실행**

Run: `gradlew.bat check`
Expected: PASS (spotless, 테스트, 커버리지 게이트 포함). 커버리지 게이트가 실패하면 새로 추가한 브랜치(예: `applyEditable`의 각 null-분기, `schedule`의 두 실패 조건)에 대한 단위 테스트가 빠지지 않았는지 확인하고 보강한다.

- [ ] **Step 5: 커밋 (수정이 있었다면)**

```bash
git add -A
git commit -m "fix(zoneevent): 회차 계약 변경에 따른 잔여 컴파일 오류·테스트 보강"
```

---

## Task 12: `openapi3.yaml` 문서화

**Files:**
- Modify: `src/main/resources/static/docs/openapi3.yaml` (5135번째 줄 부근 `/api/v1/admin/zone-event-rounds/*` 섹션)

- [ ] **Step 1: 기존 `/api/v1/admin/zone-event-rounds/{roundId}/open`, `.../close` 섹션(약 5394~5461줄)을 삭제**

- [ ] **Step 2: `/api/v1/admin/zone-event-rounds` POST/GET 섹션을 새 요청/응답 스키마로 갱신**

기존 형제 섹션(5135~5233줄 부근)의 한국어 서술 스타일(`summary`, `description`, 요청/응답 예시 JSON)을 그대로 따라, `roundNo`(필수, 중복 시 409), `name`, `excellenceReward` 필드와 `status/from/to/keyword/page/size` 쿼리 파라미터, `DRAFT` 상태 응답 예시를 반영한다. (정확한 YAML 서식은 파일을 열어 인접 항목을 복사해 필드만 교체하는 방식으로 작성 — 이 파일은 매우 길어 플랜에 전문을 포함하지 않는다.)

- [ ] **Step 3: `/api/v1/admin/zone-event-rounds/{roundId}` 에 PATCH 섹션 추가, GET 응답 스키마에 `revision/cancelReason/excellenceReward/settled/slots[].participantCount 등` 추가**

- [ ] **Step 4: `/api/v1/admin/zone-event-rounds/{roundId}/schedule`, `.../cancel` 섹션 신규 추가** (POST, 각각 검증 실패 400/409 예시 포함)

- [ ] **Step 5: `/api/v1/admin/zone-events` GET을 `roundId/page/size` 쿼리로, POST/PATCH 응답에 `slotCode/revision` 추가, `/activate`·`/close` 섹션 삭제**

- [ ] **Step 6: YAML 유효성 검증**

Run(메모의 방식대로): 프로젝트에 있는 OpenAPI 검증 스크립트나 `npx @redocly/cli lint src/main/resources/static/docs/openapi3.yaml` 등 기존에 쓰던 방법으로 문법 검증(기존 메모 "OpenAPI docs convention" 참고 — 리포지토리에 이미 정해진 검증 커맨드가 있으면 그것을 쓴다. 없으면 YAML 파서로 로드만 확인: `python -c "import yaml; yaml.safe_load(open('src/main/resources/static/docs/openapi3.yaml', encoding='utf-8'))"`)
Expected: 파싱 에러 없음.

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/static/docs/openapi3.yaml
git commit -m "docs(zoneevent): 회차 초안·확정·취소·PATCH 및 이벤트 목록 계약을 openapi3.yaml에 반영"
```

---

## Task 13: 최종 검증 및 브랜치 정리

- [ ] **Step 1: `./gradlew check` 최종 1회 더 실행 (문서 변경이 코드에 영향 없음을 재확인)**

Run: `gradlew.bat check`
Expected: PASS.

- [ ] **Step 2: `git status`로 의도치 않은 변경 파일이 없는지 확인 후, `git log --oneline dev..HEAD`로 이번 이슈의 커밋들이 논리적 단위로 쌓여 있는지 확인**

- [ ] **Step 3: superpowers:finishing-a-development-branch 스킬로 브랜치 통합 방법(PR 생성 등) 결정**

이 프로젝트는 PR이 dev로 자동 머지되는 워크플로우([PR 자동 머지 메모] 참고)이므로, 이 스킬의 안내에 따라 PR을 생성하고 자동 머지를 기다린다.
