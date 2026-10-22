package com.group5.MyMemories.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemoryRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,
        String content) {
}
