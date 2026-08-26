package com.omnihealth.tenant.context;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to. Reads the
 * per-thread {@link TenantContext}; falls back to {@link TenantContext#BOOTSTRAP}
 * so the resolved identifier is never {@code null} (which Hibernate rejects).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : TenantContext.BOOTSTRAP;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
