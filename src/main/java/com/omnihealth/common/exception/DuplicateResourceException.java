package com.omnihealth.common.exception;

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super(
                CommonErrorCode.RESOURCE_ALREADY_EXISTS,
                String.format("%s already exists with %s '%s'.", resource, field, value)
        );
    }
}
