package com.butingbe.domain.file.service;

import com.butingbe.domain.file.dto.FileUploadResDto;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
  FileUploadResDto upload(MultipartFile file);

  void delete(String fileKey);
}
