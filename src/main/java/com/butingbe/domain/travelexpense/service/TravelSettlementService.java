package com.butingbe.domain.travelexpense.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse.MemberSummary;
import com.butingbe.domain.travelexpense.dto.response.TravelSettlementResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelSettlementResponse.Transfer;
import com.butingbe.domain.travelexpense.entity.TravelSettlement;
import com.butingbe.domain.travelexpense.entity.TravelSettlementTransfer;
import com.butingbe.domain.travelexpense.repository.TravelSettlementRepository;
import com.butingbe.domain.travelexpense.repository.TravelSettlementTransferRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelSettlementService {

  private final TravelRepository travelRepository;
  private final TravelExpenseService travelExpenseService;
  private final TravelSettlementRepository travelSettlementRepository;
  private final TravelSettlementTransferRepository transferRepository;
  private final TravelMemberAuthorization travelMemberAuthorization;
  private final UserRepository userRepository;

  public TravelSettlementResponse getSettlement(
      AuthenticatedUser authenticatedUser, UUID travelId) {
    validateAuthenticated(authenticatedUser);
    requireTravel(travelId);
    travelMemberAuthorization.validateMember(travelId, authenticatedUser.id());

    return travelSettlementRepository
        .findByTravel_Id(travelId)
        .map(this::toConfirmedResponse)
        .orElseGet(() -> previewSettlement(authenticatedUser, travelId));
  }

  @Transactional
  public TravelSettlementResponse confirmSettlement(
      AuthenticatedUser authenticatedUser, UUID travelId) {
    validateAuthenticated(authenticatedUser);
    Travel travel =
        travelRepository
            .findByIdForUpdate(travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
    TravelMember leader =
        travelMemberAuthorization.requireLeader(
            travelId,
            authenticatedUser.id(),
            "Only the travel leader can confirm settlement.");

    return travelSettlementRepository
        .findByTravel_Id(travelId)
        .map(this::toConfirmedResponse)
        .orElseGet(
            () -> {
              List<Transfer> preview = calculateTransfers(authenticatedUser, travelId);
              TravelSettlement settlement =
                  travelSettlementRepository.save(
                      TravelSettlement.builder()
                          .travel(travel)
                          .confirmedBy(leader.getUser())
                          .build());
              Map<UUID, User> users = usersById(preview);
              List<TravelSettlementTransfer> transfers =
                  preview.stream()
                      .map(
                          transfer ->
                              TravelSettlementTransfer.builder()
                                  .settlement(settlement)
                                  .currency(transfer.currency())
                                  .fromUser(requireUser(users, transfer.senderId()))
                                  .toUser(requireUser(users, transfer.receiverId()))
                                  .amount(transfer.amount())
                                  .build())
                      .toList();
              transferRepository.saveAll(transfers);
              transferRepository.flush();
              return TravelSettlementResponse.confirmed(settlement, transfers);
            });
  }

  private TravelSettlementResponse previewSettlement(
      AuthenticatedUser authenticatedUser, UUID travelId) {
    return TravelSettlementResponse.preview(
        travelId, calculateTransfers(authenticatedUser, travelId));
  }

  private List<Transfer> calculateTransfers(
      AuthenticatedUser authenticatedUser, UUID travelId) {
    TravelExpenseSummaryResponse summary =
        travelExpenseService.getExpenseSummary(authenticatedUser, travelId, null, null);
    List<Transfer> transfers = new ArrayList<>();
    summary.currencySummaries().forEach(
        currency ->
            transfers.addAll(
                calculateCurrencyTransfers(currency.currency(), currency.memberSummaries())));
    return List.copyOf(transfers);
  }

  static List<Transfer> calculateCurrencyTransfers(
      String currency, List<MemberSummary> members) {
    List<Position> debtors =
        members.stream()
            .filter(member -> member.balance() < 0)
            .map(member -> new Position(member, -member.balance()))
            .sorted(Position.ORDER)
            .toList();
    List<Position> creditors =
        members.stream()
            .filter(member -> member.balance() > 0)
            .map(member -> new Position(member, member.balance()))
            .sorted(Position.ORDER)
            .toList();

    List<Transfer> transfers = new ArrayList<>();
    int debtorIndex = 0;
    int creditorIndex = 0;
    while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
      Position debtor = debtors.get(debtorIndex);
      Position creditor = creditors.get(creditorIndex);
      long amount = Math.min(debtor.remaining, creditor.remaining);
      transfers.add(
          new Transfer(
              currency,
              debtor.member.memberId(),
              debtor.member.nickname(),
              creditor.member.memberId(),
              creditor.member.nickname(),
              amount));
      debtor.remaining -= amount;
      creditor.remaining -= amount;
      if (debtor.remaining == 0) {
        debtorIndex++;
      }
      if (creditor.remaining == 0) {
        creditorIndex++;
      }
    }
    if (debtorIndex != debtors.size() || creditorIndex != creditors.size()) {
      throw new IllegalStateException("Settlement balances are inconsistent.");
    }
    return List.copyOf(transfers);
  }

  private TravelSettlementResponse toConfirmedResponse(TravelSettlement settlement) {
    return TravelSettlementResponse.confirmed(
        settlement,
        transferRepository
            .findBySettlement_IdOrderByCurrencyAscAmountDescFromUser_NicknameAscFromUser_IdAsc(
                settlement.getId()));
  }

  private Map<UUID, User> usersById(List<Transfer> transfers) {
    List<UUID> userIds =
        transfers.stream()
            .flatMap(transfer -> Stream.of(transfer.senderId(), transfer.receiverId()))
            .distinct()
            .toList();
    Map<UUID, User> users = new LinkedHashMap<>();
    userRepository.findAllById(userIds).forEach(user -> users.put(user.getId(), user));
    return users;
  }

  private User requireUser(Map<UUID, User> users, UUID userId) {
    User user = users.get(userId);
    if (user == null) {
      throw new IllegalStateException("Settlement user not found.");
    }
    return user;
  }

  private Travel requireTravel(UUID travelId) {
    return travelRepository
        .findById(travelId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
  }

  private void validateAuthenticated(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
  }

  private static class Position {

    private static final Comparator<Position> ORDER =
        Comparator.comparingLong((Position position) -> position.remaining)
            .reversed()
            .thenComparing(position -> position.member.nickname())
            .thenComparing(position -> position.member.memberId());

    private final MemberSummary member;
    private long remaining;

    private Position(MemberSummary member, long remaining) {
      this.member = member;
      this.remaining = remaining;
    }
  }
}
