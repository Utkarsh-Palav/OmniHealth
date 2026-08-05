CREATE TABLE application_metadata (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(100) NOT NULL,
    application_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO application_metadata (
    application_name,
    application_version
)
VALUES (
    'OmniHealth',
    '0.0.1'
);