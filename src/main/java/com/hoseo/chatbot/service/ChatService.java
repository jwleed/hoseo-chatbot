package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.ChatRequestDto;
//import com.hoseo.chatbot.dto.ChatResponseDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    SseEmitter ask(ChatRequestDto request);  // ChatResponseDto → SseEmitter
}