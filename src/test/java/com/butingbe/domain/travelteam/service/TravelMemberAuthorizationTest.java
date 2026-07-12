package com.butingbe.domain.travelteam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TravelMemberAuthorizationTest {

  @Test
  void returnsTravelMemberWhenUserBelongsToTravel() {
    UUID travelId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TravelMember member = TravelMember.builder().role(TravelTeamRole.MEMBER).build();
    TravelMemberAuthorization authorization = authorizationReturning(Optional.of(member));

    assertThat(authorization.requireMember(travelId, userId)).isSameAs(member);
  }

  @Test
  void rejectsUserWhoDoesNotBelongToTravel() {
    UUID travelId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TravelMemberAuthorization authorization = authorizationReturning(Optional.empty());

    assertThatThrownBy(() -> authorization.validateMember(travelId, userId))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  @Test
  void rejectsMemberWhenLeaderRoleIsRequired() {
    UUID travelId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TravelMember member = TravelMember.builder().role(TravelTeamRole.MEMBER).build();
    TravelMemberAuthorization authorization = authorizationReturning(Optional.of(member));

    assertThatThrownBy(
            () -> authorization.requireLeader(travelId, userId, "Leader role is required."))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Leader role is required.");
  }

  private TravelMemberAuthorization authorizationReturning(Optional<TravelMember> member) {
    TravelMemberRepository repository =
        (TravelMemberRepository)
            Proxy.newProxyInstance(
                TravelMemberRepository.class.getClassLoader(),
                new Class<?>[] {TravelMemberRepository.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("findByTravel_IdAndUser_Id")) {
                    return member;
                  }
                  throw new UnsupportedOperationException(method.getName());
                });
    return new TravelMemberAuthorization(repository);
  }
}
