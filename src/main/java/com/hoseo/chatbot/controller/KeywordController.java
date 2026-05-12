package com.hoseo.chatbot.controller;

import com.hoseo.chatbot.dto.KeywordRequestDto;
import com.hoseo.chatbot.dto.KeywordResponseDto;
import com.hoseo.chatbot.dto.NotificationSettingRequestDto;
import com.hoseo.chatbot.service.KeywordService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notification")
public class KeywordController {

    private final KeywordService keywordService;

    public KeywordController(KeywordService keywordService) {
        this.keywordService = keywordService;
    }

    @PostMapping("/keyword")
    @ResponseStatus(HttpStatus.CREATED)
    public KeywordResponseDto register(@RequestBody KeywordRequestDto request) {
        return keywordService.register(request);
    }

    @GetMapping("/keyword/{deviceId}")
    public List<KeywordResponseDto> getKeywords(@PathVariable String deviceId) {
        return keywordService.getKeywords(deviceId);
    }

    @DeleteMapping("/keyword/{keywordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long keywordId) {
        keywordService.delete(keywordId);
    }

    @PutMapping("/setting/{deviceId}")
    public Map<String, Object> updateSetting(
            @PathVariable String deviceId,
            @RequestBody NotificationSettingRequestDto request) {
        if (request.notificationYn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notification_yn required");
        }
        keywordService.updateSetting(deviceId, request.notificationYn());
        return Map.of("status", "success", "notification_yn", request.notificationYn());
    }
}
