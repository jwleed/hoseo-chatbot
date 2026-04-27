package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
        SseEmitter emitter = new SseEmitter(60_000L);

        Map<String, Object> body = Map.of(
                "question", request.getQuestion(),
                "domain", "notice",
                "use_tv_rag", true
        );

        webClient.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                        response -> {
                            try {
                                String answer = (String) response.get("answer");
                                List<?> sources = (List<?>) response.get("sources");

                                emitter.send(SseEmitter.event()
                                        .data(Map.of("chunk", answer != null ? answer : ""), MediaType.APPLICATION_JSON));

                                if (sources != null && !sources.isEmpty()) {
                                    emitter.send(SseEmitter.event()
                                            .data(Map.of("chunk", "", "sources", sources), MediaType.APPLICATION_JSON));
                                }

                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(Map.of("error", Map.of(
                                                "code", "CONNECTION_FAILED",
                                                "message", error.getMessage()
                                        )), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

        return emitter;
    }
}
