package com.hoseo.chatbot.controller;


import com.hoseo.chatbot.dto.ChatRequestDto;
//import com.hoseo.chatbot.dto.ChatResponseDto;
import com.hoseo.chatbot.dto.ChatResponseDto;
import com.hoseo.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/ask")
    public ChatResponseDto ask(@RequestBody ChatRequestDto request) {
        return chatService.ask(request);
    }
}
