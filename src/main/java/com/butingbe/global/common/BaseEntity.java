package com.butingbe.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass // 이 클래스를 상속받는 엔티티들에게 아래 필드들을 컬럼으로 주입
@EntityListeners(AuditingEntityListener.class) // 내부 메커니즘(AOP)으로 이벤트를 가로채서 값 주입
public abstract class BaseEntity {

  @CreatedDate // 엔티티가 생성(Persist)되는 시점을 AOP가 가로채서 자동 주입
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate // 엔티티가 수정(Update)되는 시점을 AOP가 가로채서 자동 주입
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @CreatedBy // 누가 생성했는지 (스프링 시큐리티 세션 정보와 연동 가능)
  @Column(name = "created_by", updatable = false, length = 50)
  private String createdBy;

  @LastModifiedBy // 누가 수정했는지 (스프링 시큐리티 세션 정보와 연동 가능)
  @Column(name = "updated_by", length = 50)
  private String updatedBy;
}
