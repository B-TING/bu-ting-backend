package com.butingbe.domain.file.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  public ResponseEntity<FileUploadResDto> upload(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(fileStorageService.upload(file, requireUserId(user)));
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String fileKey) {
    fileStorageService.delete(fileKey, requireUserId(user));
    return ResponseEntity.noContent().build();
  }

  /** 소유자를 기록하고 대조하려면 사용자 id가 있어야 한다. id 없는 개발 관리자 토큰도 여기서 걸린다. */
  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
