package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelExpenseShareRepository extends JpaRepository<TravelExpenseShare, UUID> {

  List<TravelExpenseShare> findByExpense_IdOrderByIdAsc(UUID expenseId);
}
