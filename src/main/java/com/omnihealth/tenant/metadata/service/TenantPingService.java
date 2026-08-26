package com.omnihealth.tenant.metadata.service;

import com.omnihealth.tenant.context.TenantContext;
import com.omnihealth.tenant.metadata.dto.TenantPingResponse;
import com.omnihealth.tenant.metadata.entity.ApplicationMetadata;
import com.omnihealth.tenant.metadata.repository.ApplicationMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Proof-of-routing service. The {@code @Transactional("tenantTransactionManager")}
 * boundary opens a tenant-scoped session, which resolves the current tenant from
 * {@link TenantContext} and routes to that organization's physical database.
 * Writing then reading back a row demonstrates per-request database-per-tenant
 * routing end to end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPingService {

    private final ApplicationMetadataRepository applicationMetadataRepository;

    @Transactional("tenantTransactionManager")
    public TenantPingResponse ping() {
        applicationMetadataRepository.save(ApplicationMetadata.builder()
                .applicationName("tenant-ping")
                .applicationVersion("probe")
                .build());

        List<TenantPingResponse.Row> rows = applicationMetadataRepository.findAll().stream()
                .map(entity -> new TenantPingResponse.Row(
                        entity.getId(),
                        entity.getApplicationName(),
                        entity.getApplicationVersion(),
                        entity.getCreatedAt()
                ))
                .toList();

        return new TenantPingResponse(TenantContext.getTenantId(), rows.size(), rows);
    }
}
