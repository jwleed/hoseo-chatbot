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
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @RequestBody NoticeEventDto dto) {

        if (requestApiKey == null || !apiKey.equals(requestApiKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("status", "error", "message", "인증 실패"));
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "items 필드가 비어 있습니다"));
        }

        System.out.println("========== 공지 이벤트 수신: " + dto.getItems().size() + "건 ==========");
        dto.getItems().forEach(item ->
            System.out.printf("  [공지] ID=%s | 제목=%s | 날짜=%s | 카테고리=%s/%s | 대상=%s | URL=%s%n",
                item.getNoticeId(), item.getTitle(), item.getDate(),
                item.getMajorCategory(), item.getCategory(),
                item.getTarget(), item.getUrl())
        );
        int processed = noticeEventService.processNotices(dto);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "processed", processed
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        System.err.println("공지 이벤트 처리 오류: " + e.getMessage());
        return ResponseEntity.status(500)
                .body(Map.of("status", "error", "message", "서버 내부 오류"));
    }
}
