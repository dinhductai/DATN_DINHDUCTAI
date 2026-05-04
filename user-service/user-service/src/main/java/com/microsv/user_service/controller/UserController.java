package com.microsv.user_service.controller;


import com.microsv.user_service.dto.request.UserCreationRequest;
import com.microsv.user_service.dto.request.UserUpdateRequest;
import com.microsv.user_service.dto.response.UserAuthResponse;
import com.microsv.user_service.dto.response.UserResponse;
import com.microsv.user_service.entity.User;
import com.microsv.user_service.service.CloudinaryService;
import com.microsv.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    CloudinaryService cloudinaryService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserCreationRequest request) {
        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/upload-profile/{userId}")
    public ResponseEntity<String> uploadProfile(@PathVariable Long userId, @RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadProfileImage(userId, file);
        return ResponseEntity.ok(url);
    }


    @PostMapping("/create")
    public ResponseEntity<User> createUser(@Valid @RequestBody User request) {
        User response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


    //tìm theo id
//    @GetMapping("/{userId}")
//    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
//        return ResponseEntity.ok(userService.getUser(userId));
//    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        // Lưu ý: Cần thêm logic getAllUsers() trong service
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/counts")
    public ResponseEntity<Long> countUser(){
        return ResponseEntity.ok(userService.countUser());
    }

    @GetMapping("/counts-register")
    public ResponseEntity<Long> countUserRegister(){
        return ResponseEntity.ok(userService.countUserRegisterThisWeek());
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam("keyword") String keyword) {
        List<UserResponse> users = userService.searchUserName(keyword);
        return ResponseEntity.ok(users);
    }

    // @GetMapping("/search-email")
    // public ResponseEntity<List<User>> getAllUsersEmail(@RequestParam("keyword") String keyword) {
    //     List<User> users = userService.searchUser(keyword);
    //     return ResponseEntity.ok(users);
    // }
}