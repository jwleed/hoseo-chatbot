package com.hoseo.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    // 메시지 PK입니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 메시지는 하나의 채팅방에 속합니다.
    // DB에는 chat_room_id 외래키로 저장됩니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoomEntity chatRoom;

    // USER는 사용자의 질문, ASSISTANT는 AI의 최종 답변을 의미합니다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageRole role;

    // 질문/답변 본문은 길어질 수 있으므로 TEXT 타입으로 저장합니다.
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ChatMessageEntity(ChatRoomEntity chatRoom, ChatMessageRole role, String content) {
        // ChatServiceImpl에서 USER/ASSISTANT 메시지를 저장할 때 사용하는 생성자입니다.
        this.chatRoom = chatRoom;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        // 메시지가 저장되는 시점을 자동으로 기록합니다.
        this.createdAt = LocalDateTime.now();
    }
}