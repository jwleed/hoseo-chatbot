package com.hoseo.chatbot.controller;

import com.hoseo.chatbot.dto.FaqRequestDto;
import com.hoseo.chatbot.dto.FaqResponseDto;
import com.hoseo.chatbot.service.FaqService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    private final FaqService faqService;
    private final String adminApiKey;

    public FaqController(FaqService faqService, @Value("${admin.api-key}") String adminApiKey) {
        this.faqService = faqService;
        this.adminApiKey = adminApiKey;
    }

    private void checkAdminKey(String apiKey) {
        if (!adminApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin key");
        }
    }

    @GetMapping
    public List<FaqResponseDto> getFaqs(@RequestParam(required = false) String category) {
        // category 파라미터가 없으면 전체 활성 FAQ를 조회하고,
        // category가 있으면 해당 카테고리의 활성 FAQ만 조회합니다.
        return faqService.getFaqs(category);
    }

@PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaqResponseDto createFaq(
            @RequestHeader("X-Admin-Key") String apiKey,
            @Valid @RequestBody FaqRequestDto request) {
        checkAdminKey(apiKey);
        return faqService.createFaq(request);
    }

    @PutMapping("/{id}")
    public FaqResponseDto updateFaq(
            @RequestHeader("X-Admin-Key") String apiKey,
            @PathVariable Long id,
            @Valid @RequestBody FaqRequestDto request) {
        checkAdminKey(apiKey);
        return faqService.updateFaq(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFaq(
            @RequestHeader("X-Admin-Key") String apiKey,
            @PathVariable Long id) {
        checkAdminKey(apiKey);
        faqService.deleteFaq(id);
    }

}
