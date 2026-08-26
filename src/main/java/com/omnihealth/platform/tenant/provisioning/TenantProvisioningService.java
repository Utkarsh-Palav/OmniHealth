package com.omnihealth.platform.tenant.provisioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Asynchronous tenant-provisioning worker. Triggered after the onboarding
 * transaction commits, so the {@code QUEUED} job and {@code PENDING} tenant row
 * are already visible.
 *
 * <p>Lifecycle: {@code markRunning} → create physical database → apply tenant
 * migrations → {@code markReady}. Any failure is logged and recorded on the job
 * and tenant rows via {@code markFailed} (never silently swallowed). The method
 * is {@code void @Async}, so exceptions are not rethrown to a caller — the
 * recorded failure is the durable signal, and re-invoking provisioning resets
 * the row and retries (create + migrate are both idempotent).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantProvisioningTxService provisioningTxService;
    private final TenantDatabaseAdmin tenantDatabaseAdmin;
    private final TenantFlywayMigrator tenantFlywayMigrator;

    @Async
    public void provisionAsync(UUID jobId, UUID tenantDatabaseId) {
        log.info("Starting async tenant provisioning: jobId={} tenantDatabaseId={}", jobId, tenantDatabaseId);
        try {
            provisioningTxService.markRunning(jobId, tenantDatabaseId);

            TenantProvisioningTxService.TenantTarget target = provisioningTxService.loadTarget(tenantDatabaseId);
            tenantDatabaseAdmin.createDatabaseIfAbsent(target.databaseName());
            tenantFlywayMigrator.migrate(target.databaseName(), target.schemaName());

            provisioningTxService.markReady(jobId, tenantDatabaseId);
            log.info("Completed tenant provisioning: jobId={} database={}", jobId, target.databaseName());
        } catch (Exception e) {
            log.error("Tenant provisioning FAILED: jobId={} tenantDatabaseId={}: {}",
                    jobId, tenantDatabaseId, e.getMessage(), e);
            try {
                provisioningTxService.markFailed(jobId, tenantDatabaseId, e.getMessage());
            } catch (Exception recordingFailure) {
                log.error("Failed to record provisioning failure for jobId={}", jobId, recordingFailure);
            }
        }
    }
}
