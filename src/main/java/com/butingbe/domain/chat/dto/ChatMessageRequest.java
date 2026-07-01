package com.butingbe.domain.chat.dto;

import java.util.UUID;

public record ChatMessageRequest(
        UUID roomId,
        String content
) {}
