package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.ChatRoomEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, Long> {
    // 프론트에서 전달한 sessionId로 기존 채팅방을 찾습니다.
    Optional<ChatRoomEntity> findBySessionId(String sessionId);

    // 특정 사용자의 채팅방 목록을 최신 수정일 기준 내림차순으로 조회합니다.
    List<ChatRoomEntity> findByUser_DeviceIdOrderByUpdatedAtDesc(String deviceId);
}