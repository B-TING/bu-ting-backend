# 보상·정산 관리 API (이슈 #244)

## 배경

부모 이슈 #235(구역 이벤트 관리자 API)의 하위 이슈. `RewardPayout`(TOP_LIKE)·`BaseRewardPayout`(BASE) 엔티티와 상태 enum(`RewardPayoutStatus`, `BaseRewardPayoutStatus`, `PayoutHoldStatus`)은 #249/#254에서 스키마·엔티티 레벨로 이미 만들어졌고, 직전 이슈 #243(PR #255, 방금 `dev`에 머지됨)이 `AdminRewardPayoutController`/`AdminRewardPayoutService`(`/admin/reward-payouts`)를 신설해 `POST /{payoutId}/release-hold` 하나만 구현해 두었다. 이번 이슈는 그 위에 나머지 8개 관리자 엔드포인트(목록·상세·수정·일괄 확정·일괄 일정·발송 3단계 기록·재시도)와 상태 전이·원장 반영 로직을 채운다.

**#243이 이미 확정해 둔, 이번에도 그대로 따르는 관례:**
- `payoutId`는 프리픽스 없는 평범한 UUID. 조회 시 `RewardPayoutRepository.findById()`를 먼저 시도하고 없으면 `BaseRewardPayoutRepository.findById()`로 폴백(두 테이블의 UUID 공간이 사실상 충돌 불가능하다는 전제, `AdminRewardPayoutService.releaseHold()`가 이미 이 패턴).
- revision 검증은 "명시적 비교로 즉시 409" + "saveAndFlush를 try/catch(ObjectOptimisticLockingFailureException)로 이중 방어" 두 겹.
- Idempotency-Key는 선택 헤더, `IdempotencyService.findReplay`/`save` 사용, fingerprint는 `payoutId + ":" + 주요필드...` 조합.
- 모든 변경 액션은 `ZoneEventAuditLogRepository`에 감사 로그(actor/action/targetType="REWARD_PAYOUT"/targetId/detail) 기록.
- 참여 숨김 해제(`AdminReviewService.unhide()`)가 `BaseRewardPayout` 보류를 자동 해제하지 않는 것은 #243이 의도적으로 남겨 둔 레거시 갭이다. 이번 이슈 범위가 아니므로 손대지 않는다.

사용자와의 대화에서 확정한 것:
- BASE 지급 건(`BaseRewardPayout`, PENDING_CONFIRM) 생성은 별도 API가 아니라 **사진 승인 시 자동 생성**(`AdminZoneEventReviewService.approve()`). 이벤트에 `baseReward`가 있고 아직 없으면 생성, 생성 시점에 미해결 신고가 있으면 바로 보류.
- 목록/상세는 두 테이블을 **각각 조회 후 서비스 레이어에서 병합**(네이티브 UNION 쿼리 대신).

## 1. 상태 모델 (기존 그대로, 전이 로직만 신규)

```
TOP_LIKE(RewardPayout):
  PENDING_ASSIGN --(PATCH로 reward.prizeRewardCode 확정)--> PENDING_CONFIRM
  PENDING_CONFIRM --(bulk-confirm)--> CONFIRMED
  CONFIRMED --(mark-mail-sent)--> MAIL_SENT
  MAIL_SENT --(mark-info-collected)--> INFO_COLLECTED
  INFO_COLLECTED --(mark-sent)--> SENT
  (CONFIRMED 이전 어느 단계든 참여 회수 시)--> FAILED  # 기존 AdminReviewService.revoke()가 이미 처리
  FAILED --(retry)--> CONFIRMED

BASE(BaseRewardPayout):
  PENDING_CONFIRM --(bulk-confirm)--> CONFIRMED
  CONFIRMED --(mark-sent)--> PAID   # 이 전이에서 RewardService.grantBaseReward() 원자 호출
  FAILED --(retry)--> CONFIRMED

holdStatus(NONE/HELD_REPORT)는 두 타입 모두 status와 독립 축(#243 기존 로직) — 
모든 전이 API는 holdStatus == NONE을 전제 조건으로 검사한다.
```

- TOP_LIKE의 "품목 미정"은 `reward.prizeRewardCode == null`인 PENDING_ASSIGN 상태를 말한다. `RewardPayoutService.generate()`가 이벤트의 `excellenceReward` 스냅샷을 그대로 복사하므로, 이벤트 생성 시 상품 코드가 안 정해져 있었으면 PENDING_ASSIGN으로 남는다.
- BASE는 애초에 `event.getBaseReward()`가 있어야만 생성되므로 PENDING_ASSIGN 단계가 없다(바로 PENDING_CONFIRM).

## 2. 데이터 모델 변경

