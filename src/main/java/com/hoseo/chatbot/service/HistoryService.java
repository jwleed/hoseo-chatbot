package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.HistoryDetailResponseDto;
import com.hoseo.chatbot.dto.HistoryRoomResponseDto;
import java.util.List;

// 채팅 히스토리 기능의 비즈니스 로직 계약입니다.
// 목록 조회, 상세 조회, 삭제 기능을 제공합니다.
public interface HistoryService {
    List<HistoryRoomResponseDto> getRooms(String deviceId);

    HistoryDetailResponseDto getMessages(String deviceId, Long chatRoomId);

    void deleteRoom(Long chatRoomId);
}