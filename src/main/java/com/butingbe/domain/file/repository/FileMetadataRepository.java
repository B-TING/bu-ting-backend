package com.butingbe.domain.file.repository;

import com.butingbe.domain.file.entity.FileMetadata;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
  Optional<FileMetadata> findByObjectKey(String objectKey);
}
