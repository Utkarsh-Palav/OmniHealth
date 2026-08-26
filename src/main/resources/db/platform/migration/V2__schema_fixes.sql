-- =====================================================
-- OmniHealth Platform Database
-- Version : V2 – Schema Fixes
-- PostgreSQL : 17
-- Flyway Migration
-- =====================================================

-- =====================================================
-- FIX 1 : is_primary unique index on WRONG column
-- -------------------------------------------------
-- The V1 index enforced that a USER can be primary in
-- at most one org. The correct constraint is that each
-- ORGANIZATION can have at most one primary member
-- (i.e. one owner).
-- =====================================================

DROP INDEX IF EXISTS uq_platform_org_memberships_primary;

CREATE UNIQUE INDEX uq_platform_org_memberships_primary
ON platform_organization_memberships(organization_id)
WHERE is_primary = TRUE
  AND deleted_at IS NULL
  AND left_at IS NULL;

-- =====================================================
-- FIX 2 : Missing initiated_by on onboarding
-- =====================================================

ALTER TABLE platform_organization_onboarding
    ADD COLUMN initiated_by_user_id UUID;

ALTER TABLE platform_organization_onboarding
    ADD CONSTRAINT fk_platform_org_onboarding_initiated_by
        FOREIGN KEY (initiated_by_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_platform_org_onboarding_initiated_by
ON platform_organization_onboarding(initiated_by_user_id);

COMMENT ON COLUMN platform_organization_onboarding.initiated_by_user_id IS
'Platform user who initiated the organization onboarding process.';

-- =====================================================
-- FIX 3 : Missing created_by / cancelled_by on
--          subscriptions
-- =====================================================

ALTER TABLE platform_subscriptions
    ADD COLUMN created_by_user_id UUID,
    ADD COLUMN cancelled_by_user_id UUID;

ALTER TABLE platform_subscriptions
    ADD CONSTRAINT fk_platform_subscriptions_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT;

ALTER TABLE platform_subscriptions
    ADD CONSTRAINT fk_platform_subscriptions_cancelled_by
        FOREIGN KEY (cancelled_by_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_platform_subscriptions_created_by
ON platform_subscriptions(created_by_user_id);

CREATE INDEX idx_platform_subscriptions_cancelled_by
ON platform_subscriptions(cancelled_by_user_id);

COMMENT ON COLUMN platform_subscriptions.created_by_user_id IS
'Platform user who created or initiated the subscription.';

COMMENT ON COLUMN platform_subscriptions.cancelled_by_user_id IS
'Platform user who cancelled the subscription, when applicable.';

-- =====================================================
-- FIX 4 : Missing platform_audit_log table
-- -------------------------------------------------
-- The platform_audit_action_type enum was defined in
-- V1 but never used. This table captures platform-
-- level audit events.
-- =====================================================

CREATE TABLE platform_audit_log (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    actor_user_id UUID,

    action platform_audit_action_type NOT NULL,

    resource_type VARCHAR(100) NOT NULL,

    resource_id UUID,

    organization_id UUID,

    description TEXT,

    ip_address INET,

    user_agent TEXT,

    old_value JSONB,

    new_value JSONB,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Foreign Keys (nullable – system actions may not have an actor)
    CONSTRAINT fk_platform_audit_log_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_audit_log_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE platform_audit_log IS
'Immutable audit trail for platform-level events. Rows should never be updated or deleted.';

COMMENT ON COLUMN platform_audit_log.actor_user_id IS
'Platform user who performed the action. NULL for system-initiated events.';

COMMENT ON COLUMN platform_audit_log.action IS
'Type of action performed (CREATE, UPDATE, DELETE, LOGIN, etc.).';

COMMENT ON COLUMN platform_audit_log.resource_type IS
'Type of resource affected, e.g. ORGANIZATION, USER, SUBSCRIPTION.';

COMMENT ON COLUMN platform_audit_log.resource_id IS
'UUID of the affected resource, when applicable.';

COMMENT ON COLUMN platform_audit_log.old_value IS
'Snapshot of the resource state before the change, when applicable.';

COMMENT ON COLUMN platform_audit_log.new_value IS
'Snapshot of the resource state after the change, when applicable.';

-- =====================================================
-- Indexes : platform_audit_log
-- =====================================================

CREATE INDEX idx_platform_audit_log_actor
ON platform_audit_log(actor_user_id);

CREATE INDEX idx_platform_audit_log_action
ON platform_audit_log(action);

CREATE INDEX idx_platform_audit_log_resource_type
ON platform_audit_log(resource_type);

CREATE INDEX idx_platform_audit_log_resource
ON platform_audit_log(resource_type, resource_id);

CREATE INDEX idx_platform_audit_log_organization
ON platform_audit_log(organization_id);

CREATE INDEX idx_platform_audit_log_created_at
ON platform_audit_log(created_at);

-- =====================================================
-- FIX 5 : Missing feature & plan-feature tables
-- -------------------------------------------------
-- The platform_feature_value_type enum was defined in
-- V1 but never used. These tables allow plans to
-- declare feature limits (e.g. max_users = 5).
-- =====================================================

CREATE TABLE platform_features (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    code VARCHAR(100) NOT NULL,

    name VARCHAR(150) NOT NULL,

    description VARCHAR(500),

    value_type platform_feature_value_type NOT NULL,

    default_value VARCHAR(255),

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    sort_order INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_features_code
        UNIQUE (code),

    CONSTRAINT chk_platform_features_sort_order
        CHECK (sort_order >= 0)
);

COMMENT ON TABLE platform_features IS
'Defines configurable features that can be attached to platform plans to enforce usage limits or enable/disable capabilities.';

COMMENT ON COLUMN platform_features.code IS
'Stable machine-readable identifier for the feature, e.g. MAX_USERS, SMS_ENABLED.';

COMMENT ON COLUMN platform_features.value_type IS
'Data type of the feature value (BOOLEAN, INTEGER, DECIMAL, STRING).';

COMMENT ON COLUMN platform_features.default_value IS
'Default value for the feature when not overridden by a plan.';

-- =====================================================
-- Indexes : platform_features
-- =====================================================

CREATE INDEX idx_platform_features_active
ON platform_features(is_active);

CREATE INDEX idx_platform_features_sort_order
ON platform_features(sort_order);

-- =====================================================
-- Table : platform_plan_features
-- Purpose: Associates features with plans and defines
--          the feature value for each plan.
-- =====================================================

CREATE TABLE platform_plan_features (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    plan_id UUID NOT NULL,

    feature_id UUID NOT NULL,

    value VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_platform_plan_features_plan
        FOREIGN KEY (plan_id)
        REFERENCES platform_plans(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_plan_features_feature
        FOREIGN KEY (feature_id)
        REFERENCES platform_features(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_platform_plan_features
        UNIQUE (plan_id, feature_id)
);

COMMENT ON TABLE platform_plan_features IS
'Defines the value of each feature for a specific plan. For example, plan BASIC may have MAX_USERS = 5.';

COMMENT ON COLUMN platform_plan_features.value IS
'The feature value for this plan, interpreted according to the feature value_type.';

-- =====================================================
-- Indexes : platform_plan_features
-- =====================================================

CREATE INDEX idx_platform_plan_features_plan
ON platform_plan_features(plan_id);

CREATE INDEX idx_platform_plan_features_feature
ON platform_plan_features(feature_id);

CREATE INDEX idx_platform_plan_features_active
ON platform_plan_features(plan_id, feature_id)
WHERE deleted_at IS NULL;

-- =====================================================
-- FIX 6 : Missing tenant_databases table
-- -------------------------------------------------
-- The tenant_database_status enum was defined in V1
-- but never used. This table tracks provisioned
-- tenant database connections per organization.
-- =====================================================

CREATE TABLE tenant_databases (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    status tenant_database_status NOT NULL DEFAULT 'PENDING',

    database_name VARCHAR(100) NOT NULL,

    host VARCHAR(255) NOT NULL,

    port INTEGER NOT NULL DEFAULT 5432,

    schema_name VARCHAR(100) NOT NULL DEFAULT 'public',

    connection_pool_size INTEGER NOT NULL DEFAULT 10,

    provisioned_at TIMESTAMPTZ,

    ready_at TIMESTAMPTZ,

    failed_at TIMESTAMPTZ,

    failure_reason TEXT,

    archived_at TIMESTAMPTZ,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign Key
    CONSTRAINT fk_tenant_databases_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    -- One active database per organization
    CONSTRAINT chk_tenant_databases_port
        CHECK (port > 0 AND port <= 65535),

    CONSTRAINT chk_tenant_databases_pool_size
        CHECK (connection_pool_size > 0),

    CONSTRAINT chk_tenant_databases_provisioned
        CHECK (
            provisioned_at IS NULL
            OR provisioned_at >= created_at
        ),

    CONSTRAINT chk_tenant_databases_ready
        CHECK (
            ready_at IS NULL
            OR ready_at >= created_at
        ),

    CONSTRAINT chk_tenant_databases_failed
        CHECK (
            failed_at IS NULL
            OR failed_at >= created_at
        )
);

COMMENT ON TABLE tenant_databases IS
'Tracks provisioned tenant databases for each organization. Each organization gets an isolated database for its operational healthcare data.';

COMMENT ON COLUMN tenant_databases.database_name IS
'PostgreSQL database name provisioned for the tenant.';

COMMENT ON COLUMN tenant_databases.host IS
'Database server hostname or IP address.';

COMMENT ON COLUMN tenant_databases.schema_name IS
'Primary schema within the tenant database.';

COMMENT ON COLUMN tenant_databases.connection_pool_size IS
'Maximum number of connections in the connection pool for this tenant.';

COMMENT ON COLUMN tenant_databases.failure_reason IS
'Reason recorded when database provisioning fails.';

-- =====================================================
-- Indexes : tenant_databases
-- =====================================================

CREATE INDEX idx_tenant_databases_organization
ON tenant_databases(organization_id);

CREATE INDEX idx_tenant_databases_status
ON tenant_databases(status);

CREATE INDEX idx_tenant_databases_database_name
ON tenant_databases(database_name);

-- One active (non-archived) database per organization
CREATE UNIQUE INDEX uq_tenant_databases_active_org
ON tenant_databases(organization_id)
WHERE status IN ('PENDING', 'PROVISIONING', 'READY')
  AND deleted_at IS NULL;
