package com.hoseo.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChatResponseDto {
    private String answer;
    private List<String> sources;
}
