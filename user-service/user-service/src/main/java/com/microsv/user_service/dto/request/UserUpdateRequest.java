package com.microsv.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password; // Optional - chỉ gửi khi muốn đổi password
    
    @NotNull
    private String userName;
    
    @NotNull
    @Email
    private String email;
    
    private String profile;
}