package com.omnihealth.config.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
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
 * Platform persistence unit — the primary EMF, bound to {@code omnihealth_platform}.
 *
 * <p>Introducing a second EMF ({@link TenantPersistenceConfig}) makes Boot's
 * {@code HibernateJpaAutoConfiguration} back off, so both units are declared
 * explicitly. This unit is built with Spring ORM's
 * {@link LocalContainerEntityManagerFactoryBean} + {@link HibernateJpaVendorAdapter}
 * (package-stable across Spring/Boot versions) and keeps {@code hbm2ddl=validate}.</p>
 *
 * <p>Beans are named exactly {@code dataSource} / {@code entityManagerFactory} /
 * {@code transactionManager} and marked {@link Primary} so all existing repositories,
 * {@code @Transactional} methods, and JPA auditing continue to resolve to the platform
 * unit unqualified. Every entity uses explicit {@code @Column(name=...)}, so the
 * physical naming strategy is irrelevant to {@code validate}.</p>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.omnihealth.platform",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class PlatformPersistenceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("platform");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    /**
     * {@code @DependsOn("flyway")} ensures platform migrations run before this
     * EMF validates the schema. (Boot also wires this dependency automatically;
     * the explicit declaration is a defensive belt-and-suspenders.)
     */
    @Bean
    @Primary
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("dataSource") DataSource dataSource
    ) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabase(Database.POSTGRESQL);
        vendorAdapter.setGenerateDdl(false);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setJpaVendorAdapter(vendorAdapter);
        emf.setPackagesToScan("com.omnihealth.platform");
        emf.setPersistenceUnitName("platform");

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.jdbc.time_zone", "UTC");
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.highlight_sql", true);
        emf.setJpaPropertyMap(properties);
        return emf;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
