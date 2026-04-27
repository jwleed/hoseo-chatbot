package com.hoseo.chatbot.dto;


import lombok.Data;
import java.util.List;

@Data
public class NoticeEventDto {
    private String source;
    private String generatedAt;
    private int count;
    private List<NoticeItemDto> items;

    @Data
    public static class NoticeItemDto {
        private String noticeId;
        private String title;
        private String date;
        private String url;
        private String category;
        private String majorCategory;
        private String target;
        private String entity;
    }
}
