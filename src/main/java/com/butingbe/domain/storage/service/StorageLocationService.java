package com.butingbe.domain.storage.service;

import com.butingbe.domain.storage.dto.StorageLocationResDto;
import com.butingbe.domain.storage.dto.StorageLocationSearchReqDto;
import java.util.List;

public interface StorageLocationService {
  List<StorageLocationResDto> search(StorageLocationSearchReqDto request);
}
