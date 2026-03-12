package com.hoseo.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class HoseoChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoseoChatbotApplication.class, args);
    }

    @Bean
    // "Spring아 이 RestTemplate 객체 관리해줘" 라고 등록
    // 이게 있어야 ChatServiceImpl에서 @Autowired로 쓸 수 있음
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
