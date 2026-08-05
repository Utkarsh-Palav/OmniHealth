create TYPE organization_status AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED');

create table organizations (
                               id UUID PRIMARY KEY,

                               organization_code varchar(50) not null unique,

                               legal_name varchar(255) not null,

                               display_name varchar(255) not null,

                               email varchar(255) not null,

                               phone varchar(20),

                               website varchar(255),

                               logo_url varchar(500),

                               country_code varchar(2) not null,

                               timezone varchar(100) not null,

                               currency_code varchar(3) not null,

                               status organization_status NOT NULL default 'PENDING',

                               is_demo BOOLEAN NOT NULL DEFAULT FALSE,

                               created_at TIMESTAMPTZ NOT NULL,

                               updated_at TIMESTAMPTZ NOT NULL,

                               deleted_at TIMESTAMPTZ,

                               version BIGINT NOT NULL
);

create TYPE user_status AS ENUM (
    'PENDING',
    'ACTIVE',
    'LOCKED',
    'DISABLED'
    );

create TYPE auth_provider AS ENUM (
    'LOCAL',
    'GOOGLE',
    'MICROSOFT',
    'AZURE_AD'
    );

create table users (

                       id UUID PRIMARY KEY,

                       first_name varchar(100) not null,

                       last_name varchar(100),

                       email varchar(255) not null unique,

                       phone varchar(20),

                       avatar_url varchar(500),

                       password_hash text not null,

                       auth_provider auth_provider NOT NULL default 'LOCAL',

                       email_verified BOOLEAN NOT NULL default FALSE,

                       phone_verified BOOLEAN NOT NULL DEFAULT FALSE,

                       last_login_at TIMESTAMPTZ,

                       status user_status NOT NULL DEFAULT 'PENDING',

                       created_at TIMESTAMPTZ NOT NULL,

                       updated_at TIMESTAMPTZ NOT NULL,

                       deleted_at TIMESTAMPTZ,

                       version BIGINT NOT NULL
);

create TYPE membership_status AS ENUM (
    'INVITED',
    'ACTIVE',
    'SUSPENDED',
    'REMOVED'
    );

create table organization_memberships (

                                          id UUID PRIMARY KEY,

                                          organization_id UUID NOT NULL,

                                          user_id UUID NOT NULL,

                                          membership_status membership_status NOT NULL,

                                          is_owner boolean not null default FALSE,

                                          joined_at TIMESTAMPTZ,

                                          created_at TIMESTAMPTZ NOT NULL,

                                          updated_at TIMESTAMPTZ NOT NULL,

                                          deleted_at TIMESTAMPTZ,

                                          version bigint not null,

                                          constraint fk_membership_org
                                              foreign key (organization_id)
                                                  references organizations(id)
                                                  on delete restrict,

                                          constraint fk_membership_user
                                              foreign key (user_id)
                                                  references users(id)
                                                  on delete restrict,

                                          constraint uk_org_user
                                              unique (organization_id, user_id)
);

create TYPE invitation_status AS ENUM (
    'PENDING',
    'ACCEPTED',
    'DECLINED',
    'EXPIRED',
    'REVOKED'
    );

create table organization_invitations (

                                          id UUID PRIMARY KEY,

                                          organization_id UUID NOT NULL,

                                          invited_email varchar(255) not null,

                                          invited_by UUID NOT NULL,

                                          message text,

                                          status invitation_status NOT NULL default 'PENDING',

                                          invitation_token VARCHAR(255) NOT NULL UNIQUE,

                                          expires_at TIMESTAMPTZ NOT NULL,

                                          accepted_at TIMESTAMPTZ,

                                          created_at TIMESTAMPTZ NOT NULL,

                                          updated_at TIMESTAMPTZ NOT NULL,

                                          deleted_at TIMESTAMPTZ,

                                          version BIGINT NOT NULL,

                                          CONSTRAINT fk_invitation_org
                                              FOREIGN KEY (organization_id)
                                                  REFERENCES organizations(id)
                                                  ON delete RESTRICT,

                                          CONSTRAINT fk_invitation_user
                                              FOREIGN KEY (invited_by)
                                                  REFERENCES users(id)
                                                  ON delete RESTRICT
);

