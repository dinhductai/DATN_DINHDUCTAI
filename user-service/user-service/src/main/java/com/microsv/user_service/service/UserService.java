package com.microsv.user_service.service;



import com.microsv.user_service.dto.request.UserCreationRequest;
import com.microsv.user_service.dto.request.UserUpdateRequest;
import com.microsv.user_service.dto.response.UserAuthResponse;
import com.microsv.user_service.dto.response.UserProfileResponse;
import com.microsv.user_service.dto.response.UserResponse;
import com.microsv.user_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    User getUserById(Long id);
    User createUser(User user);
    UserResponse createUser(UserCreationRequest request);
    UserResponse getUser(Long userId);
    UserResponse updateUser(Long userId, UserUpdateRequest request);
    void deleteUser(Long userId);
    UserAuthResponse getUserByEmail(String email);
    String getUserEmailById(Long userId);
    long countUser();
    List<UserResponse> searchUserName(String name);
    Long countUserRegisterThisWeek();
    UserProfileResponse getUserProfile(Long userId);
    List<Long> getAllUserIds();
}
