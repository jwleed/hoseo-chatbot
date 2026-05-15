package com.hoseo.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record UserCategoryRequestDto(
        @JsonProperty("user_id") String userId,
        List<String> categories
) {}
