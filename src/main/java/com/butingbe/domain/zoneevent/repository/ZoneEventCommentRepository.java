package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ZoneEventCommentRepository
    extends JpaRepository<ZoneEventComment, UUID>, JpaSpecificationExecutor<ZoneEventComment> {

  List<ZoneEventComment> findByParticipationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
      UUID participationId);
}
