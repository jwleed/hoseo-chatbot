package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.NoticeEventDto;
import com.hoseo.chatbot.dto.NoticeEventDto.NoticeItemDto;
import com.hoseo.chatbot.entity.KeywordEntity;
import com.hoseo.chatbot.entity.NotificationEntity;
import com.hoseo.chatbot.entity.UserEntity;
import com.hoseo.chatbot.repository.KeywordRepository;
import com.hoseo.chatbot.repository.NotificationRepository;
import com.hoseo.chatbot.service.FcmService;
import com.hoseo.chatbot.service.NoticeEventService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeEventServiceImpl implements NoticeEventService {

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FcmService fcmService;

    @Override
    @Transactional
    public int processNotices(NoticeEventDto dto) {
        List<KeywordEntity> keywords = keywordRepository.findAllWithUser();
        int processed = 0;

        for (NoticeItemDto item : dto.getItems()) {
            matchAndNotify(item, keywords);
            processed++;
        }
        return processed;
    }

    private void matchAndNotify(NoticeItemDto item, List<KeywordEntity> keywords) {
        String title = item.getTitle() != null ? item.getTitle() : "";
        String url = item.getUrl() != null ? item.getUrl() : "";

        for (KeywordEntity kw : keywords) {
            if (!title.contains(kw.getKeyword())) continue;

            UserEntity user = kw.getUser();
            if (!Boolean.TRUE.equals(user.getNotificationYn())) continue;

            if (notificationRepository.existsByUserAndKeywordAndUrl(user, kw, url)) continue;

            notificationRepository.save(new NotificationEntity(user, kw, title, url));
            fcmService.send(user.getFcmToken(), kw.getKeyword(), title, item.getDate(), url);

            System.out.printf("알림 발송: user=%s, keyword=%s, notice=%s%n",
                    user.getDeviceId(), kw.getKeyword(), title);
        }
    }
}
