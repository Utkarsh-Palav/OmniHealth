package com.omnihealth.config.persistence;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated maintenance datasource used to run {@code CREATE DATABASE} and to
 * back Hibernate's {@code getAnyConnection()} (bootstrap / non-tenanted work).
 *
 * <p>Kept tiny and separate from the platform pool so administrative work never
 * starves request-serving connections. Auto-commit is on because
 * {@code CREATE DATABASE} cannot run inside a transaction block.</p>
 *
 * <p>Also the anchor for {@link TenantProvisioningProperties} via
 * {@link EnableConfigurationProperties}.</p>
 */
@Configuration
@EnableConfigurationProperties(TenantProvisioningProperties.class)
@RequiredArgsConstructor
public class TenantAdminDataSourceConfig {

    private final TenantProvisioningProperties properties;

    @Bean
    public HikariDataSource tenantAdminDataSource() {
        TenantProvisioningProperties.Admin admin = properties.getAdmin();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("tenant-admin");
        dataSource.setJdbcUrl(admin.getUrl());
        dataSource.setUsername(admin.getUsername());
        dataSource.setPassword(admin.getPassword());
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(0);
        dataSource.setAutoCommit(true);
        return dataSource;
    }
}
