package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글 작성·수정 요청. */
public record CommentReqDto(@NotBlank @Size(max = 200) String content) {}
