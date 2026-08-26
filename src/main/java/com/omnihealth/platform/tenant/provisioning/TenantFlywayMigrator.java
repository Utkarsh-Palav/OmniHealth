package com.omnihealth.platform.tenant.provisioning;

import com.omnihealth.config.persistence.TenantProvisioningProperties;
import com.omnihealth.platform.tenant.exception.TenantErrorCode;
import com.omnihealth.platform.tenant.exception.TenantProvisioningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Component;

/**
 * Applies the tenant baseline schema ({@code db/tenant/migration}) to a freshly
 * created tenant database using a throwaway, programmatic Flyway instance.
 *
 * <p>This is entirely separate from Boot's platform Flyway autoconfiguration
 * (which migrates {@code db/platform/migration} against {@code omnihealth_platform}).
 * Each tenant database gets its own {@code flyway_schema_history}. Flyway migration
 * is idempotent, so re-running after a partial failure is safe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFlywayMigrator {

    private final TenantProvisioningProperties properties;

    public void migrate(String databaseName, String schemaName) {
        TenantProvisioningProperties.Connection connection = properties.getConnection();
        TenantProvisioningProperties.Flyway flywayProps = properties.getFlyway();

        String jdbcUrl = "jdbc:postgresql://" + connection.getHost() + ":" + connection.getPort() + "/" + databaseName;
        String schema = (schemaName != null && !schemaName.isBlank()) ? schemaName : connection.getSchema();

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, connection.getUsername(), connection.getPassword())
                    .schemas(schema)
                    .locations(flywayProps.getLocations())
                    .baselineOnMigrate(flywayProps.isBaselineOnMigrate())
                    .load();

            MigrateResult result = flyway.migrate();
            log.info("Applied {} tenant migration(s) to database={} schema={}",
                    result.migrationsExecuted, databaseName, schema);
        } catch (Exception e) {
            throw new TenantProvisioningException(
                    TenantErrorCode.TENANT_MIGRATION_FAILED,
                    "Failed to migrate tenant database " + databaseName + ": " + e.getMessage(),
                    e
            );
        }
    }
}
