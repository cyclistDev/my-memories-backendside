package com.group5.MyMemories.service;

import com.group5.MyMemories.dto.request.UserSettingsRequest;
import com.group5.MyMemories.dto.response.UserSettingsResponse;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserSettingsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        User user = findUser(userId);
        return new UserSettingsResponse(user.getResurfaceFrequency());
    }

    public UserSettingsResponse updateSettings(Long userId, UserSettingsRequest request) {
        User user = findUser(userId);
        user.setResurfaceFrequency(request.resurfaceFrequency());
        userRepository.save(user);
        return new UserSettingsResponse(user.getResurfaceFrequency());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
