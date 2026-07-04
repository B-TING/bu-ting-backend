package com.butingbe.domain.travelteam.service;

import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.entity.TravelInvite;
import com.butingbe.domain.travelteam.repository.TravelInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TravelTeamService {

    private final TravelInviteRepository travelInviteRepository;

    public InviteVerificationResponse verifyToken(String token) {

        TravelInvite invite = travelInviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 링크입니다."));

        if (invite.isExpired()) {
            throw new IllegalStateException("만료된 초대 링크입니다.");
        }

        if (invite.getUsed()) {
            throw new IllegalStateException("이미 사용된 초대 링크입니다.");
        }
        invite.setUsed(true);


        return new InviteVerificationResponse(invite.getTravel().getId(), invite.getTravel().getTitle(), true);
    }
}
