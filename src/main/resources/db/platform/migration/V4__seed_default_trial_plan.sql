-- =====================================================
-- OmniHealth Platform Database
-- Version : V4 – Seed default self-service trial plan
-- PostgreSQL : 17
-- Flyway Migration
-- =====================================================

-- =====================================================
-- Seed the default trial plan referenced by every
-- self-service onboarding subscription. Idempotent:
-- re-running is a no-op thanks to the unique plan code.
-- =====================================================

INSERT INTO platform_plans (
    code,
    name,
    description,
    billing_cycle,
    price,
    currency_code,
    trial_days,
    is_public,
    is_active,
    sort_order
)
VALUES (
    'TRIAL',
    'Free Trial',
    'Default self-service trial plan',
    'MONTHLY',
    0.00,
    'INR',
    14,
    TRUE,
    TRUE,
    0
)
ON CONFLICT (code) DO NOTHING;
