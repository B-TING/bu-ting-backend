package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.LocalChatroom;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalChatroomRepository extends JpaRepository<LocalChatroom, UUID> {

    @Query(value = """
        SELECT * FROM local_chatroom 
        WHERE latitude BETWEEN :swLat AND :neLat 
          AND longitude BETWEEN :swLng AND :neLng
        """, nativeQuery = true)
    List<LocalChatroom> findChatroomsWithinBounds(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng
    );


    Optional<Object> findByGooglePlaceId(@NotBlank(message = "Google Place ID는 필수입니다.") String s);

    @Query("SELECT lc FROM LocalChatroom lc WHERE SUBSTRING(lc.localCode, 1, 5) IN :prefixes")
    List<LocalChatroom> findByLocalCodePrefixIn(@Param("prefixes") List<String> prefixes);
}
