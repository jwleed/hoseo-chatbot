package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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
                .flatMapMany(response -> {
                    String answer = response.get("answer") != null ? (String) response.get("answer") : "";
                    List<?> sources = (List<?>) response.get("sources");

                    // 단어 단위로 쪼개기 (공백 포함)
                    String[] tokens = answer.split("(?<= )");

                    List<Object> events = new ArrayList<>();
                    for (String token : tokens) {
                        events.add(Map.of("chunk", token));
                    }
                    if (sources != null && !sources.isEmpty()) {
                        events.add(Map.of("chunk", "", "sources", sources));
                    }

                    // 토큰마다 30ms 딜레이
                    return Flux.fromIterable(events)
                            .delayElements(Duration.ofMillis(30));
                })
                .subscribe(
                        event -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(event, MediaType.APPLICATION_JSON));
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
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

        return emitter;
    }
}
