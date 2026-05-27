package com.hoseo.chatbot.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

    // false 반환 = 토큰이 만료/무효 → 호출부에서 DB 토큰 삭제 필요
    public boolean send(String fcmToken, String keyword, String noticeTitle, String noticeDate, String noticeUrl) {
        if (fcmToken == null || fcmToken.isBlank()) {
            System.out.printf("[FCM] FCM 토큰 없음 — 발송 스킵 (keyword=%s)%n", keyword);
            return true;
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
            return true;
        } catch (FirebaseMessagingException e) {
            System.err.printf("[FCM] 발송 실패: %s | keyword=%s | notice=%s%n", e.getMessage(), keyword, noticeTitle);
            return e.getMessagingErrorCode() != MessagingErrorCode.UNREGISTERED;
        }
    }
}
