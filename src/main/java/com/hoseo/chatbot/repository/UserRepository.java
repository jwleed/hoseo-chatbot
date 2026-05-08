package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    // deviceId(userId)로 기존 사용자를 찾습니다. 없으면 ChatServiceImpl에서 새로 생성합니다.
    Optional<UserEntity> findByDeviceId(String deviceId);

    // 특정 deviceId가 이미 존재하는지 확인할 때 사용하는 메서드입니다.
    boolean existsByDeviceId(String deviceId);
}