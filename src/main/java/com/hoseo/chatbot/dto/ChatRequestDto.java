package com.hoseo.chatbot.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequestDto {
    @NotBlank(message = "userId는 필수입니다.")
    private String userId;

    @NotBlank(message = "sessionId는 필수입니다.")
    private String sessionId;

    @NotBlank(message = "question은 필수입니다.")
    private String question;

    private String category;
}