마이그레이션 `V46__reward_payout_memo.sql`:
- `reward_payout`에 `memo TEXT`, `reference VARCHAR(255)` 컬럼 추가.
- `base_reward_payout`에 `memo TEXT` 컬럼 추가(실물 발송이 없어 reference 불필요 — BASE의 mark-sent에 reference를 보내도 memo로 흡수).

엔티티 메서드 추가:
- `RewardPayout`: `assignReward(RewardSnapshot reward)`(reward 갱신, `prizeRewardCode != null`이고 현재 PENDING_ASSIGN이면 PENDING_CONFIRM으로 전진), `updateMemo(String)`, `updateSchedule(OffsetDateTime)`, `confirm(UUID operatorId)`(PENDING_CONFIRM만 허용, CONFIRMED + confirmedBy/At 세팅), `markMailSent(OffsetDateTime, String note)`, `markInfoCollected(OffsetDateTime, String note)`, `markSent(OffsetDateTime, String reference, String note)`, `retry()`(FAILED만 허용 → CONFIRMED, failureCode 초기화).
- `BaseRewardPayout`: `updateReward(RewardSnapshot)`, `updateMemo(String)`, `updateSchedule(OffsetDateTime)`, `confirm(UUID operatorId)`(PENDING_CONFIRM→CONFIRMED), `markSent(OffsetDateTime paidAt, String note)`(CONFIRMED만 허용 → PAID), `retry()`(FAILED → CONFIRMED).
- 상태 가드는 각 메서드 내부에서 `IllegalStateException` 대신 서비스 레이어가 먼저 상태를 검사해 `ConflictException`을 던지는 기존 관례를 따른다(엔티티 메서드 자체는 상태를 세팅만 하고, "지금 이 상태에서 호출 가능한가"는 서비스가 판단).

`AdminZoneEventReviewService.approve()` 수정: `participation.markSuccess()` 직후, `event.getBaseReward() != null`이고 `baseRewardPayoutRepository.findByParticipationId(participationId)`가 비어 있으면 `BaseRewardPayout.builder().participationId(...).reward(event.getBaseReward()).build()` 생성·저장. `reportRepository.hasUnresolvedReports(participationId)`면 즉시 `.hold()`.

## 3. API 설계 (`AdminRewardPayoutController`에 엔드포인트 추가, 기존 release-hold 유지)

### 3.1 `GET /admin/reward-payouts` — 목록
쿼리: `roundId, eventId, rewardReason(BASE|TOP_LIKE), status, holdStatus, scheduledFrom, scheduledTo, page, size`.
- `rewardReason`이 지정되면 해당 리포지토리만 새 `searchForAdmin(...)` JPQL로 페이징 조회(둘 다 참여 통해 이벤트/회차 조인 — `RewardPayout`은 `eventId` 컬럼이 있어 `ZoneEvent` 직접 조인, `BaseRewardPayout`은 `participationId`로 `ZoneEventParticipation`을 조인해 `p.event.id`/`p.event.roundId`로 필터).
- 미지정 시 두 리포지토리를 각각 무페이징 조회 후 서비스에서 병합, `scheduledAt`(null이면 `createdAt`) 기준 정렬, 인메모리 페이징. (회차당 물량이 작아 허용 가능 — TOP_LIKE는 topN 수상자, BASE는 승인된 성공 참여 수만큼.)
- 응답 아이템: `AdminRewardPayoutListItemResDto(payoutId, payoutType, participationId, eventId, status, holdStatus, scheduledAt, reward, revision)`.

### 3.2 `GET /admin/reward-payouts/{payoutId}` — 상세
기존 `release-hold`와 동일한 try-RewardPayout-then-BaseRewardPayout 조회. `AdminRewardPayoutDetailResDto`는 두 타입의 필드를 모두 담되 타입에 없는 필드는 null(예: BASE 조회 시 `rankN/likeCountAtClose/mailedAt/informationCollectedAt/sentAt/reference`는 null, `paidAt`만 채움).

### 3.3 `PATCH /admin/reward-payouts/{payoutId}` — 보상 설정·메모·일정
요청: `reward(선택, RewardSnapshot), memo(선택), scheduledAt(선택), expectedRevision(필수)`. 정책:
- `reward` 필드가 오면: 현재 status가 `PENDING_ASSIGN`(TOP_LIKE) 또는 `PENDING_CONFIRM`(둘 다)이 아니면 409(`error.reward.payout.reward_locked`) — "확정 후 임의 보상 변경 불가".
- `memo`/`scheduledAt`은 `FAILED/SENT/PAID`가 아니면 언제든 수정 가능(발송 완료·실패 건은 잠금).
- 성공 시 `revision` 증가(JPA `@Version`), 응답에 최신 상태 반환.