create table plans (

                       id UUID PRIMARY KEY,

                       plan_code varchar(30) not null unique,

                       name varchar(100) not null,

                       description text,

                       monthly_price numeric(12,2),

                       yearly_price numeric(12,2),

                       currency_code varchar(3) not null,

                       is_active boolean not null default TRUE,

                       created_at TIMESTAMPTZ NOT NULL,

                       updated_at TIMESTAMPTZ NOT NULL,

                       deleted_at TIMESTAMPTZ,

                       version bigint not null
);

create TYPE subscription_status AS ENUM (

    'TRIAL',

    'ACTIVE',

    'PAST_DUE',

    'CANCELLED',

    'EXPIRED'
    );

create table subscriptions (

                               id UUID PRIMARY KEY,

                               organization_id UUID NOT NULL,

                               plan_id UUID NOT NULL,

                               subscription_status subscription_status NOT NULL,

                               starts_at TIMESTAMPTZ NOT NULL,

                               ends_at TIMESTAMPTZ,

                               trial_ends_at TIMESTAMPTZ,

                               is_current boolean not null default TRUE,

                               created_at TIMESTAMPTZ NOT NULL,

                               updated_at TIMESTAMPTZ NOT NULL,

                               deleted_at TIMESTAMPTZ,

                               version bigint not null,

                               constraint fk_subscription_org
                                   foreign key (organization_id)
                                       references organizations(id)
                                       on delete restrict,

                               constraint fk_subscription_plan
                                   foreign key (plan_id)
                                       references plans(id)
                                       on delete restrict
);

create TYPE payment_status AS ENUM (
    'PENDING',
    'SUCCESS',
    'FAILED',
    'REFUNDED',
    'CANCELLED'
    );

create TYPE payment_provider AS ENUM (
    'RAZORPAY',
    'STRIPE',
    'PAYPAL',
    'MANUAL'
    );

create table payments (

                          id UUID PRIMARY KEY,

                          subscription_id UUID NOT NULL,

                          provider payment_provider NOT NULL,

                          transaction_reference varchar(255) not null,

                          order_reference varchar(255),

                          failure_reason text,

                          amount numeric(12,2) not null,

                          currency_code varchar(3) not null,

                          status payment_status NOT NULL,

                          payment_method varchar,

                          paid_at TIMESTAMPTZ,

                          created_at TIMESTAMPTZ NOT NULL,

                          updated_at TIMESTAMPTZ NOT NULL,

                          deleted_at TIMESTAMPTZ,

                          version bigint not null,

                          CONSTRAINT fk_payment_subscription
                              FOREIGN KEY (subscription_id)
                                  REFERENCES subscriptions(id)
                                  ON DELETE RESTRICT
);

create TYPE tenant_database_status AS ENUM (

    'PROVISIONING',

    'ACTIVE',

    'FAILED',

    'ARCHIVED'
    );

create TYPE Database_Type AS ENUM (
    'POSTGRESQL'
    );

create table tenant_databases (

                                  id UUID PRIMARY KEY,

                                  organization_id UUID NOT NULL UNIQUE,

                                  database_name varchar(100) not null unique,

                                  database_type Database_Type NOT NULL default 'POSTGRESQL',

                                  host VARCHAR(255) NOT NULL,

                                  port INTEGER NOT NULL,

                                  username VARCHAR(100) NOT NULL,

                                  encrypted_password TEXT NOT NULL,

                                  ssl_enabled BOOLEAN NOT NULL DEFAULT FALSE,

                                  schema_version VARCHAR(50) NOT NULL,

                                  status tenant_database_status NOT NULL,

                                  last_migrated_at TIMESTAMPTZ,

                                  created_at TIMESTAMPTZ NOT NULL,

                                  updated_at TIMESTAMPTZ NOT NULL,

                                  deleted_at TIMESTAMPTZ,

                                  version BIGINT NOT NULL,

                                  CONSTRAINT fk_tenant_database_org
                                      FOREIGN KEY (organization_id)
                                          REFERENCES organizations(id)
                                          ON DELETE RESTRICT
);

create INDEX idx_users_email
    ON users(email);

create INDEX idx_membership_user
    ON organization_memberships(user_id);

create INDEX idx_membership_org
    ON organization_memberships(organization_id);

create INDEX idx_invitation_email
    ON organization_invitations(invited_email);

create INDEX idx_subscription_org
    ON subscriptions(organization_id);

create INDEX idx_tenant_org
    ON tenant_databases(organization_id);

create INDEX idx_payment_subscription
    ON payments(subscription_id);

create INDEX idx_invitation_token
    ON organization_invitations(invitation_token);