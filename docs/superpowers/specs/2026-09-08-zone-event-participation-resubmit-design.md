# 참여·재제출 사용자 API 확장 (이슈 #240)

## 배경

이슈 #235(구역 이벤트 관리자 API)의 하위 이슈. 관리자 API(#239 타겟 CRUD, #238 회차/슬롯)는 이미 `dev`에 머지되어 있다. 사용자용 참여·제출 API(#188~#191)도 이미 머지되어 있지만, 반려 후 재제출·제출별 이력 보존·마감 검증이 빠져 있어 관리자 검수 API가 실질적으로 의미를 갖지 못한다.

핵심 발견: `ZoneEventSubmission` 엔티티(제출 1회당 1 row, `attemptNo`/좌표·반경 스냅샷/`reviewStatus` 모두 이미 구현됨, `ZoneEventSubmissionRepository`도 존재)가 **완전히 미사용 상태**다. 현재 `ZoneEventSubmitService.submit()`은 `ZoneEventParticipation` 필드를 직접 덮어쓰고 `ZoneEventSubmission` row를 만들지 않는다. `AdminReviewService`도 `ZoneEventParticipation`만 조작하고 `ZoneEventSubmission`을 전혀 건드리지 않는다. 이번 작업의 본질은 이미 설계된 이 엔티티를 실제 흐름에 연결하는 것이다.

사용자와의 대화에서 다음을 확정했다:

- `ParticipationStatus.FAIL`을 재사용해 "반려됨, 마감 전까지 재제출 가능" 상태로 삼는다(신규 enum 값 추가 안 함) — `markFail`은 `AdminReviewService.reject()`에서만 호출되는 것을 확인했으므로 다른 의미와 충돌하지 않는다.
- `targetId`는 참여 시작(join)과 제출(submit) 양쪽 요청에 모두 추가한다. 재제출 시 이전과 다른 타겟을 선택할 수 있다(이슈 API 예시·완료조건 근거).
- 요구사항 #9(카메라 업로더 검증)는 클라이언트 자기신고 플래그(`source=CAMERA`) 없이, 서버가 실제로 검증 가능한 항목만 강화한다 — 이슈 본문이 "촬영 세션 연계 검증은 파일/모바일팀과 별도 계약 필요"라고 명시했기 때문.

## 1. 상태 모델

`ParticipationStatus`는 그대로 유지(`JOINED, SUBMITTED, UNDER_REVIEW, SUCCESS, FAIL, CANCELLED, REVOKED`)하되 `FAIL`의 의미를 확장한다.

```
JOINED --(submit)--> SUBMITTED --(자동/수동 판정)--> SUCCESS
                                                    \-> UNDER_REVIEW --(관리자 승인)--> SUCCESS
                                                                     \-(관리자 반려)-> FAIL
FAIL --(재제출, now < endsAt)--> SUBMITTED  (새 ZoneEventSubmission row, attemptNo+1)
FAIL --(now >= endsAt)--> FAIL (터미널, 더 이상 재제출 불가)
```

- `submit()`이 허용하는 참여 상태를 `JOINED` 단독 → `JOINED` 또는 `FAIL`로 확장한다. `FAIL`에서 진입할 때만 "재제출" 경로이며, 이때 직전(가장 최신 `attemptNo`) `ZoneEventSubmission.reviewStatus == REJECTED`인지 확인한다(참여 상태가 `FAIL`인 것은 항상 최신 제출이 반려됐다는 뜻이므로 사실상 동치이지만, 방어적으로 명시 검증한다).
- `now >= event.endsAt()`이면 `JOINED`/`FAIL` 어느 쪽이든 제출·재제출을 거부한다(신규 참여도 동일, 아래 3.2).
- join 시 중복 판정에 쓰이는 `OPEN_STATUSES`(`ZoneEventParticipationService`)에 `FAIL`을 추가한다 — 반려된 참여가 있으면 새 참여를 만들지 않고 기존 `participationId`로 재제출하도록 유도한다(409 + `participationId` 반환, 기존 `OpenParticipationExistsException` 재사용).
- `isOpen()`(취소 가능 여부 판정에 쓰임)은 변경하지 않는다 — `FAIL` 참여를 사용자가 임의로 취소하는 기능은 이번 이슈 범위 밖이다.

