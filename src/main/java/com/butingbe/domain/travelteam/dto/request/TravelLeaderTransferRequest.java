package com.butingbe.domain.travelteam.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TravelLeaderTransferRequest(
    @NotNull(message = "New leader user id is required.") UUID newLeaderUserId) {}
