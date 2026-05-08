package com.hoseo.chatbot.dto;

// 공지 등록 결과 응답 DTO입니다.
// created=false이면 중복 공지라서 새로 저장하지 않았다는 의미입니다.
public record NoticeResponseDto(
        Long id,
        boolean created,
        String message
) {
}