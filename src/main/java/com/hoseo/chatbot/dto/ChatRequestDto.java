package com.hoseo.chatbot.dto;


import lombok.Data;

@Data
public class ChatRequestDto {

    // 프론트엔드(사용자)가 보내는 질문
    private String question;
}
