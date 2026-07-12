package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelExpenseShareRepository extends JpaRepository<TravelExpenseShare, UUID> {

  List<TravelExpenseShare> findByExpense_IdOrderByIdAsc(UUID expenseId);

  @Query(
      """
      select s.expense.id as expenseId, count(s) as participantCount
      from TravelExpenseShare s
      where s.expense.id in :expenseIds
      group by s.expense.id
      """)
  List<ExpenseParticipantCount> countParticipantsByExpenseIds(
      @Param("expenseIds") List<UUID> expenseIds);

  interface ExpenseParticipantCount {

    UUID getExpenseId();

    long getParticipantCount();
  }
}
