package com.butingbe.domain.route.dto.response;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import java.util.UUID;

/**
 * 리부트로 다시 짠 남은 일정.
 *
 * <p>{@code orderedPoints}는 현재 위치에서 시작해 남은 시간에 담기는 장소들의 순서다(현재 위치 포함). {@code legs}는 그 사이 이동 구간이다.
 *
 * <p>장소가 빠지는 이유는 셋이다. {@code visitedPlaceIds}는 이미 다녀온 곳, {@code droppedForTimePlaceIds}는 남은 시간에 못
 * 담은 곳, {@code skippedNoCoordinatesPlaceIds}는 좌표가 없어 계산할 수 없는 곳. 이유가 다르므로 나눠 알린다.
 */
public record TravelRebootResDto(
    TransportType transportType,
    List<RoutePoint> orderedPoints,
    List<RouteLeg> legs,
    int totalTravelMinutes,
    int totalStayMinutes,
    int totalMinutes,
    int availableMinutes,
    List<UUID> reachablePlaceIds,
    List<UUID> droppedForTimePlaceIds,
    List<UUID> visitedPlaceIds,
    List<UUID> skippedNoCoordinatesPlaceIds) {}
