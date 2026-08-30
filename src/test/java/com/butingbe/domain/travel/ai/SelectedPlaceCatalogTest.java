package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SelectedPlaceCatalogTest {
  @Test
  void equalNamesDoNotCollapseDistinctTourismContentIds() {
    var first =
        new WizardPickedPlaceReqDto("GOOGLE", "126083", "동일 이름", "부산", 35.1, 129.1, "TOURIST_SPOT");
    var second =
        new WizardPickedPlaceReqDto("GOOGLE", "127537", "동일 이름", "부산", 35.2, 129.2, "TOURIST_SPOT");
    var catalog = SelectedPlaceCatalog.from(request(List.of(first, second)));
    assertThat(catalog)
        .hasSize(2)
        .containsEntry(PlaceKey.of("GOOGLE", "126083"), first)
        .containsEntry(PlaceKey.of("GOOGLE", "127537"), second);
  }

  @Test
  void sameIdDifferentProvidersAreDistinct() {
    var a = new WizardPickedPlaceReqDto("GOOGLE", "126083", "이름", "부산", null, null, null);
    var b = new WizardPickedPlaceReqDto("NAVER", "126083", "이름", "부산", null, null, null);
    assertThat(SelectedPlaceCatalog.from(request(List.of(a, b)))).hasSize(2);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "UNKNOWN"})
  void invalidProviderIsRejectedBeforeGeneration(String provider) {
    assertInvalid(new WizardPickedPlaceReqDto(provider, "126083", "이름", "부산", null, null, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void invalidContentIdIsRejected(String contentId) {
    assertInvalid(new WizardPickedPlaceReqDto("GOOGLE", contentId, "이름", "부산", null, null, null));
  }

  @Test
  void invalidCoordinatesAndMissingStoredMetadataAreRejected() {
    assertInvalid(
        new WizardPickedPlaceReqDto("GOOGLE", "126083", "이름", "부산", Double.NaN, null, null));
    assertInvalid(new WizardPickedPlaceReqDto("GOOGLE", "126083", "이름", null, 35.0, 129.0, null));
    assertInvalid(new WizardPickedPlaceReqDto("GOOGLE", "126083", "", "부산", 35.0, 129.0, null));
  }

  private void assertInvalid(WizardPickedPlaceReqDto place) {
    assertThatThrownBy(() -> SelectedPlaceCatalog.from(request(List.of(place))))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e -> {
              assertThat(e.getReason())
                  .isEqualTo(TravelPlanValidationException.Reason.INVALID_PLACE_REFERENCE);
              assertThat(e.isGeneratedResponse()).isFalse();
            });
  }
}
