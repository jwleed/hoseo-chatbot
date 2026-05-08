package com.hoseo.chatbot.dto;

import java.time.LocalDateTime;

// FAQ API가 클라이언트에 반환하는 응답 DTO입니다.
// 활성 여부, 클릭 수, 생성/수정 시간을 함께 내려주어 관리자 화면에서도 활용할 수 있습니다.
public record FaqResponseDto(
        Long faqId,
        String category,
        String question,
        Integer sortOrder,
        Long viewCount,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}