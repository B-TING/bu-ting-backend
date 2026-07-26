package com.butingbe.domain.file.controller;

import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {
  private final FileStorageService fileStorageService;

  @PostMapping(consumes = "multipart/form-data")
  public ResponseEntity<FileUploadResDto> upload(@RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(fileStorageService.upload(file));
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam String fileKey) {
    fileStorageService.delete(fileKey);
    return ResponseEntity.noContent().build();
  }
}
