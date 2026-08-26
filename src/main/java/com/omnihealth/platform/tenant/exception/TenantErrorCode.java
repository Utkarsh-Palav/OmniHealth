package com.omnihealth.platform.tenant.exception;

import com.omnihealth.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error codes for tenant database provisioning and runtime routing.
 */
@Getter
public enum TenantErrorCode implements BaseErrorCode {

    TENANT_NOT_PROVISIONED(
            HttpStatus.CONFLICT,
            "The tenant database is not provisioned or not ready yet"
    ),

    TENANT_CONTEXT_MISSING(
            HttpStatus.CONFLICT,
            "No tenant is bound to the current request"
    ),

    TENANT_DATABASE_CREATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to create the tenant database"
    ),

    TENANT_MIGRATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to apply tenant database migrations"
    );

    private final HttpStatus httpStatus;

    private final String defaultMessage;

    TenantErrorCode(
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
