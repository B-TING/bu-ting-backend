package com.butingbe.domain.zoneevent.exception;

import java.util.UUID;
import lombok.Getter;

/** 유저·이벤트당 열린 참여가 이미 있을 때. 409로 매핑하며 재개용 참여 id를 함께 돌려준다. */
@Getter
public class OpenParticipationExistsException extends RuntimeException {

  private final UUID participationId;

  public OpenParticipationExistsException(UUID participationId) {
    super("error.zone_event.participation.already_open");
    this.participationId = participationId;
  }
}
