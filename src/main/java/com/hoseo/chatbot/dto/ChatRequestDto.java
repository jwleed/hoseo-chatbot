package com.hoseo.chatbot.dto;


import lombok.Data;

@Data
public class ChatRequestDto {
    private String userId;    // 추가
    private String sessionId; // 추가
    private String question;  // 기존 유지
}
