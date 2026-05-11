package com.microsv.task_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {
    @GetMapping("/{id}/email")
    String getUserEmail(@PathVariable("id") Long userId);

    @GetMapping("/ids")
    List<Long> getAllUserIds();
}
