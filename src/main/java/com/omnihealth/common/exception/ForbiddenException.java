package com.omnihealth.common.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(CommonErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(CommonErrorCode.FORBIDDEN, message);
    }
}