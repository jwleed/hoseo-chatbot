package com.hoseo.chatbot.dto;

import java.util.List;

// 특정 채팅방의 상세 대화 내역 응답 DTO입니다.
// chatRoom 정보와 그 안의 메시지 목록을 함께 반환합니다.
public record HistoryDetailResponseDto(
        String status,
        Long chatRoomId,
        List<HistoryMessageResponseDto> messages
) {
}