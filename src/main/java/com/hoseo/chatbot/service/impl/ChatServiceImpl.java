package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
//import com.hoseo.chatbot.dto.ChatResponseDto;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;


@Service
public class ChatServiceImpl implements ChatService {

    private final WebClient webClient;

    public ChatServiceImpl(@Value("${rag.server.url}") String ragServerUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(ragServerUrl)
                .build();
    }

    @Override
    public ChatResponseDto ask(ChatRequestDto request) {
        Map<String, Object> body = Map.of(
                "question", request.getQuestion(),
                "domain", "notice",
                "use_tv_rag", true
        );

        try {
            Map response = webClient.post()
                    .uri("/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String answer = (String) response.get("answer");
            List<String> sources = (List<String>) response.get("sources");

            return new ChatResponseDto(answer, sources != null ? sources : List.of());

        } catch (Exception e) {
            return new ChatResponseDto("AI 서버 연결 오류: " + e.getMessage(), List.of());
        }
    }
}
