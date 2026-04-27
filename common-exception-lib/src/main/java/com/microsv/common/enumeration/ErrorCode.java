package com.microsv.common.enumeration;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    //1000-1999: lỗi hệ thống, máy chủ, tài nguyên
    INTERNAL_SERVER_ERROR(1000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(1001, "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_CONNECTION_FAILED(1002, "Failed to connect to database", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_QUERY_ERROR(1003, "Error occurred while executing database query", HttpStatus.INTERNAL_SERVER_ERROR),
    CONFIGURATION_ERROR(1004, "Invalid or missing configuration", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_STORAGE_ERROR(1005, "File storage operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND(1006, "Requested file not found", HttpStatus.NOT_FOUND),
    FILE_UPLOAD_FAILED(1007, "File upload failed", HttpStatus.INTERNAL_SERVER_ERROR),
    NETWORK_ERROR(1008, "Network communication error", HttpStatus.INTERNAL_SERVER_ERROR),
    TIMEOUT_ERROR(1009, "Request timed out", HttpStatus.REQUEST_TIMEOUT),
    RESOURCE_LOCKED(1010, "Resource is currently locked or in use", HttpStatus.CONFLICT),
    SERIALIZATION_ERROR(1011, "Failed to serialize or deserialize data", HttpStatus.INTERNAL_SERVER_ERROR),
    MEMORY_OVERFLOW(1012, "Memory overflow or resource exhaustion", HttpStatus.INTERNAL_SERVER_ERROR),
    UNKNOWN_ERROR(1099, "Unknown system error occurred", HttpStatus.INTERNAL_SERVER_ERROR),


    //2000-2999: lỗi người dùng, xác thực ...
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(2002, "User already exists", HttpStatus.CONFLICT),
    INVALID_USER_ID(2003, "Invalid user ID", HttpStatus.BAD_REQUEST),
    INVALID_USERNAME_OR_PASSWORD(2004, "Invalid username or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(2005, "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(2006, "Account is disabled", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_IN_USE(2007, "Email already in use", HttpStatus.CONFLICT),
    PHONE_ALREADY_IN_USE(2008, "Phone number already in use", HttpStatus.CONFLICT),
    EMAIL_NOT_VERIFIED(2009, "Email not verified", HttpStatus.FORBIDDEN),
    PASSWORD_TOO_WEAK(2010, "Password does not meet security requirements", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(2011, "Passwords do not match", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(2012, "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2013, "Access token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(2014, "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED_ACCESS(2015, "Unauthorized access", HttpStatus.UNAUTHORIZED),
    FORBIDDEN_ACCESS(2016, "Forbidden action", HttpStatus.FORBIDDEN),
    ROLE_NOT_FOUND(2017, "Role not found", HttpStatus.NOT_FOUND),
    PERMISSION_DENIED(2018, "Permission denied", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(2019, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(2020, "User session has expired", HttpStatus.UNAUTHORIZED),
    LOGIN_FAILED(2021, "Login attempt failed", HttpStatus.UNAUTHORIZED),
    TOO_MANY_REQUESTS(2022, "Too many login attempts, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    ACCOUNT_NOT_ACTIVATED(2023, "Account not yet activated", HttpStatus.FORBIDDEN),
    OTP_INVALID(2024, "Invalid or expired OTP code", HttpStatus.BAD_REQUEST),
    OTP_LIMIT_EXCEEDED(2025, "OTP resend limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    PASSWORD_RESET_FAILED(2026, "Failed to reset password", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_PROFILE_NOT_FOUND(2027, "User profile not found", HttpStatus.NOT_FOUND),
    USER_DELETED(2028, "User account has been deleted", HttpStatus.GONE),


    //3000-3999: lỗi đầu vào
    INVALID_INPUT(3000, "Invalid input data", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD(3001, "Missing required field", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(3002, "Invalid email format", HttpStatus.BAD_REQUEST),
    INVALID_PHONE_FORMAT(3003, "Invalid phone number format", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FORMAT(3004, "Invalid date format", HttpStatus.BAD_REQUEST),
    VALUE_OUT_OF_RANGE(3005, "Input value out of range", HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(3006, "Invalid file type", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED(3007, "File size exceeds the allowed limit", HttpStatus.BAD_REQUEST),
    DUPLICATE_ENTRY(3008, "Duplicate entry not allowed", HttpStatus.CONFLICT),
    JSON_PARSE_ERROR(3009, "Failed to parse JSON request body", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(3010, "Missing parameter in request", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE(3011, "Unsupported content type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    BAD_REQUEST(3012, "Malformed or invalid request", HttpStatus.BAD_REQUEST),
    INVALID_QUERY_PARAM(3013, "Invalid query parameter", HttpStatus.BAD_REQUEST),


    //4000-4999: lỗi nghiệp vụ, logic
    POST_NOT_FOUND(4000, "Post not found", HttpStatus.NOT_FOUND),
    COMMENT_NOT_FOUND(4001, "Comment not found", HttpStatus.NOT_FOUND),
    LIKE_ALREADY_EXISTS(4002, "You already liked this post", HttpStatus.CONFLICT),
    MATCH_ALREADY_EXISTS(4003, "Match already exists", HttpStatus.CONFLICT),
    MATCH_NOT_FOUND(4004, "Match not found", HttpStatus.NOT_FOUND),
    ACTION_NOT_ALLOWED(4005, "Action not allowed", HttpStatus.FORBIDDEN),
    INVALID_OPERATION_STATE(4006, "Invalid operation state", HttpStatus.CONFLICT),
    PAYMENT_REQUIRED(4007, "Payment required for this action", HttpStatus.PAYMENT_REQUIRED),
    SUBSCRIPTION_EXPIRED(4008, "User subscription has expired", HttpStatus.PAYMENT_REQUIRED),
    REPORT_ALREADY_EXISTS(4009, "Report already submitted", HttpStatus.CONFLICT),
    RESOURCE_NOT_OWNED(4010, "You do not own this resource", HttpStatus.FORBIDDEN),
    LIMIT_EXCEEDED(4011, "Operation limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    TASK_ALREADY_COMPLETED(4012, "Task already completed", HttpStatus.CONFLICT),


    //5000-5999: lỗi tích hợp dịch vụ bên ngoài, mạng ,...
    THIRD_PARTY_SERVICE_ERROR(5000, "External service returned an error", HttpStatus.BAD_GATEWAY),
    API_CALL_FAILED(5001, "Failed to call external API", HttpStatus.BAD_GATEWAY),
    API_TIMEOUT(5002, "External API call timed out", HttpStatus.GATEWAY_TIMEOUT),
    WEBHOOK_FAILED(5003, "Webhook callback failed", HttpStatus.BAD_GATEWAY),
    CLOUD_STORAGE_ERROR(5004, "Error while accessing cloud storage", HttpStatus.INTERNAL_SERVER_ERROR),
    PAYMENT_GATEWAY_ERROR(5005, "Payment gateway error", HttpStatus.BAD_GATEWAY),
    EMAIL_SERVICE_ERROR(5006, "Email service error", HttpStatus.INTERNAL_SERVER_ERROR),
    SMS_SERVICE_ERROR(5007, "SMS service error", HttpStatus.INTERNAL_SERVER_ERROR),
    PUSH_NOTIFICATION_ERROR(5008, "Push notification sending failed", HttpStatus.INTERNAL_SERVER_ERROR),
    INTEGRATION_AUTH_FAILED(5009, "Failed to authenticate with external service", HttpStatus.UNAUTHORIZED),
    EXTERNAL_RESOURCE_NOT_FOUND(5010, "External resource not found", HttpStatus.NOT_FOUND),
    CDN_UPLOAD_FAILED(5011, "Failed to upload file to CDN", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_ERROR(5012, "AI service processing error", HttpStatus.INTERNAL_SERVER_ERROR),
    MAP_SERVICE_ERROR(5013, "Map or geolocation service error", HttpStatus.BAD_GATEWAY),
    CLOUD_PROVIDER_ERROR(5014, "Cloud provider service error", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public int getCode() { return code; }

    public String getMessage() { return message; }

    public HttpStatus getStatus() { return status; }
}
