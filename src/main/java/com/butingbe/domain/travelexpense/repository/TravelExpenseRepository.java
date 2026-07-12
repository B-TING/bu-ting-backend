package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelExpense;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelExpenseRepository extends JpaRepository<TravelExpense, UUID> {}
