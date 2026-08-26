package com.omnihealth.platform.organization.entity;

import com.omnihealth.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_organizations")
public class Organization extends BaseEntity {

    @Column(name = "organization_code", nullable = false, unique = true, length = 50)
    private String organizationCode;

    @Column(name = "legal_name", nullable = false, length = 225)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 225)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "organization_type", nullable = false)
    private OrganizationType organizationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private OrganizationStatus status;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "tax_identification_number", length = 100)
    private String taxIdentificationNumber;

    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    @Column(name = "pan_number", length = 10)
    private String panNumber;

    @Column(name = "official_email", nullable = false, columnDefinition = "citext")
    private String officialEmail;

    @Column(name = "official_phone_country_code", length = 5)
    private String officialPhoneCountryCode;

    @Column(name = "official_phone_number", length = 20)
    private String officialPhoneNumber;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "registered_address_line1", length = 255)
    private String registeredAddressLine1;

    @Column(name = "registered_address_line2", length = 255)
    private String registeredAddressLine2;

    @Column(name = "registered_city", length = 100)
    private String registeredCity;

    @Column(name = "registered_state", length = 100)
    private String registeredState;

    @Column(name = "registered_postal_code", length = 20)
    private String registeredPostalCode;

    @Column(name = "registered_country_code", length = 2)
    private String registeredCountryCode;

    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;
}