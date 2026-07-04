package com.butingbe.domain.travelteam.entity;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.Travel;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TravelInvite {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id", nullable = false)
    private Travel travel;  // 임시 Travel 연결

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
    public TravelInvite(Travel travel, String token, OffsetDateTime expiredAt) {
        this.travel = travel;
        this.token = token;
        this.expiredAt = expiredAt;
    }


}
