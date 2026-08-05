package com.omnihealth.common.exception;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(CommonErrorCode.VALIDATION_FAILED, message);
    }
}