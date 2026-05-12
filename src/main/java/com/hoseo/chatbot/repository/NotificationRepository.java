package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.KeywordEntity;
import com.hoseo.chatbot.entity.NotificationEntity;
import com.hoseo.chatbot.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    boolean existsByUserAndKeywordAndUrl(UserEntity user, KeywordEntity keyword, String url);

    void deleteByKeyword_Id(Long keywordId);
}
