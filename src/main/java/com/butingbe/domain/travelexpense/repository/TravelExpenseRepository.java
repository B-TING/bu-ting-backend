package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface TravelExpenseRepository
    extends JpaRepository<TravelExpense, UUID>, JpaSpecificationExecutor<TravelExpense> {

  @Override
  @EntityGraph(attributePaths = "payer")
  Page<TravelExpense> findAll(Specification<TravelExpense> specification, Pageable pageable);

  @EntityGraph(attributePaths = {"payer", "createdBy"})
  Optional<TravelExpense> findByIdAndTravel_Id(UUID expenseId, UUID travelId);

  @Query(
      """
      select e.currency as currency,
             sum(e.amount) as totalAmount,
             count(e) as expenseCount
      from TravelExpense e
      where e.travel.id = :travelId
        and e.spentAt >= coalesce(:from, e.spentAt)
        and e.spentAt <= coalesce(:to, e.spentAt)
      group by e.currency
      order by e.currency
      """)
  List<CurrencyTotal> summarizeCurrencies(
      @Param("travelId") UUID travelId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      """
      select e.currency as currency,
             e.category as category,
             sum(e.amount) as amount,
             count(e) as expenseCount
      from TravelExpense e
      where e.travel.id = :travelId
        and e.spentAt >= coalesce(:from, e.spentAt)
        and e.spentAt <= coalesce(:to, e.spentAt)
      group by e.currency, e.category
      order by e.currency, e.category
      """)
  List<CategoryTotal> summarizeCategories(
      @Param("travelId") UUID travelId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      """
      select e.currency as currency,
             e.payer.id as userId,
             e.payer.nickname as nickname,
             sum(e.amount) as amount
      from TravelExpense e
      where e.travel.id = :travelId
        and e.spentAt >= coalesce(:from, e.spentAt)
        and e.spentAt <= coalesce(:to, e.spentAt)
      group by e.currency, e.payer.id, e.payer.nickname
      order by e.currency, e.payer.nickname
      """)
  List<MemberAmount> summarizePaidAmounts(
      @Param("travelId") UUID travelId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  interface CurrencyTotal {

    String getCurrency();

    long getTotalAmount();

    long getExpenseCount();
  }

  interface CategoryTotal {

    String getCurrency();

    ExpenseCategory getCategory();

    long getAmount();

    long getExpenseCount();
  }

  interface MemberAmount {

    String getCurrency();

    UUID getUserId();

    String getNickname();

    long getAmount();
  }
}
