package com.omnihealth.tenant.context;

/**
 * Holds the tenant identifier (the organization UUID, as a string) for the
 * current thread. Populated per request by {@link TenantContextFilter} and read
 * by {@link TenantIdentifierResolver} when Hibernate opens a tenant-scoped
 * session.
 *
 * <p>The {@link #BOOTSTRAP} sentinel is returned when no tenant is bound (e.g.
 * platform-only requests, background bootstrap). The tenant connection provider
 * treats it as "use the maintenance/admin connection", so a missing tenant can
 * never silently route to an arbitrary tenant database.</p>
 */
public final class TenantContext {

    /**
     * Sentinel tenant id used when no organization is bound to the thread.
     * Deliberately not a valid UUID so it can never collide with a real tenant.
     */
    public static final String BOOTSTRAP = "__bootstrap__";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
