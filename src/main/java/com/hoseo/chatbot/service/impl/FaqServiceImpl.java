package com.hoseo.chatbot.service.impl;

import com.hoseo.chatbot.dto.FaqRequestDto;
import com.hoseo.chatbot.dto.FaqResponseDto;
import com.hoseo.chatbot.entity.FaqEntity;
import com.hoseo.chatbot.repository.FaqRepository;
import com.hoseo.chatbot.service.FaqService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaqServiceImpl implements FaqService {

    // FAQ 등록/조회/수정/비활성화/클릭 수 증가를 담당하는 Repository입니다.
    private final FaqRepository faqRepository;

    public FaqServiceImpl(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqResponseDto> getFaqs(String category) {
        // category가 비어 있으면 전체 활성 FAQ, 있으면 해당 카테고리의 활성 FAQ만 조회합니다.
        List<FaqEntity> faqs = isBlank(category)
                ? faqRepository.findByIsActiveTrueOrderBySortOrderAsc()
                : faqRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category);
        return faqs.stream().map(this::toResponse).toList();
    }

@Override
    @Transactional
    public FaqResponseDto createFaq(FaqRequestDto request) {
        // 사용자의 실제 질문 로그가 아니라, 관리자가 정제한 대표 FAQ를 저장합니다.
        FaqEntity faq = new FaqEntity(
                request.category(),
                request.question(),
                request.sortOrder()
        );
        return toResponse(faqRepository.save(faq));
    }

    @Override
    @Transactional
    public FaqResponseDto updateFaq(Long id, FaqRequestDto request) {
        // 기존 FAQ를 찾아 요청값으로 내용을 갱신합니다.
        FaqEntity faq = getFaqOrThrow(id);
        faq.setCategory(request.category());
        faq.setQuestion(request.question());
        faq.setSortOrder(request.sortOrder());
        return toResponse(faq);
    }

    @Override
    @Transactional
    public void deleteFaq(Long id) {
        // FAQ는 기록 보존을 위해 DB에서 삭제하지 않고 비활성화합니다.
        FaqEntity faq = getFaqOrThrow(id);
        faq.setIsActive(false);
    }

    private FaqEntity getFaqOrThrow(Long id) {
        // FAQ가 없으면 공통적으로 404를 반환하기 위한 보조 메서드입니다.
        return faqRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "faq not found"));
    }

    private FaqResponseDto toResponse(FaqEntity faq) {
        // Entity를 API 응답 DTO로 변환합니다.
        return new FaqResponseDto(
                faq.getId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getSortOrder(),
                faq.getIsActive(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }

    private boolean isBlank(String value) {
        // null, 빈 문자열, 공백 문자열을 모두 비어 있는 값으로 처리합니다.
        return value == null || value.trim().isEmpty();
    }
}