package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {}
