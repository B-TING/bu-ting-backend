package com.butingbe.domain.travelteam.entity;

import com.butingbe.domain.temp.entity.TravelTemp;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
public class TravelInvite {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id", nullable = false)
    private TravelTemp travel;  // 임시 Travel 연결

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(nullable = false)
    private OffsetDateTime expiredAt;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(this.expiredAt);
    }

    @Builder
    public TravelInvite(TravelTemp travel, String token, OffsetDateTime expiredAt) {
        this.travel = travel;
        this.token = token;
        this.expiredAt = expiredAt;
    }


}
