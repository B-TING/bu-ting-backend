package com.butingbe.domain.zonetitle.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zonetitle.dto.response.CityGradeResDto;
import com.butingbe.domain.zonetitle.entity.CityGrade;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserCityGradeHistoryRepository;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.support.AbstractContainerTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CityGradeServiceTest extends AbstractContainerTest {

  @Autowired private CityGradeService cityGradeService;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private UserCityGradeHistoryRepository historyRepository;
  @Autowired private UserRepository userRepository;

  private UUID userId;
  private final Map<String, ZoneTitleDef> defsByCode = new HashMap<>();

  @BeforeEach
  void setUp() {
    int[][] tiers = {{1, 1}, {2, 3}, {3, 7}};
    for (ChatZone zone : ChatZone.values()) {
      for (int[] tr : tiers) {
        ZoneTitleDef def =
            titleDefRepository.save(
                ZoneTitleDef.builder()
                    .titleCode(zone.name() + "_T" + tr[0])
                    .zoneId(zone.name())
                    .tier(tr[0])
                    .requiredSuccessCount(tr[1])
                    .titleName(zone.name() + " T" + tr[0])
                    .style("chip")
                    .color("#000")
                    .build());
        defsByCode.put(def.getTitleCode(), def);
      }
    }
    userId =
        userRepository
            .save(
                User.builder()
                    .email("cg-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("grader")
                    .role(UserRole.USER)
                    .build())
            .getId();
  }

  @Test
  @DisplayName("서로 다른 구역 T1 3개면 탐험가로 계산된다")
  void computesExplorer() {
    grant("HAEUNDAE_GIJANG_T1");
    grant("SUYEONG_NAMGU_T1");
    grant("YEONGDO_T1");

    assertThat(cityGradeService.computeGrade(userId)).isEqualTo(CityGrade.EXPLORER);
  }

  @Test
  @DisplayName("등급이 오르면 이력을 남기고, 오르지 않으면 남기지 않는다")
  void recordsRiseOnce() {
    grant("HAEUNDAE_GIJANG_T1");
    grant("SUYEONG_NAMGU_T1");
    grant("YEONGDO_T1");
    cityGradeService.recordIfRisen(userId); // BEGINNER→EXPLORER 기록

    assertThat(
            historyRepository.findTopByUserIdOrderByReachedAtDesc(userId).orElseThrow().getGrade())
        .isEqualTo(CityGrade.EXPLORER);

    // 같은 등급에서 재호출은 이력을 늘리지 않는다.
    cityGradeService.recordIfRisen(userId);
    assertThat(historyRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("도시 등급 응답은 등급·상승시점·다음 조건을 담는다")
  void cityGradeResponse() {
    grant("HAEUNDAE_GIJANG_T1");
    grant("SUYEONG_NAMGU_T1");
    grant("YEONGDO_T1");
    cityGradeService.recordIfRisen(userId);

    CityGradeResDto res = cityGradeService.cityGradeOf(userId);
    assertThat(res.grade()).isEqualTo("EXPLORER");
    assertThat(res.reachedAt()).isNotNull();
    assertThat(res.next().grade()).isEqualTo("MASTER");
  }

  @Test
  @DisplayName("초보 여행자는 이력을 남기지 않고 상승 시점도 null이다")
  void beginnerNoHistory() {
    cityGradeService.recordIfRisen(userId);
    assertThat(historyRepository.findAll()).isEmpty();

    CityGradeResDto res = cityGradeService.cityGradeOf(userId);
    assertThat(res.grade()).isEqualTo("BEGINNER");
    assertThat(res.reachedAt()).isNull();
  }

  private void grant(String code) {
    ZoneTitleDef def = defsByCode.get(code);
    userZoneTitleRepository.save(
        UserZoneTitle.builder()
            .userId(userId)
            .titleDef(def)
            .zoneId(def.getZoneId())
            .equipped(false)
            .build());
  }
}
