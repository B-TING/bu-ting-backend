package com.butingbe.domain.chat.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ChatZone {

    HAEUNDAE_GIJANG(
            "해운대+기장",
            List.of("26350", "26710"), // 해운대구, 기장군
            List.of("해운대 해수욕장", "해동용궁사", "센텀시티")
    ),

    SUYEONG_NAMGU(
            "수영구+남구",
            List.of("26500", "26290"), // 수영구, 남구
            List.of("광안리 해수욕장", "민락수변공원", "오륙도 스카이워크")
    ),

    CENTRAL_NORTH(
            "금정+동래+연제+부산진",
            List.of("26415", "26260", "26470", "26230"), // 금정구, 동래구, 연제구, 부산진구
            List.of("전포 카페거리", "서면 젊음의 거리", "사직구장", "범어사")
    ),

    OLD_DOWNTOWN(
            "서구+중구+동구",
            List.of("26140", "26110", "26130"), // 서구, 중구, 동구
            List.of("자갈치시장", "국제시장", "용두산공원")
    ),

    YEONGDO(
            "영도구",
            List.of("26200"), // 영도구
            List.of("흰여울문화마을", "태종대")
    ),

    WESTERN_BUSAN(
            "강서+사상+사하+북구",
            List.of("26440", "26530", "26380", "26320"), // 강서구, 사상구, 사하구, 북구
            List.of("감천문화마을", "다대포 해수욕장", "김해국제공항")
    );

    private final String zoneName;          // 프론트엔드 화면 노출용 한글 이름
    private final List<String> cityCodes;   // 조회용 법정동 구·군 코드 앞 5자리 (IN 쿼리 매핑용)
    private final List<String> landmarks;   // 권역 설명 및 UI 팁 제공용 랜드마크 리스트

    ChatZone(String zoneName, List<String> cityCodes, List<String> landmarks) {
        this.zoneName = zoneName;
        this.cityCodes = cityCodes;
        this.landmarks = landmarks;
    }


    public static ChatZone fromString(String value) {
        return Arrays.stream(ChatZone.values())
                .filter(zone -> zone.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅 권역입니다: " + value));
    }
}
