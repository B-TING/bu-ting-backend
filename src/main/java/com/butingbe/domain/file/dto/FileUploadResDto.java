package com.butingbe.domain.file.dto;

public record FileUploadResDto(
    String fileKey, String originalFileName, String contentType, long fileSize, String url) {}
