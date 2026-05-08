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

            boolean isDuplicate = (item.getNoticeId() != null && !item.getNoticeId().isBlank())
                    ? noticeRepository.existsByNoticeId(item.getNoticeId())
                    : noticeRepository.existsByTitleAndDate(item.getTitle(), item.getDate());

            if (isDuplicate) {
                System.out.println("중복 공지 스킵: " + item.getTitle());
                continue;
            }

            NoticeEntity entity = new NoticeEntity();
            entity.setNoticeId(item.getNoticeId());
            entity.setTitle(item.getTitle());
            entity.setDate(item.getDate());
            entity.setUrl(item.getUrl());
            entity.setCategory(item.getCategory());
            entity.setMajorCategory(item.getMajorCategory());
            entity.setTarget(item.getTarget());
            entity.setEntity(item.getEntity());
            noticeRepository.save(entity);

            System.out.println("새 공지 저장: " + item.getTitle());

            // TODO: 5주차 - 키워드 매칭 + FCM 발송 로직 추가 예정

            processed++;
        }
        return processed;
    }
}
