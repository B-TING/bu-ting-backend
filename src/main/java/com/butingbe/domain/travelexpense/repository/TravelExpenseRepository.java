package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelExpense;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface TravelExpenseRepository
    extends JpaRepository<TravelExpense, UUID>, JpaSpecificationExecutor<TravelExpense> {

  @Override
  @EntityGraph(attributePaths = "payer")
  Page<TravelExpense> findAll(Specification<TravelExpense> specification, Pageable pageable);

  @EntityGraph(attributePaths = {"payer", "createdBy"})
  Optional<TravelExpense> findByIdAndTravel_Id(UUID expenseId, UUID travelId);
}
