package com.group5.MyMemories.dto.response;

import com.group5.MyMemories.dto.MemoryDTO;

import java.util.List;

public record OnThisDayYearGroup(int year, List<MemoryDTO> memories) {
}
