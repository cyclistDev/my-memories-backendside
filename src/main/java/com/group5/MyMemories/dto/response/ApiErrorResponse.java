package com.group5.MyMemories.dto.response;

import java.util.Map;

public record ApiErrorResponse(String message, Map<String, String> fieldErrors) {
}
