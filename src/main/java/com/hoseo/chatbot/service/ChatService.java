package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.dto.ChatResponseDto;

public interface ChatService {

    ChatResponseDto ask(ChatRequestDto request);
}
