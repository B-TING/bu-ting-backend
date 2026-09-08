package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventAuthTargetRepository extends JpaRepository<ZoneEventAuthTarget, UUID> {

  /** 이벤트의 선택 장소 전체(취소 포함). 상세·목록 조회용. */
  List<ZoneEventAuthTarget> findByEvent_Id(UUID eventId);

  /**
   * 이벤트의 ACTIVE 타겟 전체(참여자가 고를 수 있는 목록). createdAt 오름차순으로 고정해, 하위 호환 단수 필드(authTarget)로 쓰는 첫 원소가
   * {@link #findFirstByEvent_IdAndStatusOrderByCreatedAtAsc}와 항상 같은 타겟을 가리키게 한다.
   */
  List<ZoneEventAuthTarget> findByEvent_IdAndStatusOrderByCreatedAtAsc(
      UUID eventId, ZoneEventTargetStatus status);

  /**
   * 이벤트에서 참여·제출에 실제로 쓸 대표 타겟 하나. 오늘은 이벤트당 ACTIVE 타겟이 정확히 하나뿐이라 안전하다. 여러 개 중 사용자가 직접 고르는 흐름은 후속
   * 이슈(참여·제출 API)에서 다룬다.
   */
  Optional<ZoneEventAuthTarget> findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
      UUID eventId, ZoneEventTargetStatus status);

  /** eventId 범위로 스코프된 타겟 조회(다른 이벤트의 타겟 접근 방지). */
  Optional<ZoneEventAuthTarget> findByIdAndEvent_Id(UUID id, UUID eventId);

  /** 같은 이벤트에서 같은 관광지 contentId로 이미 등록된 타겟이 있는지(중복 등록 방지). */
  Optional<ZoneEventAuthTarget> findByEvent_IdAndPlaceContentIdAndStatus(
      UUID eventId, String placeContentId, ZoneEventTargetStatus status);
}
