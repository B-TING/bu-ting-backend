package com.butingbe.domain.file.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileMetadata extends TimestampEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "object_key", nullable = false, unique = true, length = 500)
  private String objectKey;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "media_type", nullable = false, length = 20)
  private String mediaType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(nullable = false, length = 100)
  private String bucket;

  @Column(name = "uploader_id")
  private UUID uploaderId;

  @Builder
  public FileMetadata(
      String objectKey,
      String originalFileName,
      String contentType,
      String mediaType,
      long fileSize,
      String bucket,
      UUID uploaderId) {
    this.objectKey = objectKey;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.mediaType = mediaType;
    this.fileSize = fileSize;
    this.bucket = bucket;
    this.uploaderId = uploaderId;
  }
}
