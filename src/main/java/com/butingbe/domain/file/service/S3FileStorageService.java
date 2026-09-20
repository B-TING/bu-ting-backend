package com.butingbe.domain.file.service;

import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.InvalidRequestException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "video/mp4", "video/quicktime");

  private final S3Client s3Client;
  private final FileMetadataRepository fileMetadataRepository;
  private final S3Presigner s3Presigner;

  @Value("${file-storage.s3.bucket}")
  private String bucket;

  @Value("${file-storage.s3.max-file-size:52428800}")
  private long maxFileSize;

  @Value("${file-storage.s3.key-prefix:uploads}")
  private String keyPrefix;

  @Value("${file-storage.s3.presigned-url-expiration:3600}")
  private long presignedUrlExpiration;

  @Override
  public FileUploadResDto upload(MultipartFile file, UUID uploaderId) {
    validate(file);
    String contentType = file.getContentType();
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String key =
        keyPrefix
            + "/"
            + (contentType.startsWith("video/") ? "videos" : "images")
            + "/"
            + UUID.randomUUID()
            + (extension == null ? "" : "." + extension);
    try {
      s3Client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
          RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    } catch (IOException exception) {
      throw new IllegalStateException("파일을 읽을 수 없습니다.", exception);
    }
    try {
      fileMetadataRepository.save(
          FileMetadata.builder()
              .objectKey(key)
              .originalFileName(file.getOriginalFilename())
              .contentType(contentType)
              .mediaType(contentType.startsWith("video/") ? "VIDEO" : "IMAGE")
              .fileSize(file.getSize())
              .bucket(bucket)
              .uploaderId(uploaderId)
              .build());
    } catch (RuntimeException exception) {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
      throw exception;
    }
    String url = createPresignedGetUrl(key);
    return new FileUploadResDto(key, file.getOriginalFilename(), contentType, file.getSize(), url);
  }

  @Override
  public void delete(String fileKey, UUID requesterId) {
    validateFileKey(fileKey);
    // 소유자 확인이 먼저다. S3 객체를 지운 뒤에 거절하면 이미 되돌릴 수 없다.
    FileMetadata metadata =
        fileMetadataRepository
            .findByObjectKey(fileKey)
            .orElseThrow(() -> new ResourceNotFoundException("error.file.not_found"));
    // 인증 필수 이전에 올라간 파일은 uploader_id가 비어 있다. 그 경우도 소유자 불일치로 막는다.
    if (!requesterId.equals(metadata.getUploaderId())) {
      throw new ForbiddenException("error.file.forbidden");
    }
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build());
    fileMetadataRepository.delete(metadata);
  }

  @Override
  public String getPresignedUrl(String fileKey) {
    validateFileKey(fileKey);
    fileMetadataRepository
        .findByObjectKey(fileKey)
        .orElseThrow(() -> new InvalidRequestException("error.file.not_found"));
    return createPresignedGetUrl(fileKey);
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidRequestException("error.file.empty");
    }
    if (file.getSize() > maxFileSize) {
      throw new InvalidRequestException("error.file.too_large");
    }
    if (!ALLOWED_TYPES.contains(file.getContentType())) {
      throw new InvalidRequestException("error.file.unsupported_type");
    }
  }

  private void validateFileKey(String fileKey) {
    if (!StringUtils.hasText(fileKey) || fileKey.contains("..") || fileKey.startsWith("/")) {
      throw new InvalidRequestException("error.file.invalid_key");
    }
  }

  private String createPresignedGetUrl(String key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
            .getObjectRequest(getObjectRequest)
            .build();
    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }
}
