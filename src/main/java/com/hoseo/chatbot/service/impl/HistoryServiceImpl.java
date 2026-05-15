package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.HistoryDetailResponseDto;
import com.hoseo.chatbot.dto.HistoryMessageResponseDto;
import com.hoseo.chatbot.dto.HistoryRoomResponseDto;
import com.hoseo.chatbot.entity.ChatRoomEntity;
import com.hoseo.chatbot.repository.ChatMessageRepository;
import com.hoseo.chatbot.repository.ChatRoomRepository;
import com.hoseo.chatbot.service.HistoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoryServiceImpl implements HistoryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public HistoryServiceImpl(ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryRoomResponseDto> getRooms(String deviceId) {
        return chatRoomRepository.findByUser_DeviceIdOrderByUpdatedAtDesc(deviceId)
                .stream()
                .map(room -> new HistoryRoomResponseDto(
                        room.getId(),
                        room.getSessionId(),
                        room.getTitle(),
                        room.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HistoryDetailResponseDto getMessages(String deviceId, Long chatRoomId) {
        ChatRoomEntity room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "chat room not found"));

        if (!room.getUser().getDeviceId().equals(deviceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access denied");
        }

        List<HistoryMessageResponseDto> messages = chatMessageRepository
                .findByChatRoom_IdOrderByCreatedAtAsc(chatRoomId)
                .stream()
                .map(msg -> new HistoryMessageResponseDto(
                        msg.getRole().name().toLowerCase(),
                        msg.getContent(),
                        msg.getCreatedAt()
                ))
                .toList();

        return new HistoryDetailResponseDto("success", room.getId(), messages);
    }

    @Override
    @Transactional
    public void deleteRoom(Long chatRoomId) {
        if (!chatRoomRepository.existsById(chatRoomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chat room not found");
        }
        chatMessageRepository.deleteByChatRoom_Id(chatRoomId);
        chatRoomRepository.deleteById(chatRoomId);
    }
}
