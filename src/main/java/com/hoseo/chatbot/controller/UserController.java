package com.hoseo.chatbot.controller;

import com.hoseo.chatbot.dto.FcmTokenRequestDto;
import com.hoseo.chatbot.service.KeywordService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final KeywordService keywordService;

    public UserController(KeywordService keywordService) {
        this.keywordService = keywordService;
    }

    @PostMapping("/fcm-token")
    public Map<String, String> registerFcmToken(@RequestBody FcmTokenRequestDto request) {
        keywordService.registerFcmToken(request.userId(), request.fcmToken());
        return Map.of("status", "success");
    }
}
