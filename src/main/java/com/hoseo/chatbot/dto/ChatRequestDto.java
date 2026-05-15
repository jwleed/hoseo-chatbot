package com.hoseo.chatbot.dto;


import lombok.Data;

@Data
public class ChatRequestDto {
    private String userId;
    private String sessionId;
    private String question;
    private String category;
}
