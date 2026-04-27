package com.microsv.common.enumeration;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_NOT_FOUND(1001, "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(1002, "User already exists", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(1003, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    TASK_NOT_FOUND(2001, "Task not found", HttpStatus.NOT_FOUND),
    INVALID_DATE_FORMAT(2002, "Invalid date format", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_NOT_FOUND(2003, "Subscription not found", HttpStatus.NOT_FOUND),
    INTERNAL_SERVER_ERROR(9999, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
