CREATE TABLE IF NOT EXISTS plm_role_assignment (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(128) NOT NULL,
    role_label VARCHAR(255) NOT NULL,
    assignee_user_id VARCHAR(64),
    assignee_display_name VARCHAR(255),
    assignment_scope VARCHAR(64) NOT NULL,
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_role_assignment_bill
    ON plm_role_assignment (business_type, bill_id, sort_order);

CREATE TABLE IF NOT EXISTS plm_domain_acl (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    domain_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    access_scope VARCHAR(64) NOT NULL,
    policy_source VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_domain_acl_bill
    ON plm_domain_acl (business_type, bill_id, domain_code, sort_order);

CREATE TABLE IF NOT EXISTS plm_external_integration_record (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64),
    system_code VARCHAR(64) NOT NULL,
    system_name VARCHAR(255) NOT NULL,
    direction_code VARCHAR(32) NOT NULL,
    integration_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    endpoint_key VARCHAR(128),
    external_ref VARCHAR(255),
    last_sync_at TIMESTAMP,
    message VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_external_integration_bill
    ON plm_external_integration_record (business_type, bill_id, system_code, sort_order);

CREATE TABLE IF NOT EXISTS plm_external_sync_event (
    id VARCHAR(64) PRIMARY KEY,
    integration_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json TEXT,
    error_message VARCHAR(500),
    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_external_sync_event_integration
    ON plm_external_sync_event (integration_id, sort_order, happened_at);
