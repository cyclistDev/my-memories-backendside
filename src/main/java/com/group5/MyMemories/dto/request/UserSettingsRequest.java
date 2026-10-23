package com.group5.MyMemories.dto.request;

import com.group5.MyMemories.entity.ResurfaceFrequency;
import jakarta.validation.constraints.NotNull;

public record UserSettingsRequest(
        @NotNull(message = "Resurface frequency is required")
        ResurfaceFrequency resurfaceFrequency) {
}
