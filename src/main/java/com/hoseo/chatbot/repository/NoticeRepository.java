package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.NoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<NoticeEntity, Long> {
    boolean existsByTitleAndDate(String title, String date);
}
