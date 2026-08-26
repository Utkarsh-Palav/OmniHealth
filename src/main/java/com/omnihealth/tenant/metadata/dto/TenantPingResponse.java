package com.omnihealth.tenant.metadata.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for the tenant routing probe. Echoes the resolved tenant id and the
 * rows currently visible in that tenant's {@code application_metadata} table.
 */
public record TenantPingResponse(
        String tenantId,
        int rowCount,
        List<Row> rows
) {

    public record Row(
            Long id,
            String applicationName,
            String applicationVersion,
            LocalDateTime createdAt
    ) {
    }
}
