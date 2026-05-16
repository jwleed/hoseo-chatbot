package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.ChatRequestDto;
import com.hoseo.chatbot.entity.ChatMessageEntity;
import com.hoseo.chatbot.entity.ChatMessageRole;
import com.hoseo.chatbot.entity.ChatRoomEntity;
import com.hoseo.chatbot.entity.UserEntity;
import com.hoseo.chatbot.repository.ChatMessageRepository;
import com.hoseo.chatbot.repository.ChatRoomRepository;
import com.hoseo.chatbot.repository.UserRepository;
import com.hoseo.chatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final WebClient webClient;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatServiceImpl(
            @Value("${rag.server.url}") String ragServerUrl,
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository) {
        this.webClient = WebClient.builder()
                .baseUrl(ragServerUrl)
                .defaultHeader("ngrok-skip-browser-warning", "true")
                .build();
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public SseEmitter ask(ChatRequestDto request) {
        log.info("[CHAT] 요청 수신 | userId={} | sessionId={} | category={} | question={}",
                request.getUserId(), request.getSessionId(), request.getCategory(), request.getQuestion());

        SseEmitter emitter = new SseEmitter(180_000L);

        UserEntity user = userRepository.findByDeviceId(request.getUserId())
                .orElseGet(() -> userRepository.save(new UserEntity(request.getUserId())));

        String title = request.getQuestion().length() > 30
                ? request.getQuestion().substring(0, 30)
                : request.getQuestion();
        ChatRoomEntity chatRoom = chatRoomRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> chatRoomRepository.save(new ChatRoomEntity(user, request.getSessionId(), title)));

        chatMessageRepository.save(new ChatMessageEntity(chatRoom, ChatMessageRole.USER, request.getQuestion()));
        chatRoom.refreshUpdatedAt();
        chatRoomRepository.save(chatRoom);

        String domain = switch (request.getCategory() != null ? request.getCategory() : "") {
            case "rules", "학칙" -> "rules";
            default -> "notice";
        };

        log.info("[CHAT] RAG 서버 요청 | domain={}", domain);
        long ragStart = System.currentTimeMillis();

        Map<String, Object> body = Map.of(
                "question", request.getQuestion(),
                "domain", domain,
                "use_tv_rag", true
        );

        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicReference<Disposable> heartbeatRef = new AtomicReference<>();

        Disposable heartbeat = Flux.interval(Duration.ofSeconds(3))
                .subscribe(tick -> {
                    try {
                        emitter.send(SseEmitter.event().name("ping").data(""));
                    } catch (Exception ignored) {}
                });
        heartbeatRef.set(heartbeat);

        emitter.onCompletion(() -> {
            log.info("[CHAT] emitter 연결 종료 (정상)");
            Disposable d = subscriptionRef.get();
            if (d != null) d.dispose();
            Disposable h = heartbeatRef.get();
            if (h != null) h.dispose();
        });
        emitter.onTimeout(() -> {
            log.warn("[CHAT] emitter 타임아웃 발생");
            Disposable d = subscriptionRef.get();
            if (d != null) d.dispose();
            Disposable h = heartbeatRef.get();
            if (h != null) h.dispose();
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("[CHAT] emitter 오류 발생 | error={}", e.getMessage());
            Disposable d = subscriptionRef.get();
            if (d != null) d.dispose();
            Disposable h = heartbeatRef.get();
            if (h != null) h.dispose();
        });

        Disposable disposable = webClient.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(120))
                .publishOn(Schedulers.boundedElastic())
                .flatMapMany(response -> {
                    String answer = response.get("answer") != null ? (String) response.get("answer") : "";
                    List<?> sources = (List<?>) response.get("sources");
                    Object latency = response.get("latency_sec");
                    log.info("[CHAT] RAG 응답 수신 | 백엔드측 {}ms | AI처리 {}초 | answerLength={} | sourcesCount={}",
                            System.currentTimeMillis() - ragStart, latency, answer.length(), sources != null ? sources.size() : 0);
                    log.info("[CHAT] SSE 청크 전송 시작");

                    chatMessageRepository.save(new ChatMessageEntity(chatRoom, ChatMessageRole.ASSISTANT, answer));
                    chatRoom.refreshUpdatedAt();
                    chatRoomRepository.save(chatRoom);

                    String[] tokens = answer.split("(?<= )");

                    List<Object> events = new ArrayList<>();
                    for (String token : tokens) {
                        events.add(Map.of("chunk", token));
                    }
                    if (sources != null && !sources.isEmpty()) {
                        String sourcesText = String.join(", ", sources.stream()
                                .map(Object::toString)
                                .toList());
                        events.add(Map.of("chunk", "", "sources", sourcesText));
                    }

                    return Flux.fromIterable(events)
                            .delayElements(Duration.ofMillis(30));
                })
                .subscribe(
                        event -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(event, MediaType.APPLICATION_JSON));
                            } catch (Exception e) {
                                log.warn("[CHAT] 클라이언트 연결 끊김 (청크 전송 중) | error={}", e.getMessage());
                                Disposable d = subscriptionRef.get();
                                if (d != null) d.dispose();
                            }
                        },
                        error -> {
                            log.error("[CHAT] RAG 서버 오류 | error={}", error.getMessage());
                            Disposable h = heartbeatRef.get();
                            if (h != null) h.dispose();
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(Map.of("error", Map.of(
                                                "code", "CONNECTION_FAILED",
                                                "message", error.getMessage()
                                        )), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        () -> {
                            log.info("[CHAT] SSE 스트리밍 완료");
                            Disposable h = heartbeatRef.get();
                            if (h != null) h.dispose();
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

        subscriptionRef.set(disposable);
        return emitter;
    }
}
