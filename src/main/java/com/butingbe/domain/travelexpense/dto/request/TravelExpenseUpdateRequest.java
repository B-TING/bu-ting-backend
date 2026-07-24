package com.butingbe.domain.travelexpense.dto.request;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelExpenseUpdateRequest(
    @NotBlank @Size(max = 50) String title,
    @NotNull @Positive Long amount,
    @Size(min = 3, max = 3) String currency,
    @NotNull ExpenseCategory category,
    @JsonAlias("payerUserId") @NotNull UUID payerId,
    @JsonAlias("participantUserIds") @NotEmpty List<@NotNull UUID> participantIds,
    @NotNull LocalDateTime spentAt,
    @Size(max = 500) String memo) {

  @JsonIgnore
  public UUID payerUserId() {
    return payerId;
  }

  @JsonIgnore
  public List<UUID> participantUserIds() {
    return participantIds;
  }
}
