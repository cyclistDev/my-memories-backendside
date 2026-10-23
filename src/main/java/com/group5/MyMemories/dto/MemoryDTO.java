package com.group5.MyMemories.dto;

import com.group5.MyMemories.entity.Memory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemoryDTO(
        Long id,
        String title,
        String content,
        LocalDate memoryDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> tags) {

    public static MemoryDTO from(Memory memory) {
        return new MemoryDTO(
                memory.getId(),
                memory.getTitle(),
                memory.getContent(),
                memory.getMemoryDate(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted()
                        .toList());
    }
}
