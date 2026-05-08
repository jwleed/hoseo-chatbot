package com.hoseo.chatbot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String noticeId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String date;
    private String url;
    private String category;
    private String majorCategory;
    private String target;
    private String entity;
}
