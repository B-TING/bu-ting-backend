package com.butingbe.domain.zoneevent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventComment;
import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.support.AbstractContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventSocialRepositoryTest extends AbstractContainerTest {

  @Autowired private ZoneEventLikeRepository likeRepository;
  @Autowired private ZoneEventCommentRepository commentRepository;
  @Autowired private ZoneEventReportRepository reportRepository;

  @Test
  @DisplayName("좋아요 존재·조회")
  void likeExistsAndFind() {
    UUID participationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    likeRepository.save(
        ZoneEventLike.builder().participationId(participationId).userId(userId).build());

    assertThat(likeRepository.existsByParticipationIdAndUserId(participationId, userId)).isTrue();
    assertThat(likeRepository.findByParticipationIdAndUserId(participationId, userId)).isPresent();
    assertThat(likeRepository.existsByParticipationIdAndUserId(participationId, UUID.randomUUID()))
        .isFalse();
  }

  @Test
  @DisplayName("댓글은 수정·소프트 삭제되고 삭제된 것은 조회에서 빠진다")
  void commentEditAndSoftDelete() {
    UUID participationId = UUID.randomUUID();
    ZoneEventComment kept =
        commentRepository.save(
            ZoneEventComment.builder()
                .participationId(participationId)
                .userId(UUID.randomUUID())
                .content("첫 댓글")
                .build());
    kept.edit("수정한 댓글");
    ZoneEventComment removed =
        commentRepository.save(
            ZoneEventComment.builder()
                .participationId(participationId)
                .userId(UUID.randomUUID())
                .content("지울 댓글")
                .build());
    removed.softDelete();

    var visible =
        commentRepository.findByParticipationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            participationId);
    assertThat(visible).hasSize(1);
    assertThat(visible.get(0).getContent()).isEqualTo("수정한 댓글");
    assertThat(removed.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("신고는 존재·개수·조회되고 처리 상태를 바꾼다")
  void reportExistsCountAndResolve() {
    UUID participationId = UUID.randomUUID();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(participationId)
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.NOT_ON_SITE)
                .memo("숙소에서 찍은 듯")
                .build());
    report.resolveAs(ReportStatus.DISMISSED);

    assertThat(
            reportRepository.existsByParticipationIdAndReporterId(
                participationId, report.getReporterId()))
        .isTrue();
    assertThat(reportRepository.countByParticipationId(participationId)).isEqualTo(1);
    assertThat(reportRepository.findByParticipationId(participationId).get(0).getStatus())
        .isEqualTo(ReportStatus.DISMISSED);
  }
}
