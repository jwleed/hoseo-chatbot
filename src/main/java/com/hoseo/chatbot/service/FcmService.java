package com.hoseo.chatbot.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

    public void send(String fcmToken, String keyword, String noticeTitle, String noticeDate, String noticeUrl) {
        if (fcmToken == null || fcmToken.isBlank()) {
            System.out.printf("[FCM] FCM 토큰 없음 — 발송 스킵 (keyword=%s)%n", keyword);
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle("[" + keyword + "] 새 공지")
                        .setBody(noticeTitle)
                        .build())
                .putData("keyword", keyword)
                .putData("title", noticeTitle)
                .putData("date", noticeDate != null ? noticeDate : "")
                .putData("url", noticeUrl != null ? noticeUrl : "")
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.printf("[FCM] 발송 성공: %s | keyword=%s | notice=%s%n", response, keyword, noticeTitle);
        } catch (FirebaseMessagingException e) {
            System.err.printf("[FCM] 발송 실패: %s | keyword=%s | notice=%s%n", e.getMessage(), keyword, noticeTitle);
        }
    }
}
