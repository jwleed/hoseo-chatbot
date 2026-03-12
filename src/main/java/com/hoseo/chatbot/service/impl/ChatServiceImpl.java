package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.dto.ChatResponseDto;
import com.hoseo.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${rag.server.url}")
    private String ragServerUrl;

    @Override
    public ChatResponseDto ask(ChatRequestDto request) {
        String endpoint = ragServerUrl + "/rag/query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("question", request.getQuestion());

        HttpEntity<Map<String, String>> httpRequest = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, httpRequest, Map.class);
            // Python 서버로 POST 전송

            String answer = (String) response.getBody().get("answer");
            // answer 값 꺼냄

            return new ChatResponseDto(answer);
            // DTO에 담아서 반환

        } catch (Exception e) {
            return new ChatResponseDto("챗봇 응답 오류: Python 서버에 연결할 수 없습니다.");
            // Python 서버 연결 실패시 에러 메시지 반환
        }
    }
}
