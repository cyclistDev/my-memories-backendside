package com.group5.MyMemories.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private Long id;
    private String username;
    private String email;
    private String token;
}
