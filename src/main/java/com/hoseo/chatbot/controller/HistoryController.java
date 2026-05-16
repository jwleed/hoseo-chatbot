package com.hoseo.chatbot.controller;

import com.hoseo.chatbot.dto.HistoryDetailResponseDto;
import com.hoseo.chatbot.dto.HistoryRoomResponseDto;
import com.hoseo.chatbot.service.HistoryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    // 채팅 히스토리 조회/삭제 API입니다.
    // 채팅 저장 자체는 ChatServiceImpl에서 처리하고, 이 컨트롤러는 저장된 결과를 조회합니다.
    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{deviceId}")
    public Map<String, Object> getRooms(@PathVariable String deviceId) {
        return Map.of(
                "status", "success",
                "history", historyService.getRooms(deviceId)
        );
    }

    @GetMapping("/{deviceId}/{chatRoomId}")
    public HistoryDetailResponseDto getMessages(
            @PathVariable String deviceId,
            @PathVariable Long chatRoomId
    ) {
        // 특정 사용자(deviceId)의 특정 채팅방(chatRoomId)에 저장된 메시지 목록을 반환합니다.
        return historyService.getMessages(deviceId, chatRoomId);
    }

    @DeleteMapping("/{deviceId}/{chatRoomId}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable String deviceId,
            @PathVariable Long chatRoomId) {
        historyService.deleteRoom(deviceId, chatRoomId);
        return ResponseEntity.noContent().build();
    }
}
