package com.hoseo.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    // users 테이블의 PK입니다. MySQL auto_increment와 연결됩니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 앱/브라우저에서 전달하는 userId를 deviceId처럼 저장합니다.
    // 같은 deviceId가 중복 저장되지 않도록 unique 제약을 둡니다.
    @Column(nullable = false, unique = true)
    private String deviceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserEntity(String deviceId) {
        // ChatServiceImpl에서 userId가 처음 들어왔을 때 자동으로 사용자를 만들기 위한 생성자입니다.
        this.deviceId = deviceId;
    }

    @PrePersist
    void onCreate() {
        // DB에 처음 저장되기 직전에 생성일/수정일을 자동으로 넣습니다.
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        // 이미 저장된 사용자가 수정될 때 updatedAt만 갱신합니다.
        this.updatedAt = LocalDateTime.now();
    }
}