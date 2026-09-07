package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 앨범 커서 페이징 응답. */
public record AlbumPageResDto(List<AlbumItemResDto> items, String nextCursor, boolean hasNext) {}
