package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.RewardCatalog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RewardCatalogRepository
    extends JpaRepository<RewardCatalog, UUID>, JpaSpecificationExecutor<RewardCatalog> {

  Optional<RewardCatalog> findByCode(String code);

  boolean existsByCode(String code);
}
