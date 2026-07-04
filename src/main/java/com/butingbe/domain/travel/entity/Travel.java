package com.butingbe.domain.travel.entity;


import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity  // 연관관계용 임시 Travel 테이블
@Getter
public class Travel {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    private String title;


}
