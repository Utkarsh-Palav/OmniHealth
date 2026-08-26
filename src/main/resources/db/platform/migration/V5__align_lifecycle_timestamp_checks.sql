-- =====================================================
-- OmniHealth Platform Database
-- Version : V5 – Align lifecycle-timestamp CHECKs to business anchors
-- PostgreSQL : 17
-- Flyway Migration
-- =====================================================
--
-- WHY THIS MIGRATION EXISTS
-- -------------------------------------------------
-- tenant_databases (V2) and platform_provisioning_jobs (V3) guard their
-- lifecycle-event columns with "<event>_at >= created_at" CHECKs. That guard
-- is unsatisfiable for rows inserted through the ORM:
--
--   * created_at is populated by JPA auditing (@CreatedDate) at persist() time.
--   * The service captures Instant.now() for the event columns (provisioned_at,
--     ready_at, completed_at, ...) a few instructions EARLIER, before save().
--
-- So created_at is always a hair AFTER the event timestamps, and
-- "<event>_at >= created_at" fails on essentially every insert (it only ever
-- "passed" for SQL-seeded rows, where a single statement's NOW() is stable).
--
-- The rest of this schema already avoids the trap by anchoring lifecycle CHECKs
-- to a BUSINESS column rather than the audit column, e.g.:
--   * platform_organization_onboarding: completed_at/failed_at >= started_at
--   * platform_subscriptions:          trial_ends_at >= trial_starts_at,
--                                      cancelled_at/ended_at >= starts_at
--
-- This migration makes the two outlier tables follow that same convention:
-- ready/failed are anchored to provisioned_at, and completed/failed to
-- started_at. The "provisioned_at >= created_at" guard is dropped outright
-- (provisioned_at is the first lifecycle event on the row, so it has no valid
-- business predecessor to compare against).
--
-- Purely additive: no data change, only constraint definitions are swapped.
-- =====================================================

-- -----------------------------------------------------
-- tenant_databases  (constraints originally defined in V2)
-- -----------------------------------------------------
ALTER TABLE tenant_databases
    DROP CONSTRAINT chk_tenant_databases_provisioned;

ALTER TABLE tenant_databases
    DROP CONSTRAINT chk_tenant_databases_ready;

ALTER TABLE tenant_databases
    DROP CONSTRAINT chk_tenant_databases_failed;

-- ready_at cannot precede provisioning
ALTER TABLE tenant_databases
    ADD CONSTRAINT chk_tenant_databases_ready
        CHECK (
            ready_at IS NULL
            OR provisioned_at IS NULL
            OR ready_at >= provisioned_at
        );

-- failure cannot precede provisioning
ALTER TABLE tenant_databases
    ADD CONSTRAINT chk_tenant_databases_failed
        CHECK (
            failed_at IS NULL
            OR provisioned_at IS NULL
            OR failed_at >= provisioned_at
        );

-- -----------------------------------------------------
-- platform_provisioning_jobs  (constraints originally defined in V3)
-- -----------------------------------------------------
ALTER TABLE platform_provisioning_jobs
    DROP CONSTRAINT chk_ppj_completed;

ALTER TABLE platform_provisioning_jobs
    DROP CONSTRAINT chk_ppj_failed;

-- completion cannot precede the job starting
ALTER TABLE platform_provisioning_jobs
    ADD CONSTRAINT chk_ppj_completed
        CHECK (
            completed_at IS NULL
            OR started_at IS NULL
            OR completed_at >= started_at
        );

-- failure cannot precede the job starting
ALTER TABLE platform_provisioning_jobs
    ADD CONSTRAINT chk_ppj_failed
        CHECK (
            failed_at IS NULL
            OR started_at IS NULL
            OR failed_at >= started_at
        );
