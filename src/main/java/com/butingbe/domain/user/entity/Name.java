package com.butingbe.domain.user.entity;

import com.butingbe.global.error.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Embeddable // JPA가 이 클래스를 다른 엔티티의 일부로 인식하도록 설정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙상 필요한 기본 생성자
public class Name {

  @Column(name = "last_name", nullable = false, length = 20)
  private String lastName; // 성 (예: 김)

  @Column(name = "first_name", nullable = false, length = 50)
  private String firstName; // 이름 (예: 철수)

  public Name(String lastName, String firstName) {
    // Spring Assert 는 IllegalArgumentException 을 던진다. 전용 핸들러가 사라진 뒤로는 그게 500 이 된다.
    // 사용자 프로필 입력에서 만들어지는 값이라 400 이어야 한다.
    if (!StringUtils.hasText(lastName)) {
      throw new InvalidRequestException("error.user.last_name.required");
    }
    if (!StringUtils.hasText(firstName)) {
      throw new InvalidRequestException("error.user.first_name.required");
    }

    this.lastName = lastName.trim();
    this.firstName = firstName.trim();
  }

  // 편의를 위해 전체 이름을 반환하는 메서드 추가
  public String getFullName() {
    return lastName + firstName;
  }
}
