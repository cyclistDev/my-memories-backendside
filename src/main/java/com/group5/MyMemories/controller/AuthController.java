package com.group5.MyMemories.controller;

import com.group5.MyMemories.dto.request.LoginRequest;
import com.group5.MyMemories.dto.request.RegisterRequest;
import com.group5.MyMemories.dto.response.LoginResponse;
import com.group5.MyMemories.dto.response.RegisterResponse;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody(required = false) RegisterRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return authService.login(request);
    }
}
