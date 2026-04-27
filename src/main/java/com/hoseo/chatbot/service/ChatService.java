package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.ChatRequestDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    SseEmitter ask(ChatRequestDto request);
}