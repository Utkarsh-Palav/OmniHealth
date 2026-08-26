package com.omnihealth.config.persistence;

import com.omnihealth.tenant.context.TenantIdentifierResolver;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Tenant persistence unit — the database-per-tenant EMF for
 * {@code com.omnihealth.tenant.*} entities/repositories.
 *
 * <p>Multi-tenancy in Hibernate 7 is active purely because a
 * {@link MultiTenantConnectionProvider} is present (the old {@code MultiTenancyStrategy}
 * enum was removed). The provider and {@link TenantIdentifierResolver} are wired as
 * <em>instances</em> into this EMF's property map only — never via {@code spring.jpa.*}
 * — so they cannot leak into the platform unit.</p>
 *
 * <p>{@code hbm2ddl=none} here: the unit bootstraps through {@code getAnyConnection()}
 * (the maintenance database, which lacks tenant tables), so schema validation must be
 * off — tenant schema correctness is guaranteed by Flyway instead. The dialect is set
 * explicitly and JDBC metadata access disabled so bootstrap performs no schema probing.</p>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.omnihealth.tenant",
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager"
)
public class TenantPersistenceConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            @Qualifier("dataSource") DataSource bootstrapDataSource,
            MultiTenantConnectionProvider<String> tenantConnectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver
    ) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabase(Database.POSTGRESQL);
        vendorAdapter.setGenerateDdl(false);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(bootstrapDataSource);
        emf.setJpaVendorAdapter(vendorAdapter);
        emf.setPackagesToScan("com.omnihealth.tenant");
        emf.setPersistenceUnitName("tenant");

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.jdbc.time_zone", "UTC");
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.highlight_sql", true);
        properties.put("hibernate.boot.allow_jdbc_metadata_access", false);
        properties.put("hibernate.multi_tenant_connection_provider", tenantConnectionProvider);
        properties.put("hibernate.tenant_identifier_resolver", tenantIdentifierResolver);
        emf.setJpaPropertyMap(properties);
        return emf;
    }

    @Bean
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEntityManagerFactory
    ) {
        return new JpaTransactionManager(tenantEntityManagerFactory);
    }
}
