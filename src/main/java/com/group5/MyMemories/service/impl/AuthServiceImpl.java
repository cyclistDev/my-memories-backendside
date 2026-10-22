package com.group5.MyMemories.service.impl;

import com.group5.MyMemories.dto.request.LoginRequest;
import com.group5.MyMemories.dto.request.RegisterRequest;
import com.group5.MyMemories.dto.response.LoginResponse;
import com.group5.MyMemories.dto.response.RegisterResponse;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.repositories.UserRepository;
import com.group5.MyMemories.service.AuthService;
import com.group5.MyMemories.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public ResponseEntity<RegisterResponse> register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists", "username");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already exists", "email");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());

        RegisterResponse response = RegisterResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .token(token)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password", "email");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        LoginResponse response = LoginResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .token(token)
                .build();
        return ResponseEntity.ok(response);
    }
}
