package com.group5.MyMemories.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MemoryRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,
        @Size(max = 10000, message = "Content must be at most 10000 characters")
        String content,
        LocalDate memoryDate,
        @Size(max = 10, message = "A memory can have at most 10 tags")
        List<String> tags) {
}
