package com.butingbe.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Embeddable // JPA가 이 클래스를 다른 엔티티의 일부로 인식하도록 설정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙상 필요한 기본 생성자
public class Name {

  @Column(name = "last_name", nullable = false, length = 20)
  private String lastName; // 성 (예: 김)

  @Column(name = "first_name", nullable = false, length = 50)
  private String firstName; // 이름 (예: 철수)

  public Name(String lastName, String firstName) {
    Assert.hasText(lastName, "성은 필수 입력 항목입니다.");
    Assert.hasText(firstName, "이름은 필수 입력 항목입니다.");

    this.lastName = lastName.trim();
    this.firstName = firstName.trim();
  }

  // 편의를 위해 전체 이름을 반환하는 메서드 추가
  public String getFullName() {
    return lastName + firstName;
  }
}
