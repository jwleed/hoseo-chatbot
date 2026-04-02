package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
//import com.hoseo.chatbot.dto.ChatResponseDto;
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
    public SseEmitter ask(ChatRequestDto request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        Map<String, String> body = Map.of(
                "user_id", request.getUserId(),
                "session_id", request.getSessionId(),
                "question", request.getQuestion()
        );

        webClient.post()
                .uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})  // ← 변경
                .subscribe(
                        event -> {
                            try {
                                String data = event.data();  // "data:" 파싱을 WebClient가 자동으로 해줌
                                if (data == null) return;
                                if ("[DONE]".equals(data)) {
                                    emitter.complete();
                                } else {
                                    emitter.send(SseEmitter.event().data(data));
                                }
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        "{\"error\":{\"code\":\"CONNECTION_FAILED\",\"message\":\"Python 서버에 연결할 수 없습니다.\"}}"
                                ));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::complete
                );

        return emitter;
    }
}
