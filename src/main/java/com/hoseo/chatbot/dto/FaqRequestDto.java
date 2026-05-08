package com.hoseo.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

// FAQ 생성/수정 요청에 사용하는 DTO입니다.
// Entity를 직접 RequestBody로 받지 않고 DTO를 두면 API 입력값과 DB 구조를 분리할 수 있습니다.
public record FaqRequestDto(
        String category,
        @NotBlank String question,
        Integer sortOrder
) {
}