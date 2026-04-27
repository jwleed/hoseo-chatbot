package com.hoseo.chatbot.controller;

import com.hoseo.chatbot.dto.NoticeEventDto;
import com.hoseo.chatbot.service.NoticeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notices")
@CrossOrigin(origins = "*")
public class NoticeEventController {

    @Value("${notice.api-key}")
    private String apiKey;

    @Autowired
    private NoticeEventService noticeEventService;

    @PostMapping("/new")
    public ResponseEntity<Map<String, Object>> receiveNotice(
            @RequestHeader("X-API-Key") String requestApiKey,
            @RequestBody NoticeEventDto dto) {

        if (!apiKey.equals(requestApiKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("status", "error", "message", "인증 실패"));
        }

        System.out.println("공지 이벤트 수신: " + dto.getCount() + "건");
        int processed = noticeEventService.processNotices(dto);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "processed", processed
        ));
    }
}
