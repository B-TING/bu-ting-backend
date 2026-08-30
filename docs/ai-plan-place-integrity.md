# AI 일정 장소 무결성

`POST /api/v1/travels/{travelId}/ai-plans`는 기존 요청 필드와 성공 응답
`TravelPlansResDto`를 유지한다. `selectedPlaces`는 장소 원본이며 장소 이름으로 매칭하지 않는다.

## 식별자와 저장

- `providerPlaceId`에는 관광데이터 검색 API의 `contentId` 문자열을 그대로 넣는다.
- 기존 앱의 `provider=GOOGLE` 표기는 유지한다. 해당 ID를 Google Places ID로 변환하지 않는다.
- 서버는 `(PlaceProvider, contentId)` 복합키로 카탈로그를 만들고 중복 입력과 잘못된 참조를 AI 호출 전에 거부한다.
- LLM 내부 응답은 `date`, `order`, `provider`, `providerPlaceId`, `memo`만 받는다.
- LLM이 추가한 이름·주소·좌표 필드는 무시된다. 저장 및 공개 응답 값은 요청 원본을 사용한다.
- 전체 날짜의 결과를 합쳐 입력 키 집합과 정확하게 비교한다. 각 키의 출현 횟수는 반드시 1이다.
- 전체 결과 검증이 끝나기 전에는 Plan/PlanPlace 저장을 호출하지 않는다. 기존 저장 트랜잭션은 유지한다.

## 실패 사유

입력 오류는 HTTP 400, 생성된 AI 결과 오류는 HTTP 502다.
기존 `ApiResponse` 오류 형식은 유지하며 내부 예외에 reason과 관련 복합키 집합을 보관한다.
사용자 입력이나 AI 원문은 로그에 출력하지 않는다.

| 내부 사유 | 의미 |
| --- | --- |
| MISSING_SELECTED_PLACE | 선택한 장소 누락 |
| UNEXPECTED_PLACE | 선택하지 않은 복합키 등장 |
| DUPLICATED_PLACE | 입력 또는 전체 일정에 동일 키 중복 |
| INVALID_PLACE_REFERENCE | provider/ID 또는 저장할 장소 정보가 유효하지 않음 |
| INVALID_SCHEDULE | 날짜·순서·memo 형식 오류 |

자동 LLM 재시도는 추가하지 않았다. 실패한 결과는 저장하지 않고 명확한 오류로 종료한다.

## Pace와 숙소

서버에 BALANCED를 하루 2곳으로 자르는 로직은 없었다. Pace는 날짜별 분배와 밀도에만 사용한다.
예를 들어 3일 8곳이면 2/3/3 등으로 배치해야 하며 2/2/2 결과는 검증에서 거부된다.
선택한 장소가 날짜보다 적으면 일부 날짜가 빈 배열일 수 있지만 동일 장소를 임의로 재사용하지 않는다.

장소의 주소와 좌표를 프롬프트에 전달한다. 예약 숙소는 이름만 주어지므로 정확한 좌표·이동 시간을
계산하는 기능은 없다. 숙소 이름과 권역은 LLM의 동선 참고 정보이며 선택 장소에 없으면 추가할 수 없다.

## 관광데이터와의 교차 검증 범위

현재 Place 계층은 Tour API 검색 결과를 반환하며 provider별 장소 원장을 DB에 보관하지 않는다.
기존 상세 조회도 contentTypeId를 필요로 하고 provider+ID 단일 조회 계약은 없다.
따라서 Google Places 조회로 숫자형 contentId를 검증하거나 임의 FK를 추가하지 않는다.
이 수정은 요청 스냅샷의 형식과 AI 응답 참조의 무결성을 검증한다. 악의적인 클라이언트가 유효한
contentId에 잘못된 장소 정보를 붙여 보낸 경우까지 관광데이터 원본과 대조하는 기능은 아니다.
그 수준의 검증에는 관광데이터 contentId 기반 원본 조회 또는 서버가 발급한 검색 결과 서명이 필요하다.
