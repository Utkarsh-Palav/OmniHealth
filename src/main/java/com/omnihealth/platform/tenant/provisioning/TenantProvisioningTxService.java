package com.omnihealth.platform.tenant.provisioning;

import com.omnihealth.platform.tenant.entity.ProvisioningJob;
import com.omnihealth.platform.tenant.entity.TenantDatabase;
import com.omnihealth.platform.tenant.enums.ProvisioningJobStatus;
import com.omnihealth.platform.tenant.enums.TenantDatabaseStatus;
import com.omnihealth.platform.tenant.repository.ProvisioningJobRepository;
import com.omnihealth.platform.tenant.repository.TenantDatabaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns the short, committed status transitions of a provisioning job and its
 * tenant-database row. Kept as a <em>separate</em> bean from the async worker so
 * each {@code @Transactional} method runs through a proxy (self-invocation from
 * {@link TenantProvisioningService} would otherwise bypass transaction advice).
 *
 * <p>All methods bind to the platform transaction manager ({@code "transactionManager"}),
 * since these rows live in the platform database.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningTxService {

    /** Defensive cap; {@code failure_reason} is TEXT, so this only keeps rows sane. */
    private static final int MAX_FAILURE_REASON_LENGTH = 4000;

    private final ProvisioningJobRepository provisioningJobRepository;
    private final TenantDatabaseRepository tenantDatabaseRepository;

    /** Coordinates needed by the worker to create + migrate the physical database. */
    public record TenantTarget(String databaseName, String schemaName) {
    }

    @Transactional("transactionManager")
    public void markRunning(UUID jobId, UUID tenantDatabaseId) {
        Instant now = Instant.now();

        ProvisioningJob job = requireJob(jobId);
        job.setStatus(ProvisioningJobStatus.RUNNING);
        job.setStartedAt(now);
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        provisioningJobRepository.save(job);

        TenantDatabase tenantDatabase = requireTenantDatabase(tenantDatabaseId);
        tenantDatabase.setStatus(TenantDatabaseStatus.PROVISIONING);
        tenantDatabase.setProvisionedAt(now);
        tenantDatabaseRepository.save(tenantDatabase);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public TenantTarget loadTarget(UUID tenantDatabaseId) {
        TenantDatabase tenantDatabase = requireTenantDatabase(tenantDatabaseId);
        return new TenantTarget(tenantDatabase.getDatabaseName(), tenantDatabase.getSchemaName());
    }

    @Transactional("transactionManager")
    public void markReady(UUID jobId, UUID tenantDatabaseId) {
        Instant now = Instant.now();

        TenantDatabase tenantDatabase = requireTenantDatabase(tenantDatabaseId);
        tenantDatabase.setStatus(TenantDatabaseStatus.READY);
        tenantDatabase.setReadyAt(now);
        tenantDatabase.setFailedAt(null);
        tenantDatabase.setFailureReason(null);
        tenantDatabaseRepository.save(tenantDatabase);

        ProvisioningJob job = requireJob(jobId);
        job.setStatus(ProvisioningJobStatus.COMPLETED);
        job.setCompletedAt(now);
        job.setFailedAt(null);
        job.setFailureReason(null);
        provisioningJobRepository.save(job);
    }

    @Transactional("transactionManager")
    public void markFailed(UUID jobId, UUID tenantDatabaseId, String reason) {
        Instant now = Instant.now();
        String trimmedReason = truncate(reason);

        TenantDatabase tenantDatabase = requireTenantDatabase(tenantDatabaseId);
        tenantDatabase.setStatus(TenantDatabaseStatus.FAILED);
        tenantDatabase.setFailedAt(now);
        tenantDatabase.setFailureReason(trimmedReason);
        tenantDatabaseRepository.save(tenantDatabase);

        ProvisioningJob job = requireJob(jobId);
        job.setStatus(ProvisioningJobStatus.FAILED);
        job.setFailedAt(now);
        job.setFailureReason(trimmedReason);
        provisioningJobRepository.save(job);
    }

    private ProvisioningJob requireJob(UUID jobId) {
        return provisioningJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("ProvisioningJob not found: " + jobId));
    }

    private TenantDatabase requireTenantDatabase(UUID tenantDatabaseId) {
        return tenantDatabaseRepository.findById(tenantDatabaseId)
                .orElseThrow(() -> new IllegalStateException("TenantDatabase not found: " + tenantDatabaseId));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_FAILURE_REASON_LENGTH
                ? value
                : value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
