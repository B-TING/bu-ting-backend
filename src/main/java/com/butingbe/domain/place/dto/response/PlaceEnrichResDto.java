package com.butingbe.domain.place.dto.response;

/**
 * 인기도 보강 결과 요약.
 *
 * <p>{@code withoutRating}은 조회에는 성공했지만 구글에 평점이 없던 장소, {@code failed}는 외부 호출이 실패한 장소다. 전자는 다시 조회할
 * 필요가 없고 후자는 다음 호출에서 재시도된다.
 */
public record PlaceEnrichResDto(int targeted, int enriched, int withoutRating, int failed) {}
