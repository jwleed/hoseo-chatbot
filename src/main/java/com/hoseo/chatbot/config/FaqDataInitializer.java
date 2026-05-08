package com.hoseo.chatbot.config;

import com.hoseo.chatbot.entity.FaqEntity;
import com.hoseo.chatbot.repository.FaqRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FaqDataInitializer implements ApplicationRunner {

    private final FaqRepository faqRepository;

    public FaqDataInitializer(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (faqRepository.count() > 0) {
            return;
        }

        faqRepository.saveAll(List.of(
                new FaqEntity("수강신청", "수강신청 기간은 언제인가요?", 1),
                new FaqEntity("수강신청", "수강신청 변경은 어떻게 하나요?", 2),
                new FaqEntity("장학", "장학금 신청은 어디서 하나요?", 3),
                new FaqEntity("학적", "휴학 신청 방법은 무엇인가요?", 4),
                new FaqEntity("학적", "복학 신청은 어떻게 하나요?", 5),
                new FaqEntity("졸업", "졸업 요건은 어디서 확인하나요?", 6),
                new FaqEntity("비교과", "비교과 프로그램은 어디서 신청하나요?", 7),
                new FaqEntity("성적", "성적 확인은 어디서 하나요?", 8),
                new FaqEntity("공지", "학사 공지는 어디서 확인하나요?", 9),
                new FaqEntity("학생지원", "학생증 발급은 어떻게 하나요?", 10)
        ));
    }
}