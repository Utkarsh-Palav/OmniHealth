package com.omnihealth.platform.tenant.exception;

import com.omnihealth.common.exception.BusinessException;

/**
 * Raised when a request resolves to a tenant whose database has not been
 * provisioned (or is not yet {@code READY}). Surfaced to the client as a
 * {@code 409 CONFLICT} via the global exception handler.
 */
public class TenantNotProvisionedException extends BusinessException {

    public TenantNotProvisionedException(String tenantIdentifier) {
        super(
                TenantErrorCode.TENANT_NOT_PROVISIONED,
                "No READY tenant database found for tenant " + tenantIdentifier
        );
    }
}
