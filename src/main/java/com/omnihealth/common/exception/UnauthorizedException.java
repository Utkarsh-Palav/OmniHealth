package com.omnihealth.common.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(CommonErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(CommonErrorCode.UNAUTHORIZED, message);
    }
}