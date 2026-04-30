package com.microsv.user_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Url;
import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.exception.BaseException;
import com.microsv.user_service.entity.User;
import com.microsv.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final UserRepository userRepository;

    private static final String FOLDER = "user-profiles";

    @Transactional
    public String uploadProfileImage(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String publicId = "user_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    Map.of(
                            "public_id", publicId,
                            "folder", FOLDER,
                            "resource_type", "image",
                            "transformation", new com.cloudinary.Transformation()
                                    .width(500)
                                    .height(500)
                                    .crop("fill")
                                    .gravity("face")
                    )
            );

            String url = (String) result.get("secure_url");
            user.setProfile(url);
            userRepository.save(user);

            log.info("Profile image uploaded for user {}: {}", userId, url);
            return url;

        } catch (IOException e) {
            log.error("Failed to upload profile image for user {}", userId, e);
            throw new BaseException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
}
