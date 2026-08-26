package com.omnihealth.platform.tenant.provisioning;

import com.omnihealth.platform.tenant.exception.TenantErrorCode;
import com.omnihealth.platform.tenant.exception.TenantProvisioningException;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Creates the physical PostgreSQL database for a tenant using the maintenance
 * (admin) connection.
 *
 * <p>{@code CREATE DATABASE} cannot run inside a transaction block and cannot
 * target the database being created, so this runs on an auto-commit connection
 * against the maintenance database. PostgreSQL has no {@code CREATE DATABASE IF
 * NOT EXISTS}, so existence is guarded with a {@code pg_database} lookup, making
 * the operation idempotent (retry-safe). The database name is regex-validated
 * and double-quoted because identifiers cannot be bind parameters.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDatabaseAdmin {

    private static final Pattern VALID_DATABASE_NAME = Pattern.compile("^omnihealth_tenant_[a-z0-9]{32}$");

    private final HikariDataSource tenantAdminDataSource;

    public void createDatabaseIfAbsent(String databaseName) {
        if (databaseName == null || !VALID_DATABASE_NAME.matcher(databaseName).matches()) {
            throw new TenantProvisioningException(
                    TenantErrorCode.TENANT_DATABASE_CREATION_FAILED,
                    "Refusing to create database with invalid name: " + databaseName,
                    null
            );
        }

        try (Connection connection = tenantAdminDataSource.getConnection()) {
            // CREATE DATABASE must not run inside a transaction block.
            connection.setAutoCommit(true);

            if (databaseExists(connection, databaseName)) {
                log.info("Tenant database already exists; skipping CREATE: {}", databaseName);
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE \"" + databaseName + "\"");
            }
            log.info("Created tenant database: {}", databaseName);
        } catch (SQLException e) {
            throw new TenantProvisioningException(
                    TenantErrorCode.TENANT_DATABASE_CREATION_FAILED,
                    "Failed to create tenant database " + databaseName + ": " + e.getMessage(),
                    e
            );
        }
    }

    private boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
