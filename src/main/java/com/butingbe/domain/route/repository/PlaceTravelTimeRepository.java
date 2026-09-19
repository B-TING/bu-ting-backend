package com.butingbe.domain.route.repository;

import com.butingbe.domain.route.entity.PlaceTravelTime;
import com.butingbe.domain.route.entity.PlaceTravelTimeId;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceTravelTimeRepository
    extends JpaRepository<PlaceTravelTime, PlaceTravelTimeId> {

  /** 만료된 캐시 행을 지운다. 지운 건수를 돌려준다. */
  @Modifying(clearAutomatically = true)
  @Query("delete from PlaceTravelTime p where p.fetchedAt <= :threshold")
  int deleteFetchedBefore(@Param("threshold") LocalDateTime threshold);
}
