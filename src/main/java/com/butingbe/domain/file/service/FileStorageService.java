package com.butingbe.domain.file.service;

import com.butingbe.domain.file.dto.FileUploadResDto;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  /** uploaderId는 인증된 업로더(없으면 null). 저장 시 파일 소유자로 기록된다. */
  FileUploadResDto upload(MultipartFile file, UUID uploaderId);

  String getPresignedUrl(String fileKey);

  void delete(String fileKey);
}
