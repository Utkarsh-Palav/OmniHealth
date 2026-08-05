package com.omnihealth.common.exception;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(CommonErrorCode.BAD_REQUEST, message);
    }
}