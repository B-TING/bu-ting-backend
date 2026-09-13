package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.UserPointBalance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointBalanceRepository extends JpaRepository<UserPointBalance, UUID> {}
