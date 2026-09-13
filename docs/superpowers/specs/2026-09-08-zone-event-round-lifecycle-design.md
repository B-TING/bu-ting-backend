# 회차·슬롯 관리 API 및 자동 시작/종료 전환 (이슈 #238)

## 배경

이슈 #235(구역 이벤트 관리자 API)의 하위 이슈. #237(스키마 보완)이 이미 `dev`에 머지되어 있고, 그 위에서 회차(`ZoneEventRound`)·슬롯 관련 관리 콘솔(`AdminRoundConsoleService`, `ZoneEventRoundScheduler`, `RoundSlotSuggestionService`, `RoundStatusQueryService`)이 이미 존재한다. 하지만 상태 모델과 회차 생성 계약이 이슈 #238의 체크리스트·API 예시와 다르므로, 아래처럼 조정한다.

이슈 원문 체크리스트, API 예시, 완료 조건을 기준으로 하며, 사용자와의 대화에서 다음 3가지를 확정했다:

- `roundNo`는 관리자가 생성 요청에 직접 지정한다(서버 자동발급 폐기). 중복 시 409.
- 기존 수동 "회차 열기/닫기" 버튼(`POST .../open`, `POST .../close`)은 **완전히 삭제**한다. 상태 전환은 시작/종료 시각 기준 자동 전환만 존재한다.
- 회차 생성 시 `excellenceReward`(TOP N 포함)를 **회차 공통 기본값**으로 받는다. 이후 각 구역 슬롯(zone-event) 생성 시 별도로 넘기지 않으면 이 기본값을 물려받는다.

## 1. 상태 모델

`RoundStatus`: `SCHEDULED, OPEN, CLOSED, SETTLED` → `DRAFT, SCHEDULED, ACTIVE, CLOSED, CANCELLED, SETTLED`

```
DRAFT --(POST /schedule, 검증 통과)--> SCHEDULED
SCHEDULED --(startsAt 도달, 자동)--> ACTIVE
ACTIVE --(endsAt 도달, 자동)--> CLOSED
CLOSED --(정산, 후속 이슈 #244)--> SETTLED
DRAFT|SCHEDULED|ACTIVE --(POST /cancel)--> CANCELLED
```

- `ACTIVE`/`CLOSED`는 자동 전환(스케줄러 또는 요청 시점 동기화)으로만 도달한다. 일반 `PATCH`에는 `status` 필드 자체가 없어 클라이언트가 강제로 지정할 방법이 없다.
- `CANCELLED`는 `DRAFT/SCHEDULED/ACTIVE`에서만 가능(이미 `CLOSED/CANCELLED/SETTLED`면 409).
- `ZoneEventStatus`(이벤트/슬롯)에도 대응해 `CANCELLED`가 이미 있으므로 그대로 사용.

마이그레이션: `zone_event_round` 체크 제약을 `('DRAFT','SCHEDULED','ACTIVE','CLOSED','CANCELLED','SETTLED')`로 교체. 기존 데이터가 없다는 전제(스키마만 있고 운영 데이터 없음)로 단순 컬럼 유지 + 제약 교체.

## 2. 데이터 모델 변경

- `zone_event_round`: `excellence_reward JSONB` 컬럼 추가(회차 공통 기본 보상), `cancel_reason VARCHAR(300)` 추가.
- `zone_event`: 취소 사유 저장을 위해 이미 있는 감사 로그(`ZoneEventAuditLog`)로 충분 — 별도 컬럼 불필요.
- `zone_event_round_slot`(기존 6구역 로테이션 제안·예비 타겟 기능이 쓰는 테이블)은 그대로 둔다. 다만 지금까지 `AdminZoneEventService.create()`가 `roundId`를 받아도 이 테이블을 갱신하지 않던 연결 누락을 이번에 고친다: `roundId`가 있으면 해당 회차의 슬롯을 찾아 없으면 만들고 `slot.assignEvent(eventId)`로 연결한다. 이렇게 하면 "정확히 4개 구역" 검증과 슬롯 조회(`suggest-slots`, `swap-target`, round 상세의 slots 목록)가 하나의 사실 기반으로 일치한다.

