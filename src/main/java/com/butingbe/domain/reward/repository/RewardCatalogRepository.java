package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.RewardCatalog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardCatalogRepository extends JpaRepository<RewardCatalog, UUID> {

  Optional<RewardCatalog> findByCode(String code);

  boolean existsByCode(String code);
}
