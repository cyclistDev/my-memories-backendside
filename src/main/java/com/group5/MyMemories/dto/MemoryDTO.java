package com.group5.MyMemories.dto;

import com.group5.MyMemories.entity.Memory;

public record MemoryDTO(Long id, String title, String content) {

    public static MemoryDTO from(Memory memory) {
        return new MemoryDTO(memory.getId(), memory.getTitle(), memory.getContent());
    }
}
