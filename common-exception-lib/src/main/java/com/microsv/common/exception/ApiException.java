package com.microsv.common.exception;

import com.microsv.common.enumeration.ErrorCode;

public class ApiException extends BaseException {
    public ApiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}