package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.ChatMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    // 채팅방 상세 조회에서 메시지를 시간순으로 보여주기 위해 사용합니다.
    List<ChatMessageEntity> findByChatRoom_IdOrderByCreatedAtAsc(Long chatRoomId);

    @Transactional
        // 채팅방 삭제 전에 해당 채팅방의 메시지를 먼저 삭제합니다.
    void deleteByChatRoom_Id(Long chatRoomId);
}