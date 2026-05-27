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
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

@Service
public class ChatServiceImpl implements ChatService {

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

    private String truncateAtWord(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        int cut = text.lastIndexOf(' ', maxLen);
        return cut > 0 ? text.substring(0, cut) : text.substring(0, maxLen);
    }

    @Override
    public SseEmitter ask(ChatRequestDto request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        // userId 기반으로 사용자 찾거나 새로 생성
        UserEntity user = userRepository.findByDeviceId(request.getUserId())
                .orElseGet(() -> userRepository.save(new UserEntity(request.getUserId())));

        // sessionId 기반으로 채팅방 찾거나 새로 생성 (제목은 첫 질문 앞 30자)
        String title = truncateAtWord(request.getQuestion(), 30);
        ChatRoomEntity chatRoom = chatRoomRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> chatRoomRepository.save(new ChatRoomEntity(user, request.getSessionId(), title)));

        // USER 메시지 저장
        chatMessageRepository.save(new ChatMessageEntity(chatRoom, ChatMessageRole.USER, request.getQuestion()));
        chatRoom.refreshUpdatedAt();
        chatRoomRepository.save(chatRoom);

        Map<String, Object> body = Map.of(
                "question", request.getQuestion(),
                "domain", "notice",
                "use_tv_rag", true
        );

        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicReference<Disposable> heartbeatRef = new AtomicReference<>();

        Disposable heartbeat = Flux.interval(Duration.ofSeconds(3))
                .subscribe(tick -> {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (Exception ignored) {}
                });
        heartbeatRef.set(heartbeat);

        emitter.onCompletion(() -> {
            Disposable d = subscriptionRef.get();
            if (d != null) d.dispose();
            Disposable h = heartbeatRef.get();
            if (h != null) h.dispose();
        });
        emitter.onTimeout(() -> {
            Disposable d = subscriptionRef.get();
            if (d != null) d.dispose();
            Disposable h = heartbeatRef.get();
            if (h != null) h.dispose();
            emitter.complete();
        });

        Disposable disposable = webClient.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(120))
                .flatMapMany(response -> {
                    String answer = response.get("answer") != null ? (String) response.get("answer") : "";
                    List<?> sources = (List<?>) response.get("sources");

                    // ASSISTANT 메시지 저장
                    chatMessageRepository.save(new ChatMessageEntity(chatRoom, ChatMessageRole.ASSISTANT, answer));
                    chatRoom.refreshUpdatedAt();
                    chatRoomRepository.save(chatRoom);

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
                            } catch (Exception e) {
                                Disposable d = subscriptionRef.get();
                                if (d != null) d.dispose();
                            }
                        },
                        error -> {
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
