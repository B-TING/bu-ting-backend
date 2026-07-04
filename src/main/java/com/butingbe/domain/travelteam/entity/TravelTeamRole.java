package com.butingbe.domain.travelteam.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TravelTeamRole {
  LEADER("leader"),
  MEMBER("member");

  private final String description;
}