## 3. API 설계

### 3.1 `POST /admin/zone-event-rounds` — 회차 초안 생성
요청: `roundNo(필수,중복시409), name, startsAt, endsAt, timezone, roundType, excellenceReward(선택)`
생성 시 `status=DRAFT`. 구역/슬롯은 받지 않는다(이후 `/admin/zone-events`로 추가).

### 3.2 `GET /admin/zone-event-rounds` — 목록
쿼리: `status, from, to, keyword(name 부분일치), page, size` (기존 cursor 방식 → page/size로 변경, 이슈 요구사항 그대로).

### 3.3 `GET /admin/zone-event-rounds/{roundId}` — 상세
기존 응답(슬롯/예비 타겟)에 추가:
- 슬롯(구역)별: `eventId, zoneId, participantCount, successCount, underReviewCount`
- 회차 전체: `settled(boolean)`, `revision`

### 3.4 `PATCH /admin/zone-event-rounds/{roundId}` — 메타데이터 수정
요청: `expectedRevision(필수), name, startsAt, endsAt, timezone, roundType` (모두 null 허용, null은 미변경).
`status` 필드 없음. `DRAFT/SCHEDULED` 상태에서만 허용(그 외 409). `expectedRevision != round.revision` → 409.

### 3.5 `POST /admin/zone-event-rounds/{roundId}/schedule` — 확정
`DRAFT`에서만 호출 가능. 검증:
- 이 회차에 연결된 `ZoneEvent`(zone-events)가 정확히 4개, `zoneId`가 서로 달라야 함 → 아니면 400.
- 각 이벤트에 `ACTIVE` 상태 인증 타겟이 최소 1개 → 아니면 400.
통과 시 `DRAFT → SCHEDULED`.

### 3.6 `POST /admin/zone-event-rounds/{roundId}/cancel` — 긴급 취소
요청: `reason(필수), expectedRevision(필수)`.
`DRAFT/SCHEDULED/ACTIVE`에서만 가능. 연결된 zone-event들도 함께 취소(`ZoneEvent.markCancelled()`, 기존 참여/검수/보상 이력은 그대로 유지 — 이미 있는 `AdminZoneEventService.cancel()`의 BR-13 처리와 동일 로직 재사용).

### 3.7 `GET /admin/zone-events` — 슬롯 목록 (기존 존재, 계약 변경)
이슈 체크리스트가 `roundId/zoneId/status/page/size`를 명시하므로, 기존 `cursor` 방식을 `page/size`로 바꾸고 `roundId` 필터를 추가한다(`zone` 파라미터명은 유지). 응답도 `AdminZoneEventPageResDto`(cursor 기반) 대신 `page/size/totalElements`를 포함하는 새 페이지 응답으로 바꾼다.

### 3.8 `POST /admin/zone-events` — 슬롯 생성 (기존 존재, 검증 추가)
추가 검증:
- 같은 구역·겹치는 시간대(`[startsAt, endsAt)` 겹침, 상태 `SCHEDULED`/`ACTIVE`인 기존 이벤트와 겹치면 409).
- `roundId`가 있으면: 이미 그 회차에 같은 `zoneId`가 있으면 409, 이미 4개면 409.
- `slotCode`는 서버가 `{round.roundNo}-A|B|C|D` 중 그 회차에서 아직 안 쓰인 첫 글자로 자동 발급. 클라이언트가 보내면 무시.
- `excellenceReward`가 없고 `roundId`가 있으면 회차의 기본값을 사용.