## 2. 데이터 모델 변경

- `ZoneEventParticipation`: 컬럼 추가 없음. 기존 `currentSubmissionId`(현재 죽은 필드)를 실제로 `linkSubmission()`을 통해 채우도록 배선한다(조회 편의용 projection으로 사용 — 최신 제출 조회 시 `ZoneEventSubmissionRepository.findFirstByParticipation_IdOrderByAttemptNoDesc`를 우선 쓰되, 이 컬럼도 최신값과 항상 일치시킨다).
- `ZoneEventSubmission`: 컬럼 변경 없음(이미 완성된 스키마). `target`(FK, NOT NULL)에 제출 시점에 선택한 타겟을 저장 — join 시 선택한 타겟과 달라도 된다(재제출 시 다른 장소 선택).
- 신규 리포지토리 메서드: `ZoneEventSubmissionRepository.existsByMediaFileKey(String mediaFileKey)` (업로드 재사용 방지, 5절).
- 마이그레이션 불필요 — 모든 컬럼이 이미 존재한다.

## 3. API 설계

### 3.1 `GET /api/v1/zone-events/{eventId}` (기존, 확장)

`ZoneEventDetailResDto`에 추가:

- `slotCode: String?` — `ZoneEvent.slotCode`를 그대로 노출(엔티티엔 이미 있으나 DTO에서 누락돼 있었음).
- `targets: List<AuthTargetDetailResDto>` — 기존 단일 `authTarget` 필드는 하위 호환을 위해 유지하되(첫 번째 ACTIVE 타겟), `targets`는 이 이벤트의 ACTIVE 타겟 전체(`ZoneEventAuthTargetRepository.findByEvent_IdAndStatus`).
- `deadline: OffsetDateTime` — `event.endsAt()`과 동일 값(기존 `endsAt` 필드는 그대로 유지, `deadline`은 재제출 UI가 쓸 명시적 이름).
- `myParticipation: MyParticipationResDto?` — 비로그인 시 `null`. 로그인 시 이 이벤트에서 사용자의 가장 최근 참여(`ZoneEventParticipationRepository.findFirstByEvent_IdAndUserIdOrderByJoinedAtDesc`, 신규 메서드) 1건을 `{participationId, status, canResubmit}`로 반환. 참여 이력이 없으면 `null`.
  - `canResubmit = status == FAIL && now < event.endsAt()`.

### 3.2 `POST /api/v1/zone-events/{eventId}/participations` (기존, 확장)

`ParticipationJoinReqDto`에 `targetId: String(UUID, 필수)` 추가.

`ZoneEventParticipationService.join()` 변경:

1. `now >= event.endsAt()` → 409 `error.zone_event.ended` (신규).
2. 타겟 조회를 `findFirstByEvent_IdAndStatusOrderByCreatedAtAsc` → `findByIdAndEvent_Id(targetId, eventId)` + `status==ACTIVE` 검증으로 교체. 없거나 다른 이벤트 소속·비활성 → 404 `error.zone_event.target_not_found`(기존 키 재사용).
3. GPS 반경 검증은 선택된 타겟 좌표 기준으로 동일하게 수행.
4. 중복 참여 체크(`OPEN_STATUSES`)에 `FAIL` 포함(1절).
5. 나머지(성공 상한, 동시성 재확인) 로직은 그대로.

### 3.3 `POST /api/v1/zone-events/{eventId}/participations/{participationId}/submit` (기존, 대폭 변경)

`ParticipationSubmitReqDto`에 `targetId: String(UUID, 필수)` 추가.

`ZoneEventSubmitService.submit()` 재작성:

