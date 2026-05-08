package com.hoseo.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomEntity {

    // 채팅방 PK입니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 채팅방은 한 사용자에게 속합니다.
    // DB에는 user_id 외래키로 저장됩니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // 프론트가 전달하는 세션 식별자입니다.
    // 현재 로직은 sessionId를 기준으로 기존 채팅방을 찾거나 새로 만듭니다.
    @Column(nullable = false)
    private String sessionId;

    // 채팅방 목록에서 보여줄 제목입니다. 첫 질문의 앞 30자를 저장합니다.
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ChatRoomEntity(UserEntity user, String sessionId, String title) {
        // 새 sessionId가 들어왔을 때 채팅방을 만들기 위한 생성자입니다.
        this.user = user;
        this.sessionId = sessionId;
        this.title = title;
    }

    public void refreshUpdatedAt() {
        // 메시지가 추가될 때 채팅방의 최신 활동 시간을 갱신합니다.
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        // 채팅방이 처음 생성될 때 생성일/수정일을 자동으로 기록합니다.
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        // 채팅방 정보가 수정될 때 updatedAt을 자동 갱신합니다.
        this.updatedAt = LocalDateTime.now();
    }
}