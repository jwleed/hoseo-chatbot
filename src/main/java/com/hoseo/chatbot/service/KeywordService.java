package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.KeywordRequestDto;
import com.hoseo.chatbot.dto.KeywordResponseDto;
import java.util.List;

public interface KeywordService {

    KeywordResponseDto register(KeywordRequestDto request);

    List<KeywordResponseDto> getKeywords(String deviceId);

    void delete(Long keywordId);

    void updateSetting(String deviceId, boolean notificationYn);

    void registerFcmToken(String userId, String fcmToken);

    void saveCategories(String userId, List<String> categories);
}
