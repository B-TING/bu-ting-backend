package com.butingbe.domain.travelexpense.dto.request;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelExpenseCreateRequest(
    @NotBlank @Size(max = 50) String title,
    @NotNull @Positive Long amount,
    @Size(min = 3, max = 3) String currency,
    @NotNull ExpenseCategory category,
    @NotNull UUID payerUserId,
    @NotEmpty List<@NotNull UUID> participantUserIds,
    @NotNull LocalDateTime spentAt,
    @Size(max = 500) String memo) {}
