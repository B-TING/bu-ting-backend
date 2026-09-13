package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventSubmissionRepository extends JpaRepository<ZoneEventSubmission, UUID> {

  List<ZoneEventSubmission> findByParticipation_IdOrderByAttemptNoDesc(UUID participationId);

  Optional<ZoneEventSubmission> findFirstByParticipation_IdOrderByAttemptNoDesc(
      UUID participationId);

  long countByParticipation_Id(UUID participationId);

  long countByParticipation_Event_Id(UUID eventId);

  /** 파일이 이미 다른 제출에 쓰였는지(재사용 방지). */
  boolean existsByMediaFileKey(String mediaFileKey);

  /** 이력 페이지의 참여 id 목록으로 배치 조회(N+1 방지). */
  List<ZoneEventSubmission> findByParticipation_IdIn(List<UUID> participationIds);
}
