package com.omnihealth.config.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing across the application.
 *
 * Automatically populates fields annotated with:
 * - @CreatedDate
 * - @LastModifiedDate
 */

@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

}
