package com.hoseo.chatbot.service;

import org.springframework.stereotype.Service;

@Service
public class FcmService {

    // TODO: Phase 3 — Firebase Admin SDK 의존성 추가 후 실제 발송으로 교체
    public void send(String fcmToken, String keyword, String noticeTitle, String noticeDate, String noticeUrl) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        System.out.printf("[FCM STUB] token=%s | keyword=%s | notice=%s%n", fcmToken, keyword, noticeTitle);
    }
}
