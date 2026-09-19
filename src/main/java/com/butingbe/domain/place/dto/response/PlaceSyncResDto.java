package com.butingbe.domain.place.dto.response;

/**
 * 장소 동기화 결과 요약.
 *
 * <p>{@code skipped}는 contentId나 이름이 없어 저장할 수 없던 항목, {@code failedPages}는 외부 호출이 실패해 건너뛴 페이지다. 둘을
 * 나눠 보고해야 원인이 데이터인지 네트워크인지 구분된다.
 */
public record PlaceSyncResDto(
    int created, int updated, int skipped, int failedPages, int totalCount) {}
