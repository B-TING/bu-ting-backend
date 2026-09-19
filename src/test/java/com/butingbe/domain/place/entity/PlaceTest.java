package com.butingbe.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatZone;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceTest {

  @Test
  @DisplayName("적재 직후에는 보강 컬럼이 비어 있어도 된다")
  void buildsWithoutEnrichedFields() {
    Place place =
        Place.builder()
            .provider("TOUR_API")
            .providerPlaceId("126508")
            .name("감천문화마을")
            .address("부산광역시 사하구 감내2로 203")
            .latitude(35.0975)
            .longitude(129.0107)
            .contentTypeId("12")
            .imageUrl("https://example.com/gamcheon.jpg")
            .zoneId(ChatZone.WESTERN_BUSAN)
            .districtCode("26380")
            .build();

    assertThat(place.getProvider()).isEqualTo("TOUR_API");
    assertThat(place.getProviderPlaceId()).isEqualTo("126508");
    assertThat(place.getName()).isEqualTo("감천문화마을");
    assertThat(place.getAddress()).isEqualTo("부산광역시 사하구 감내2로 203");
    assertThat(place.getLatitude()).isEqualTo(35.0975);
    assertThat(place.getLongitude()).isEqualTo(129.0107);
    assertThat(place.getContentTypeId()).isEqualTo("12");
    assertThat(place.getImageUrl()).isEqualTo("https://example.com/gamcheon.jpg");
    assertThat(place.getZoneId()).isEqualTo(ChatZone.WESTERN_BUSAN);
    assertThat(place.getDistrictCode()).isEqualTo("26380");
    assertThat(place.getDwellMinutes()).isNull();
    assertThat(place.getRating()).isNull();
    assertThat(place.getReviewCount()).isNull();
    assertThat(place.getPreferredTimeSlot()).isNull();
  }

  @Test
  @DisplayName("보강된 장소는 체류 시간·인기도·시간대를 갖는다")
  void buildsWithEnrichedFields() {
    Place place =
        Place.builder()
            .provider("TOUR_API")
            .providerPlaceId("264337")
            .name("광안리 해수욕장")
            .zoneId(ChatZone.SUYEONG_NAMGU)
            .dwellMinutes(120)
            .rating(BigDecimal.valueOf(4.5))
            .reviewCount(18420)
            .preferredTimeSlot(PlaceTimeSlot.EVENING)
            .build();

    assertThat(place.getDwellMinutes()).isEqualTo(120);
    assertThat(place.getRating()).isEqualByComparingTo("4.5");
    assertThat(place.getReviewCount()).isEqualTo(18420);
    assertThat(place.getPreferredTimeSlot()).isEqualTo(PlaceTimeSlot.EVENING);
  }
}
