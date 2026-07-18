package com.butingbe.domain.storage.repository;

import com.butingbe.domain.storage.entity.StorageLocation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, UUID> {}
