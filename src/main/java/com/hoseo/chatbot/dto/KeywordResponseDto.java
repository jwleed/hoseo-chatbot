package com.hoseo.chatbot.dto;

import java.time.LocalDateTime;

public record KeywordResponseDto(Long keywordId, String keyword, LocalDateTime createdAt) {
}
