package com.microsv.user_service.controller.internal;

import com.microsv.user_service.dto.response.UserAuthResponse;
import com.microsv.user_service.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/users") 
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInternalController {

    UserService userService;


    @GetMapping("/{email}")
    public ResponseEntity<UserAuthResponse> searchUserByEmail(@PathVariable String email) {
        UserAuthResponse userResponse = userService.getUserByEmail(email);
        return ResponseEntity.ok(userResponse);
    }
    
    @GetMapping("/{id}/email")
    public ResponseEntity<String> getUserEmail(@PathVariable("id") Long userId) {
        String email = userService.getUserEmailById(userId);
        return ResponseEntity.ok(email);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getAllUserIds() {
        List<Long> ids = userService.getAllUserIds();
        return ResponseEntity.ok(ids);
    }
}
