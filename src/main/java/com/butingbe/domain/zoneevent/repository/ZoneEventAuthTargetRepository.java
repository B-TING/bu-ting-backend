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
   * 이벤트에서 참여·제출에 실제로 쓸 대표 타겟 하나. 오늘은 이벤트당 ACTIVE 타겟이 정확히 하나뿐이라 안전하다. 여러 개 중 사용자가 직접 고르는 흐름은 후속
   * 이슈(참여·제출 API)에서 다룬다.
   */
  Optional<ZoneEventAuthTarget> findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
      UUID eventId, ZoneEventTargetStatus status);
}
