package com.hoseo.chatbot.service;

import com.hoseo.chatbot.dto.FaqRequestDto;
import com.hoseo.chatbot.dto.FaqResponseDto;
import java.util.List;

// FAQ 기능의 비즈니스 로직 계약입니다.
// Controller는 이 인터페이스만 보고 호출하고, 실제 구현은 impl 패키지에 둡니다.
public interface FaqService {
    List<FaqResponseDto> getFaqs(String category);

    FaqResponseDto createFaq(FaqRequestDto request);

    FaqResponseDto updateFaq(Long id, FaqRequestDto request);

    void deleteFaq(Long id);
}