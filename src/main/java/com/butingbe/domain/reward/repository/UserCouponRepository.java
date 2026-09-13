package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.UserCoupon;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponRepository extends JpaRepository<UserCoupon, UUID> {

  List<UserCoupon> findByGrantId(UUID grantId);
}
