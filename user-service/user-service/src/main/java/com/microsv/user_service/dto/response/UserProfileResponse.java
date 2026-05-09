package com.microsv.user_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class UserProfileResponse {
    private Long userId;
    private String userName;
    private String email;
    private String profile;
    private OffsetDateTime createdAt;
    private Integer registerSince;
}
