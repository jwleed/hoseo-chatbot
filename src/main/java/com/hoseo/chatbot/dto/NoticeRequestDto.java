package com.hoseo.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

// 새 공지 등록 요청 DTO입니다.
// 크롤러나 공지 수집 시스템이 이 형식으로 POST /api/notices/new를 호출할 수 있습니다.
public record NoticeRequestDto(
        String noticeId,
        @NotBlank String title,
        String content,
        String date,
        String url,
        String category,
        String majorCategory,
        String target,
        String entity
) {
}