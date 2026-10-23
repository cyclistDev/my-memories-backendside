package com.group5.MyMemories.controller;

import com.group5.MyMemories.dto.request.UserSettingsRequest;
import com.group5.MyMemories.dto.response.UserSettingsResponse;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public UserSettingsResponse getSettings(@AuthenticationPrincipal User user) {
        return userSettingsService.getSettings(user.getId());
    }

    @PutMapping
    public UserSettingsResponse updateSettings(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody(required = false) UserSettingsRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return userSettingsService.updateSettings(user.getId(), request);
    }
}
