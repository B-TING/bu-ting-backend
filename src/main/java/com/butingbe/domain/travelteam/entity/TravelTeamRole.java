package com.butingbe.domain.temp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TravelTeamRole {

    LEADER("방장"),
    MEMBER("팀원");

    private final String description;

}
