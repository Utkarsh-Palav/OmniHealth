package com.omnihealth.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Common application-wide error codes.
 *
 * These errors are shared across all modules.
 * Domain-specific modules (Patient, Appointment, Billing, etc.)
 * should define their own error code enums implementing BaseErrorCode.
 */
@Getter
public enum CommonErrorCode implements BaseErrorCode {

    // ==========================
    // 400 - Bad Request
    // ==========================

    BAD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Bad request"
    ),

    VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
    ),

    // ==========================
    // 401 - Unauthorized
    // ==========================

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication required"
    ),

    // ==========================
    // 403 - Forbidden
    // ==========================

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Access denied"
    ),

    // ==========================
    // 404 - Not Found
    // ==========================

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Resource not found"
    ),

    // ==========================
    // 409 - Conflict
    // ==========================

    RESOURCE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Resource already exists"
    ),

    // ==========================
    // 500 - Internal Server Error
    // ==========================

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error"
    );

    private final HttpStatus httpStatus;

    private final String defaultMessage;

    CommonErrorCode(
            HttpStatus httpStatus,
            String defaultMessage
    ) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getCode() {
        return name();
    }
}