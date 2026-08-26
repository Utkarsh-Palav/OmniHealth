package com.omnihealth.common.constants;

public final class ApiRoutes {

    public static final String API_V1 = "/api/v1";

    public static final String PLATFORM = API_V1 + "/platform";

    // Auth
    public static final String AUTH = PLATFORM + "/auth";

    // Organization
    public static final String ORGANIZATIONS = PLATFORM + "/organizations";

    // User
    public static final String USERS = PLATFORM + "/users";

    // Onboarding
    public static final String ONBOARDING = API_V1 + "/onboarding";

    // Tenant (runtime routing / probe)
    public static final String TENANT = API_V1 + "/tenant";

    private ApiRoutes() {
    }
}
