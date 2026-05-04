package com.microsv.user_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
public class UserResponse {
    private Long userId;
    private String userName;
    private String email;
    private String profile;
    private LocalDateTime createdAt;
    private Set<String> roles;
}
