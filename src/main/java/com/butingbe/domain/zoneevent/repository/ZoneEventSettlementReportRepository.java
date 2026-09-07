package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventSettlementReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventSettlementReportRepository
    extends JpaRepository<ZoneEventSettlementReport, UUID> {}
