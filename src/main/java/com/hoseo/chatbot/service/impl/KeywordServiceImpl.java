package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.KeywordRequestDto;
import com.hoseo.chatbot.dto.KeywordResponseDto;
import com.hoseo.chatbot.entity.KeywordEntity;
import com.hoseo.chatbot.entity.UserEntity;
import com.hoseo.chatbot.repository.KeywordRepository;
import com.hoseo.chatbot.repository.NotificationRepository;
import com.hoseo.chatbot.repository.UserRepository;
import com.hoseo.chatbot.service.KeywordService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KeywordServiceImpl implements KeywordService {

    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public KeywordServiceImpl(KeywordRepository keywordRepository, UserRepository userRepository,
            NotificationRepository notificationRepository) {
        this.keywordRepository = keywordRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public KeywordResponseDto register(KeywordRequestDto request) {
        validate(request.keyword());

        UserEntity user = findUserOrThrow(request.userId());

        if (keywordRepository.existsByUserAndKeyword(user, request.keyword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KEYWORD_DUPLICATE");
        }
        if (keywordRepository.countByUser(user) >= 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYWORD_LIMIT_EXCEEDED");
        }

        return toResponse(keywordRepository.save(new KeywordEntity(user, request.keyword())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeywordResponseDto> getKeywords(String deviceId) {
        return keywordRepository.findByUser_DeviceId(deviceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long keywordId) {
        if (!keywordRepository.existsById(keywordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keyword not found");
        }
        notificationRepository.deleteByKeyword_Id(keywordId);
        keywordRepository.deleteById(keywordId);
    }

    @Override
    @Transactional
    public void updateSetting(String deviceId, boolean notificationYn) {
        UserEntity user = findUserOrThrow(deviceId);
        user.setNotificationYn(notificationYn);
    }

    @Override
    @Transactional
    public void saveCategories(String userId, List<String> categories) {
        UserEntity user = userRepository.findByDeviceId(userId)
                .orElseGet(() -> userRepository.save(new UserEntity(userId)));

        List<KeywordEntity> existing = keywordRepository.findByUser(user);
        existing.forEach(kw -> notificationRepository.deleteByKeyword_Id(kw.getId()));
        keywordRepository.deleteAll(existing);

        categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> new KeywordEntity(user, c))
                .forEach(keywordRepository::save);
    }

    @Override
    @Transactional
    public void registerFcmToken(String userId, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fcm_token required");
        }
        UserEntity user = userRepository.findByDeviceId(userId)
                .orElseGet(() -> userRepository.save(new UserEntity(userId)));
        user.setFcmToken(fcmToken);
    }

    private void validate(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYWORD_BLANK");
        }
        if (keyword.length() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYWORD_TOO_LONG");
        }
    }

    private UserEntity findUserOrThrow(String deviceId) {
        return userRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }

    private KeywordResponseDto toResponse(KeywordEntity kw) {
        return new KeywordResponseDto(kw.getId(), kw.getKeyword(), kw.getCreatedAt());
    }
}
