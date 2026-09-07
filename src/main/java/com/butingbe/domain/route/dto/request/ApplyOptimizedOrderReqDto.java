package com.butingbe.domain.route.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 최적화 결과를 일정에 반영하는 요청.
 *
 * <p>최적화 응답의 {@code orderedPoints}에서 얻은 장소 id를 그 순서대로 담는다. 좌표가 없어 최적화에서 빠졌던 장소는 넣지 않아도 되며, 서버가 기존
 * 순서를 유지한 채 뒤에 붙인다.
 */
public record ApplyOptimizedOrderReqDto(
    @NotEmpty(message = "Plan place ids are required.") List<@NotNull UUID> planPlaceIds) {}
