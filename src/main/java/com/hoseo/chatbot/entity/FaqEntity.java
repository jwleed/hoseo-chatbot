package com.hoseo.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "faqs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqEntity {

    // FAQ PK입니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예: 수강신청, 장학, 졸업, 학사 등 FAQ를 분류하는 값입니다.
    private String category;

    // 사람이 정제해서 등록한 대표 질문입니다.
    @Column(nullable = false)
    private String question;

    // FAQ 목록 기본 정렬 순서입니다. 숫자가 작을수록 먼저 표시됩니다.
    @Column(name = "sort_order")
    private Integer sortOrder;

    // 사용자가 FAQ를 클릭한 횟수입니다. 인기 FAQ TOP5/TOP10 계산에 사용합니다.
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    // FAQ 삭제는 물리 삭제가 아니라 비활성화 방식으로 처리합니다.
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public FaqEntity(String category, String question, Integer sortOrder) {
        this.category = category;
        this.question = question;
        this.sortOrder = sortOrder;
    }

    public void increaseViewCount() {
        // FAQ 클릭 API가 호출될 때마다 조회수를 1 증가시킵니다.
        this.viewCount = this.viewCount + 1;
    }

    @PrePersist
    void onCreate() {
        // FAQ가 처음 등록될 때 생성일/수정일을 자동으로 기록합니다.
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        // FAQ 내용이나 조회수가 바뀔 때 수정일을 자동 갱신합니다.
        this.updatedAt = LocalDateTime.now();
    }
}