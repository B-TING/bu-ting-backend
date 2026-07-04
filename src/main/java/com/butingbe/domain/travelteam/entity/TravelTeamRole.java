package com.butingbe.domain.travelteam.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TravelTeamRole {

    LEADER("방장"),
    MEMBER("팀원");

    private final String description;

}