### 3.9 `GET/PATCH /admin/zone-events/{eventId}` — 슬롯 상세/편집 (기존 존재, 필드 추가)
`PATCH`에 `reason(변경 사유, 선택 로그용), expectedRevision(필수)` 추가. 나머지는 기존 로직 유지.

### 3.10 `POST /admin/zone-events/{eventId}/cancel` — 슬롯 긴급 취소 (기존 존재, 유지)
그대로 유지(이미 이력 보존 로직 있음).

### 3.11 제거되는 엔드포인트
- `POST /admin/zone-event-rounds/{roundId}/open`
- `POST /admin/zone-event-rounds/{roundId}/close`
- `POST /admin/zone-events/{eventId}/activate`
- `POST /admin/zone-events/{eventId}/close`

(`/settle`, `/settlement-report`는 후속 이슈 #244 소관이라 손대지 않는다.)

## 4. 자동 전환 일관성

새 `RoundTransitionService.sync(ZoneEventRound round, OffsetDateTime now)`:
- `status==SCHEDULED && now>=startsAt` → `ACTIVE`로 바꾸고 연결된 zone-event들도 `ACTIVE`로.
- `status==ACTIVE && now>=endsAt` → `CLOSED`로 바꾸고 연결된 zone-event들도 `CLOSED`로.
- 멱등(조건이 안 맞으면 아무 것도 안 함).

호출 지점:
- `ZoneEventRoundScheduler.advance(now)` (기존 로직을 이 서비스로 이관)
- `AdminRoundConsoleService`의 목록/상세 조회 시, 반환 전에 대상 회차(들)에 대해 먼저 `sync` 호출
- `RoundStatusQueryService.current()` (유저용 현재 회차 조회) — 조회 전에 후보 회차에 `sync` 호출

## 5. 감사 로그

`schedule`, `cancel`(회차/슬롯), `patch`(메타데이터 수정) 모두 기존 `audit()` 헬퍼로 기록한다. `open`/`close` 관련 액션 로그는 제거(스케줄러가 만드는 자동 전환은 감사 로그 대상이 아님 — 사람의 조작이 아니므로).

## 6. 테스트 계획

- 엔티티: `ZoneEventRound` 상태 전이 단위 테스트(DRAFT→SCHEDULED→ACTIVE→CLOSED, CANCELLED 분기, 잘못된 전이 409).
- 서비스: `AdminRoundConsoleService`(생성/목록/상세/patch/schedule/cancel), `AdminZoneEventService`(슬롯 생성 시 중복 구역·시간 겹침·slotCode 발급·보상 상속), `RoundTransitionService`(경계 시각 sync).
- 스케줄러: 기존 `ZoneEventRoundSchedulerTest`를 새 상태명·`RoundTransitionService` 위임 구조에 맞게 갱신.
- 컨트롤러: 신규/변경 엔드포인트 MockMvc 테스트, 제거된 엔드포인트는 404 확인.
- 마이그레이션: `ZoneEventRoundMigrationTest`에 새 체크 제약·컬럼 반영.
- `./gradlew check` 통과 (ASCII 경로 클론 필요 — 기존 방식).

## 7. 문서화

`openapi3.yaml`의 기존 `/admin/zone-event-rounds/*` 섹션을 새 계약으로 다시 쓰고, `/open`,`/close`,`/activate` 항목은 삭제, `/schedule`,`/cancel`(회차),`PATCH`(회차) 항목을 새로 추가한다. 기존 문서의 한국어 서술 스타일을 그대로 따른다.

## 8. 범위 제외

- 정산(`/settle`, `/settlement-report`), 리뷰(검수) API, 보상 지급 API — 각각 다른 하위 이슈.
- `ZoneEventRoundSlot`의 `MUKJJIPPA`(묵찌빠) 슬롯 종류 — Phase 3, 이번 이슈와 무관.
- 6구역 로테이션 제안(`suggest-slots`)의 알고리즘 자체는 변경하지 않는다(4구역 확정 검증과는 별개 기능).
