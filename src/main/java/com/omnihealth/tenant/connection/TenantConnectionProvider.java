package com.omnihealth.tenant.connection;

import com.omnihealth.config.persistence.TenantProvisioningProperties;
import com.omnihealth.platform.tenant.exception.TenantNotProvisionedException;
import com.omnihealth.tenant.context.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hibernate database-per-tenant connection router.
 *
 * <p>The tenant identifier is the organization UUID (as a string). Per-tenant
 * Hikari pools are built lazily on first use and cached; they are rebuilt from
 * {@code tenant_databases} (the source of truth) after a restart. Coordinates
 * come from that catalog row, credentials from {@code app.tenant.connection.*}
 * — credentials are never stored in the table.</p>
 *
 * <p>The {@link TenantContext#BOOTSTRAP} sentinel (and a null identifier) route
 * to the maintenance connection, so a request without a bound tenant can never
 * silently read or write an arbitrary tenant database.</p>
 */
@Slf4j
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String LOOKUP_SQL = """
            SELECT database_name, host, port, schema_name, connection_pool_size, status
            FROM tenant_databases
            WHERE organization_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private static final String READY_STATUS = "READY";

    private final DataSource adminDataSource;
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantProvisioningProperties properties;

    private final ConcurrentHashMap<String, HikariDataSource> tenantPools = new ConcurrentHashMap<>();

    public TenantConnectionProvider(
            @Qualifier("tenantAdminDataSource") DataSource adminDataSource,
            @Qualifier("dataSource") DataSource platformDataSource,
            TenantProvisioningProperties properties
    ) {
        this.adminDataSource = adminDataSource;
        this.platformJdbcTemplate = new JdbcTemplate(platformDataSource);
        this.properties = properties;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return adminDataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (tenantIdentifier == null || TenantContext.BOOTSTRAP.equals(tenantIdentifier)) {
            return getAnyConnection();
        }
        return tenantPools.computeIfAbsent(tenantIdentifier, this::buildTenantPool).getConnection();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    /**
     * Builds a Hikari pool for a tenant by resolving its catalog row. Runs
     * outside any Hibernate session (uses a plain {@link JdbcTemplate} over the
     * platform datasource), so it is safe to call during connection acquisition.
     * Throws (rather than caching null) when the tenant is missing or not READY,
     * so a failed lookup never poisons the pool map.
     */
    private HikariDataSource buildTenantPool(String tenantIdentifier) {
        UUID organizationId;
        try {
            organizationId = UUID.fromString(tenantIdentifier);
        } catch (IllegalArgumentException e) {
            throw new TenantNotProvisionedException(tenantIdentifier);
        }

        List<TenantRow> rows = platformJdbcTemplate.query(
                LOOKUP_SQL,
                (rs, rowNum) -> new TenantRow(
                        rs.getString("database_name"),
                        rs.getString("host"),
                        rs.getInt("port"),
                        rs.getString("schema_name"),
                        rs.getInt("connection_pool_size"),
                        rs.getString("status")
                ),
                organizationId
        );

        if (rows.isEmpty()) {
            throw new TenantNotProvisionedException(tenantIdentifier);
        }

        TenantRow row = rows.getFirst();
        if (!READY_STATUS.equals(row.status())) {
            throw new TenantNotProvisionedException(tenantIdentifier);
        }

        TenantProvisioningProperties.Connection conn = properties.getConnection();
        String jdbcUrl = "jdbc:postgresql://" + row.host() + ":" + row.port() + "/" + row.databaseName();

        int cap = conn.getMaxPoolSize();
        int poolSize = row.connectionPoolSize() > 0 ? Math.min(row.connectionPoolSize(), cap) : cap;

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("tenant-" + row.databaseName());
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(conn.getUsername());
        dataSource.setPassword(conn.getPassword());
        dataSource.setMaximumPoolSize(poolSize);
        dataSource.setMinimumIdle(0);
        dataSource.setIdleTimeout(30_000);
        if (row.schemaName() != null && !row.schemaName().isBlank()) {
            dataSource.setSchema(row.schemaName());
        }

        log.info("Built tenant connection pool for organizationId={} database={} (poolSize={})",
                tenantIdentifier, row.databaseName(), poolSize);
        return dataSource;
    }

    @PreDestroy
    public void closePools() {
        tenantPools.values().forEach(HikariDataSource::close);
        tenantPools.clear();
    }

    private record TenantRow(
            String databaseName,
            String host,
            int port,
            String schemaName,
            int connectionPoolSize,
            String status
    ) {
    }
}
