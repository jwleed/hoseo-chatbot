package com.hoseo.chatbot.dto;

import java.time.LocalDateTime;

// 채팅방 목록 조회 응답 DTO입니다.
// 채팅방 상세 메시지는 포함하지 않고, 목록 화면에 필요한 정보만 담습니다.
public record HistoryRoomResponseDto(
        Long chatRoomId,
        String sessionId,
        String firstQuestion,
        LocalDateTime createdAt
) {
}