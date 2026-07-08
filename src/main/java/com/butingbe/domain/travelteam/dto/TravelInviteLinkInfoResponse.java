package com.butingbe.domain.travelteam.dto;

import java.time.OffsetDateTime;

public record TravelInviteLinkInfoResponse(String inviteLink, OffsetDateTime expiredAt) {}
