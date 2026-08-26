package com.omnihealth.platform.tenant.exception;

import com.omnihealth.common.exception.BusinessException;

/**
 * Raised when the physical provisioning of a tenant database fails (database
 * creation or migration). Thrown inside the asynchronous provisioning worker,
 * where it is caught and recorded on the job/tenant rows as a failure reason.
 */
public class TenantProvisioningException extends BusinessException {

    public TenantProvisioningException(TenantErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
