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
}
