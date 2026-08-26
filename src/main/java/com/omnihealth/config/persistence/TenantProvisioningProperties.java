package com.omnihealth.config.persistence;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for tenant database provisioning and runtime routing, bound
 * from {@code app.tenant.*}.
 *
 * <p><strong>Credentials live here (config/env), never in the
 * {@code tenant_databases} table.</strong> The table stores only non-secret
 * connection coordinates (host, port, database name, schema).</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.tenant")
public class TenantProvisioningProperties {

    /** Maintenance/admin connection used to run {@code CREATE DATABASE}. */
    private Admin admin = new Admin();

    /** Coordinates + credentials used to connect to per-tenant databases. */
    private Connection connection = new Connection();

    /** Programmatic Flyway settings applied to each tenant database. */
    private Flyway flyway = new Flyway();

    @Getter
    @Setter
    public static class Admin {

        /** JDBC URL of a maintenance database (never the database being created). */
        @NotBlank
        private String url;

        @NotBlank
        private String username;

        /** May be blank for trust/peer authentication. */
        private String password;
    }

    @Getter
    @Setter
    public static class Connection {

        @NotBlank
        private String host;

        private int port = 5432;

        @NotBlank
        private String username;

        /** May be blank for trust/peer authentication. */
        private String password;

        private String schema = "public";

        /** Upper bound on the per-tenant Hikari pool size. */
        private int maxPoolSize = 5;
    }

    @Getter
    @Setter
    public static class Flyway {

        private String locations = "classpath:db/tenant/migration";

        private boolean baselineOnMigrate = true;
    }
}
