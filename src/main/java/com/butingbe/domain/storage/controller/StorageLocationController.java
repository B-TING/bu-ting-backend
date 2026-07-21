package com.butingbe.domain.storage.controller;

import com.butingbe.domain.storage.dto.StorageLocationResDto;
import com.butingbe.domain.storage.dto.StorageLocationSearchReqDto;
import com.butingbe.domain.storage.service.StorageLocationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/storage-locations")
@RequiredArgsConstructor
public class StorageLocationController {
  private final StorageLocationService storageLocationService;

  @GetMapping
  public ResponseEntity<List<StorageLocationResDto>> search(
      @ModelAttribute @Valid StorageLocationSearchReqDto request) {
    return ResponseEntity.ok(storageLocationService.search(request));
  }
}
