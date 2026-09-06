package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserZoneTitleRepository extends JpaRepository<UserZoneTitle, UUID> {

  boolean existsByUserIdAndTitleDef_Id(UUID userId, UUID titleDefId);

  List<UserZoneTitle> findByUserIdAndRevokedAtIsNull(UUID userId);

  Optional<UserZoneTitle> findByUserIdAndEquippedIsTrue(UUID userId);

  List<UserZoneTitle> findByUserIdInAndEquippedIsTrue(Collection<UUID> userIds);

  long countByUserIdAndEquippedIsTrue(UUID userId);
}
