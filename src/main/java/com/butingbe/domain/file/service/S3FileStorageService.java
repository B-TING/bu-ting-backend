package com.butingbe.domain.file.service;

import com.butingbe.domain.file.dto.FileUploadResDto;
import java.io.IOException;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "video/mp4", "video/quicktime");

  private final S3Client s3Client;

  @Value("${file-storage.s3.bucket}")
  private String bucket;

  @Value("${file-storage.s3.region}")
  private String region;

  @Value("${file-storage.s3.max-file-size:52428800}")
  private long maxFileSize;

  @Value("${file-storage.s3.key-prefix:uploads}")
  private String keyPrefix;

  @Override
  public FileUploadResDto upload(MultipartFile file) {
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
    String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    return new FileUploadResDto(key, file.getOriginalFilename(), contentType, file.getSize(), url);
  }

  @Override
  public void delete(String fileKey) {
    if (!StringUtils.hasText(fileKey) || fileKey.contains("..") || fileKey.startsWith("/")) {
      throw new IllegalArgumentException("유효하지 않은 파일 키입니다.");
    }
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build());
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("파일이 비어 있습니다.");
    }
    if (file.getSize() > maxFileSize) {
      throw new IllegalArgumentException("파일 크기 제한을 초과했습니다.");
    }
    if (!ALLOWED_TYPES.contains(file.getContentType())) {
      throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
    }
  }
}
