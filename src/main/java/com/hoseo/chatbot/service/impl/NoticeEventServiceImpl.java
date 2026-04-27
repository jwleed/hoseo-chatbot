package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.entity.NoticeEntity;
import com.hoseo.chatbot.repository.NoticeRepository;
import com.hoseo.chatbot.dto.NoticeEventDto;
import com.hoseo.chatbot.service.NoticeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeEventServiceImpl implements NoticeEventService {

    @Autowired
    private NoticeRepository noticeRepository;

    @Override
    @Transactional
    public int processNotices(NoticeEventDto dto) {
        int processed = 0;

        for (NoticeEventDto.NoticeItemDto item : dto.getItems()) {

            if (noticeRepository.existsByTitleAndDate(item.getTitle(), item.getDate())) {
                System.out.println("중복 공지 스킵: " + item.getTitle());
                continue;
            }

            NoticeEntity entity = new NoticeEntity();
            entity.setTitle(item.getTitle());
            entity.setContent(item.getCategory());
            entity.setDate(item.getDate());
            entity.setUrl(item.getUrl());
            noticeRepository.save(entity);

            System.out.println("새 공지 저장: " + item.getTitle());

            // TODO: 5주차 - 키워드 매칭 + FCM 발송 로직 추가 예정

            processed++;
        }
        return processed;
    }
}
