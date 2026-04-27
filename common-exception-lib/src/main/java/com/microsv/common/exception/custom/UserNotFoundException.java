package com.microsv.common.exception.custom;

import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.exception.BaseException;

public class UserNotFoundException extends BaseException {
    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
