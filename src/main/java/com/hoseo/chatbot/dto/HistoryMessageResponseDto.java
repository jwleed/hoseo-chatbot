package com.hoseo.chatbot.dto;

import java.time.LocalDateTime;

// 채팅방 상세 조회에서 각 메시지를 표현하는 DTO입니다.
// role은 USER 또는 ASSISTANT 문자열로 내려갑니다.
public record HistoryMessageResponseDto(
        String role,
        String content,
        LocalDateTime createdAt
) {
}