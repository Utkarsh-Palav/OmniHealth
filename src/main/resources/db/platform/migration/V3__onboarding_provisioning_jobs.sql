-- =====================================================
-- OmniHealth Platform Database
-- Version : V3 – Onboarding uniqueness + provisioning jobs
-- PostgreSQL : 17
-- Flyway Migration
-- =====================================================

-- =====================================================
-- FIX 1 : Allow re-onboarding after soft-delete
-- -------------------------------------------------
-- V1 enforced onboarding uniqueness with a table
-- constraint on organization_id, which also blocks a
-- fresh onboarding row after the previous one is soft-
-- deleted. Replace it with a partial unique index that
-- only considers live (non soft-deleted) rows.
-- =====================================================

ALTER TABLE platform_organization_onboarding
    DROP CONSTRAINT uq_platform_org_onboarding_organization;

CREATE UNIQUE INDEX uq_platform_org_onboarding_organization
ON platform_organization_onboarding(organization_id)
WHERE deleted_at IS NULL;

-- =====================================================
-- FIX 2 : Missing platform_provisioning_jobs table
-- -------------------------------------------------
-- The platform_provisioning_job_status enum was defined
-- in V1 but never used. This table records each tenant
-- provisioning attempt for an organization and is the
-- extension point for real, out-of-band provisioning.
-- =====================================================

CREATE TABLE platform_provisioning_jobs (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    tenant_database_id UUID,

    status platform_provisioning_job_status NOT NULL DEFAULT 'QUEUED',

    attempts INTEGER NOT NULL DEFAULT 0,

    started_at TIMESTAMPTZ,

    completed_at TIMESTAMPTZ,

    failed_at TIMESTAMPTZ,

    failure_reason TEXT,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ppj_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ppj_tenant_database
        FOREIGN KEY (tenant_database_id)
        REFERENCES tenant_databases(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ppj_attempts
        CHECK (attempts >= 0),

    CONSTRAINT chk_ppj_completed
        CHECK (completed_at IS NULL OR completed_at >= created_at),

    CONSTRAINT chk_ppj_failed
        CHECK (failed_at IS NULL OR failed_at >= created_at)
);

COMMENT ON TABLE platform_provisioning_jobs IS
'Records tenant-database provisioning attempts for an organization during onboarding.';

COMMENT ON COLUMN platform_provisioning_jobs.attempts IS
'Number of provisioning attempts made for this job.';

COMMENT ON COLUMN platform_provisioning_jobs.failure_reason IS
'Reason recorded when a provisioning attempt fails.';

-- =====================================================
-- Indexes : platform_provisioning_jobs
-- =====================================================

CREATE INDEX idx_ppj_organization
ON platform_provisioning_jobs(organization_id);

CREATE INDEX idx_ppj_tenant_database
ON platform_provisioning_jobs(tenant_database_id);

CREATE INDEX idx_ppj_status
ON platform_provisioning_jobs(status);
