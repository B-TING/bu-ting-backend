package com.butingbe.domain.storage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.storage.dto.StorageLocationResDto;
import com.butingbe.domain.storage.dto.StorageLocationSearchReqDto;
import com.butingbe.domain.storage.service.StorageLocationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class StorageLocationControllerTest {

  private MockMvc mockMvc;

  @Mock private StorageLocationService storageLocationService;

  @InjectMocks private StorageLocationController storageLocationController;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc =
        MockMvcBuilders.standaloneSetup(storageLocationController)
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .build();
  }

  @Test
  @DisplayName("좌표와 반경으로 짐보관함을 검색한다")
  void searchReturnsStorageLocations() throws Exception {
    when(storageLocationService.search(any(StorageLocationSearchReqDto.class)))
        .thenReturn(
            List.of(
                new StorageLocationResDto(
                    "2호선",
                    "서면역",
                    "1번 출구 앞",
                    35.157,
                    129.059,
                    120,
                    true,
                    new StorageLocationResDto.Counts(10, 5, 2, 1),
                    "2000원",
                    "부산교통공사",
                    List.of(
                        new StorageLocationResDto.Fee(
                            "기본",
                            List.of(new StorageLocationResDto.FeeItem("소형", 2000, "4시간")))))));

    mockMvc
        .perform(
            get("/storage-locations")
                .param("latitude", "35.157")
                .param("longitude", "129.059")
                .param("radius", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].stationName").value("서면역"))
        .andExpect(jsonPath("$[0].line").value("2호선"))
        .andExpect(jsonPath("$[0].distanceMeters").value(120))
        .andExpect(jsonPath("$[0].openNow").value(true))
        .andExpect(jsonPath("$[0].counts.small").value(10))
        .andExpect(jsonPath("$[0].fees[0].items[0].amount").value(2000));

    ArgumentCaptor<StorageLocationSearchReqDto> captor =
        ArgumentCaptor.forClass(StorageLocationSearchReqDto.class);
    verify(storageLocationService).search(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().radius()).isEqualTo(500);
  }

  @Test
  @DisplayName("반경이 허용 범위를 벗어나면 400을 반환한다")
  void searchRejectsRadiusOutOfRange() throws Exception {
    mockMvc
        .perform(
            get("/storage-locations")
                .param("latitude", "35.157")
                .param("longitude", "129.059")
                .param("radius", "20001"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("위도가 허용 범위를 벗어나면 400을 반환한다")
  void searchRejectsLatitudeOutOfRange() throws Exception {
    mockMvc
        .perform(
            get("/storage-locations")
                .param("latitude", "95.0")
                .param("longitude", "129.059")
                .param("radius", "500"))
        .andExpect(status().isBadRequest());
  }
}
