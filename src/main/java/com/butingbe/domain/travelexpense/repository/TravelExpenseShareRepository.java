package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelExpenseShareRepository extends JpaRepository<TravelExpenseShare, UUID> {

  @EntityGraph(attributePaths = "user")
  List<TravelExpenseShare> findByExpense_IdOrderByIdAsc(UUID expenseId);

  void deleteByExpense_Id(UUID expenseId);

  @Query(
      """
      select s.expense.id as expenseId, count(s) as participantCount
      from TravelExpenseShare s
      where s.expense.id in :expenseIds
      group by s.expense.id
      """)
  List<ExpenseParticipantCount> countParticipantsByExpenseIds(
      @Param("expenseIds") List<UUID> expenseIds);

  @Query(
      """
      select s.expense.currency as currency,
             s.user.id as userId,
             s.user.nickname as nickname,
             sum(s.shareAmount) as amount
      from TravelExpenseShare s
      where s.expense.travel.id = :travelId
        and s.expense.spentAt >= coalesce(:from, s.expense.spentAt)
        and s.expense.spentAt <= coalesce(:to, s.expense.spentAt)
      group by s.expense.currency, s.user.id, s.user.nickname
      order by s.expense.currency, s.user.nickname
      """)
  List<MemberShareAmount> summarizeShareAmounts(
      @Param("travelId") UUID travelId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  interface ExpenseParticipantCount {

    UUID getExpenseId();

    long getParticipantCount();
  }

  interface MemberShareAmount {

    String getCurrency();

    UUID getUserId();

    String getNickname();

    long getAmount();
  }
}
