package com.omnihealth.common.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(
                CommonErrorCode.RESOURCE_NOT_FOUND,
                String.format("%s not found with %s '%s'.", resource, field, value)
        );
    }
}
