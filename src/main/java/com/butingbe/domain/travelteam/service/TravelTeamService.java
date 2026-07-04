package com.butingbe.domain.travelteam.service;

import com.butingbe.domain.temp.entity.TravelTemp;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.entity.TravelInvite;
import com.butingbe.domain.travelteam.repository.TravelInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    public String createInviteLink(Long teamId) {
        // 해당 팀이 존재하는지 확인
        TravelTemp team = travelTeampRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀입니다."));

        // 무작위 UUID 토큰 생성 및 만료 시간(24시간 뒤) 설정
        String token = UUID.randomUUID().toString();
        OffsetDateTime expiredAt = OffsetDateTime.now().plusHours(24); // 24시간 유효

        // 엔티티 생성 및 DB 저장
        TravelInvite teamInvite = TravelInvite.builder()
                .travel(team)
                .token(token)
                .expiredAt(expiredAt)
                .build();

        travelInviteRepository.save(teamInvite);

        // 프론트엔드가 접근할 완성된 초대 링크 주소 반환
        // (배포 환경에 따라 도메인 주소는 달라질 수 있음)
        return "https://yourdomain.com/invite?token=" + token;
    }
}
