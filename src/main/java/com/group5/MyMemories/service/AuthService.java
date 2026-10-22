package com.group5.MyMemories.service;

import com.group5.MyMemories.dto.request.LoginRequest;
import com.group5.MyMemories.dto.request.RegisterRequest;
import com.group5.MyMemories.dto.response.LoginResponse;
import com.group5.MyMemories.dto.response.RegisterResponse;

import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<RegisterResponse> register(RegisterRequest request);
    ResponseEntity<LoginResponse> login(LoginRequest request);
}
