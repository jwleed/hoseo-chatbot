package com.hoseo.chatbot.controller;


import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.dto.ChatResponseDto;
import com.hoseo.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/ask")
    public ChatResponseDto ask(@RequestBody ChatRequestDto request) {
        // 프론트가 보낸 JSON을 request 객체로 변환

        return chatService.ask(request);
        // 반환
    }
}
