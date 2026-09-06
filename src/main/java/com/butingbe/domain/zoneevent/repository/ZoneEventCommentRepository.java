package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventCommentRepository extends JpaRepository<ZoneEventComment, UUID> {

  List<ZoneEventComment> findByParticipationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
      UUID participationId);
}