1. 참여 조회·소유권 검증은 동일.
2. 상태 검증: `status`가 `JOINED`도 `FAIL`도 아니면 409 `invalid_state`. `FAIL`인 경우 최신 `ZoneEventSubmission.reviewStatus == REJECTED`가 아니면(이론상 항상 참이지만 방어적으로) 409.
3. `now >= event.endsAt()` → 409 `error.zone_event.ended`(반려 후 마감 지나면 재제출도 막힘 — 완료조건 그대로).
4. 타겟 조회를 `findByIdAndEvent_Id(targetId, eventId)` + `ACTIVE` 검증으로 교체(3.2와 동일 규칙). GPS 반경은 이 타겟 좌표 기준.
5. `validateMedia`를 5절 규칙으로 강화.
6. **`attemptNo` 계산**: `submissionRepository.countByParticipation_Id(participationId) + 1`.
7. `ZoneEventSubmission` row 생성(타겟 스냅샷 필드 채움: `placeName/targetLatitude/targetLongitude/radiusM/guideTextSnapshot` = 제출 시점 타겟 값. `submittedAt`은 엔티티 생성자가 이미 `OffsetDateTime.now()`로 채움 — 이게 요구사항 #6의 "서버 수신 시각" 소스). `participation.linkSubmission(submission.getId())` 호출.
8. `participation.submit(...)`은 그대로 두되(상태 `SUBMITTED`로 전이, 참여 자체의 표시용 필드도 최신 제출로 갱신) — 기존 자동판정 분기(AUTO/MANUAL, `capturedTooOld`)는 그대로 유지하되, 판정 결과를 참여뿐 아니라 **`ZoneEventSubmission`에도 반영**한다:
   - 자동 승인 시: `submission.approve(null)`(시스템 자동 승인은 `reviewerId=null`), `participation.markSuccess()`.
   - 아니면: `participation.markUnderReview()` (submission은 생성 시 이미 `UNDER_REVIEW`).
9. 응답 `SubmitResultResDto`에 `submissionId`, `attemptNo` 추가(4.3).

### 3.4 `GET /api/v1/users/me/zone-event-participations` (기존, 확장)

`ParticipationHistoryItemResDto`에 추가:

- `rejectionReason: String?` — 최신 제출이 `REJECTED`일 때만 그 사유(`ZoneEventSubmission.rejectionReason`), 아니면 `null`. 기존 `participation.failReason`(30자 제한)은 더 이상 이 필드의 소스로 쓰지 않는다(4.3 참고).
- `canResubmit: boolean` — 3.1과 동일 규칙.
- `submissions: List<SubmissionHistoryItemResDto>` — `{submissionId, attemptNo, targetId, placeName, mediaUrl, reviewStatus, rejectionReason, submittedAt, reviewedAt}`, `attemptNo` 내림차순. `ZoneEventSubmissionRepository.findByParticipation_IdOrderByAttemptNoDesc` 사용, N+1 방지를 위해 페이지 내 참여 id 목록으로 배치 조회 후 그룹핑(기존 보상 조회 패턴과 동일).

`ZoneEventParticipationQueryService.history()` 변경: 위 필드들을 채우기 위해 `ZoneEventSubmissionRepository`를 주입받는다.

## 4. 관리자 검수 서비스 내부 배선 (신규 API 아님)

`AdminReviewService`는 외부 계약(엔드포인트·요청/응답 DTO) 변경 없음. 내부적으로 `ZoneEventSubmission`도 함께 갱신하도록 고친다:

- `approve()`: `requireStatus(UNDER_REVIEW)` 뒤, 최신 `ZoneEventSubmission`(`findFirstByParticipation_IdOrderByAttemptNoDesc`)을 조회해 `submission.approve(user.id())` 호출 후 기존 `participation.markSuccess()`.
- `reject()`: 동일하게 최신 submission을 찾아 `submission.reject(user.id(), failReason)` 호출 후 `participation.markFail(failReason)`(참여의 `failReason`은 30자 제한이라 UI 요약용으로 계속 씀; 진짜 소스는 submission의 300자 `rejectionReason`).
- 두 메서드 모두 최신 submission이 없으면(이론상 `UNDER_REVIEW` 참여는 항상 submission이 있어야 함 — 이번 변경으로 submit()이 항상 만들기 때문) `IllegalStateException`으로 방어(정상 흐름에서는 도달 불가).

## 5. 업로드 검증 강화 (요구사항 #9)

`ZoneEventSubmitService.validateMedia(mediaFileKey, userId)` 규칙 변경:

1. `FileMetadata` 조회 실패 → 400 `error.zone_event.media.invalid`(기존과 동일).
2. `contentType`이 `image/`로 시작하지 않으면 → 400 `error.zone_event.media.invalid`(기존과 동일).
3. **소유권 검증 강화**: 기존엔 `uploaderId == null`(익명/레거시 업로드)이면 검증을 건너뛰었다. zone-event 제출은 항상 인증된 사용자만 호출 가능하므로(`requireUserId`가 이미 보장), 이 우회를 제거하고 `uploaderId == null`도 `uploaderId != userId`와 동일하게 403 `error.zone_event.media.forbidden`으로 거부한다.
4. **재사용 방지(신규)**: `submissionRepository.existsByMediaFileKey(mediaFileKey)`가 `true`이면(이미 다른 제출에 쓰인 파일) 400 `error.zone_event.media.already_used`(신규 키).
5. **업로드 최신성(신규)**: `file.getCreatedAt()`이 `now`로부터 `zone-event.review.upload-recency-threshold-minutes`(신규 `@Value`, 기본 30분)보다 오래됐으면 400 `error.zone_event.media.stale`(신규 키). 오래된 갤러리 사진 재사용을 억제하되, 정확한 촬영시각 증명은 하지 않는다(범위 밖으로 명시적으로 제외 — 6절).

## 6. 범위 제외

- 업로드 세션·클라이언트 서명·EXIF/디바이스 메타데이터 기반의 실제 "카메라로 방금 찍었다" 증명 — 이슈 본문이 "파일/모바일팀과 별도 계약 필요"라고 명시한 별도 트랙.
- `ParticipationResDto.mediaUrl`이 항상 `null`인 기존 버그성 동작 — 이번 이슈와 무관, 별도 이슈로 분리.
- 관리자 검수 API(`AdminReviewController`)의 엔드포인트·요청/응답 계약 변경 — 내부 배선만 변경(4절).
- `ZoneEventParticipationController`의 `/participations/me`(이벤트별 내 참여 목록) 응답 확장 — 이슈가 명시한 건 `/users/me/zone-event-participations`뿐이므로 손대지 않는다.

## 7. 테스트 계획

- 엔티티: `ZoneEventParticipation.submit()`이 `FAIL`에서도 호출 가능한지(신규), `linkSubmission()` 배선 확인.
- 서비스:
  - `ZoneEventParticipationServiceTest`: `targetId` 필수·존재하지 않음(404)·다른 이벤트 소속(404)·비활성(404), 마감 후 join 거부(409), `FAIL` 참여가 있을 때 재-join 시도 시 409(+participationId).
  - `ZoneEventSubmitServiceTest`: 최초 제출 시 `ZoneEventSubmission` 생성(attemptNo=1) 확인, 반려 후 재제출 시 attemptNo=2 새 row(이전 row 불변) 확인, `UNDER_REVIEW`/`SUCCESS` 상태에서 재제출 시도 409, 마감 후 재제출 409, 다른 이벤트/비활성 타겟으로 제출 404, 파일 재사용 400, 오래된 업로드 400, 업로더 불일치(익명 포함) 403.
  - `AdminReviewServiceTest`: approve/reject가 최신 submission도 함께 갱신하는지.
  - `ZoneEventQueryServiceTest`: `targets`/`slotCode`/`deadline`/`myParticipation`/`canResubmit` 조합.
  - `ZoneEventParticipationQueryServiceTest`: `submissions` 목록·`rejectionReason`·`canResubmit` 조합, N+1 없는지(쿼리 카운트 또는 배치 조회 구조로 확인).
- 컨트롤러: 각 엔드포인트 MockMvc 계약 테스트(요청 필드 추가분 포함).
- `./gradlew check` 통과(ASCII 경로 클론 방식, 기존 관례 그대로).

## 8. 문서화

`openapi3.yaml`의 `GET /zone-events/{eventId}`, `POST .../participations`, `POST .../submit`, `GET /users/me/zone-event-participations` 4개 항목을 위 계약대로 다시 쓴다. 기존 한국어 서술 스타일을 그대로 따르고, 신규 에러 키(`error.zone_event.ended`, `error.zone_event.media.already_used`, `error.zone_event.media.stale`)에 대응하는 응답 예시를 추가한다. 4개 로케일(`messages.properties`, `_en`, `_ja`, `_zh`)에 신규 키 3개를 추가한다.
