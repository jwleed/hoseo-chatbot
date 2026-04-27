package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.NoticeEventDto;

public interface NoticeEventService {
    int processNotices(NoticeEventDto dto);
}