### 3.4 `POST /admin/reward-payouts/bulk-confirm` — 일괄 확정
요청: `payoutIds(List<String>), expectedRevisions(Map<String,Long>)`. 2-pass:
1. **검증 패스**(변경 없음): 각 id에 대해 조회(없으면 문제), revision 일치 확인, `holdStatus==NONE` 확인, `status==PENDING_CONFIRM` 확인(TOP_LIKE가 아직 PENDING_ASSIGN이면 "품목 미정"으로 문제 처리). 문제 있는 id를 전부 모은다.
2. 문제가 하나라도 있으면 **아무것도 반영하지 않고** `BulkPayoutConflictException(problemPayoutIds, reasons)` → 409, `{ problemPayoutIds: [...] }` 페이로드(기존 `OpenParticipationExistsException` 처리 관례처럼 `GlobalExceptionHandler`에 전용 핸들러 추가).
3. 전부 통과하면 각 건 `confirm(user.id())` 호출, 감사 로그 1건(대상 목록 포함) 또는 건별 기록 중 후자 선택(추적 용이성).

### 3.5 `POST /admin/reward-payouts/bulk-schedule` — 일괄 일정
요청: `payoutIds(List<String>), scheduledAt(OffsetDateTime, 전체 공통), expectedRevisions(Map<String,Long>)`. 3.4와 동일한 2-pass, 조건은 `holdStatus==NONE`이고 `status==CONFIRMED`(그 이후 이미 발송 진행 중인 건은 일정 재조정 대상 아님 — 409). 통과 시 `updateSchedule(scheduledAt)` 일괄 적용.

### 3.6 `POST /admin/reward-payouts/mark-mail-sent` — 메일 발송 기록 (TOP_LIKE 전용)
요청(단건): `payoutId, mailedAt(선택, 기본 now), note, expectedRevision`. `status==CONFIRMED` 아니면 409, BASE 타입이면 400(`error.reward.payout.wrong_type`). `holdStatus==NONE` 확인. `markMailSent` 호출 → `MAIL_SENT`.

### 3.7 `POST /admin/reward-payouts/mark-info-collected` — 개인정보 수집 기록 (TOP_LIKE 전용)
요청: `payoutId, informationCollectedAt(선택), note, expectedRevision`. `status==MAIL_SENT` 아니면 409. `markInfoCollected` → `INFO_COLLECTED`.

### 3.8 `POST /admin/reward-payouts/mark-sent` — 실제 발송 완료 기록 (BASE/TOP_LIKE 공통)
요청: `payoutId, sentAt(선택), reference(선택, TOP_LIKE만 저장), note, expectedRevision`.
- TOP_LIKE: `status==INFO_COLLECTED` 아니면 409 → `markSent` → `SENT`.
- BASE: `status==CONFIRMED` 아니면 409 → **같은 트랜잭션에서** `ZoneEventParticipationRepository`로 참여 조회해 `userId/eventId` 확보 후 `RewardService.grantBaseReward(userId, participationId, eventId, reward.points(), reward.badgeCode())` 호출(이 메서드의 기존 UK 가드가 재호출 시 이중 지급을 막아줌) → `markSent`로 `paidAt` 세팅, `PAID`. 완료 조건 "서버가 reward_grant에 원자적으로 반영"을 여기서 충족.

### 3.9 `POST /admin/reward-payouts/{payoutId}/retry` — 재시도
요청: `note(선택), expectedRevision`. `status==FAILED` 아니면 409. `retry()` → `CONFIRMED`로 복귀, `failureCode` null화. 재시도 후 관리자가 mark-mail-sent/mark-sent를 다시 호출하는 흐름은 그대로 재사용(BASE는 `grantBaseReward`의 UK 가드로 중복 지급 없이 안전).

## 4. 예외·메시지

신규 `BulkPayoutConflictException`(`global/error/exception`) — `List<String> problemPayoutIds` 보유, `GlobalExceptionHandler`에 `OpenParticipationExistsException` 패턴과 동일하게 전용 핸들러(409 + `Map.of("problemPayoutIds", ...)`).

신규 메시지 키(4개 로케일 동일 순서로 추가): `error.reward.payout.reward_locked`, `error.reward.payout.wrong_type`, `error.reward.payout.reward_not_assigned`, `error.reward.payout.invalid_state`, `error.reward.payout.bulk_conflict`.

## 5. 테스트

기존 컨벤션: `AbstractContainerTest` 기반 `AdminRewardPayoutServiceTest`(이미 존재하는 release-hold 테스트에 이어서 확장) + `AdminRewardPayoutControllerTest`(MockMvc, 이미 존재 파일에 이어서 확장). `AdminZoneEventReviewServiceTest`(approve 시 BASE 생성 검증 — 파일 존재 여부 확인 후 확장). 4개 로케일 메시지 키. `openapi3.yaml`에 8개 엔드포인트 한글 문서화 후 `js-yaml` 파싱 검증. `./gradlew check`로 커버리지(100%)·Spotless 통과 확인.
