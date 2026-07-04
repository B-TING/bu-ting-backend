package com.butingbe.domain.travelteam.entity;

import com.butingbe.domain.temp.entity.TravelTemp;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class TravelMember {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id")
    private Travel travel;  // 임시 Travel 연결

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private TravelTeamRole role;


}
