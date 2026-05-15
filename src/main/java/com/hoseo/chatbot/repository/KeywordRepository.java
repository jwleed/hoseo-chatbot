package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.KeywordEntity;
import com.hoseo.chatbot.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KeywordRepository extends JpaRepository<KeywordEntity, Long> {

    List<KeywordEntity> findByUser_DeviceId(String deviceId);

    boolean existsByUserAndKeyword(UserEntity user, String keyword);

    long countByUser(UserEntity user);

    List<KeywordEntity> findByUser(UserEntity user);

    // 공지 키워드 매칭 시 user를 함께 로딩하여 N+1 방지
    @Query("SELECT k FROM KeywordEntity k JOIN FETCH k.user")
    List<KeywordEntity> findAllWithUser();
}
