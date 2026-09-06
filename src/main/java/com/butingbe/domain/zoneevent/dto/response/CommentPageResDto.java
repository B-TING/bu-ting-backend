package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 댓글 커서 페이징 응답. */
public record CommentPageResDto(List<CommentResDto> items, String nextCursor, boolean hasNext) {}
