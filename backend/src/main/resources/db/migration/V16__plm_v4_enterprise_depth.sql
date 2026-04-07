CREATE TABLE IF NOT EXISTS plm_object_master (
    id VARCHAR(64) PRIMARY KEY,
    object_type VARCHAR(64) NOT NULL,
    object_code VARCHAR(128) NOT NULL,
    object_name VARCHAR(255) NOT NULL,
    owner_user_id VARCHAR(64),
    domain_code VARCHAR(64) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL,
    source_system VARCHAR(64),
    external_ref VARCHAR(255),
    latest_revision VARCHAR(128),
    latest_version_label VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_object_master_domain_code
    ON plm_object_master (domain_code, object_type, object_code);

CREATE INDEX IF NOT EXISTS idx_plm_object_master_latest_revision
    ON plm_object_master (domain_code, latest_revision);

CREATE TABLE IF NOT EXISTS plm_object_revision (
    id VARCHAR(64) PRIMARY KEY,
    object_id VARCHAR(64) NOT NULL,
    revision_code VARCHAR(128) NOT NULL,
    version_label VARCHAR(128),
    version_status VARCHAR(32) NOT NULL,
    checksum VARCHAR(128),
    summary_json TEXT,
    snapshot_json TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_object_revision_object_revision
    ON plm_object_revision (object_id, revision_code);

CREATE INDEX IF NOT EXISTS idx_plm_object_revision_object
    ON plm_object_revision (object_id, created_at DESC);

CREATE TABLE IF NOT EXISTS plm_bill_object_link (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    object_revision_id VARCHAR(64),
    role_code VARCHAR(64),
    change_action VARCHAR(32),
    before_revision_code VARCHAR(128),
    after_revision_code VARCHAR(128),
    remark VARCHAR(1000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_bill_object_link_bill
    ON plm_bill_object_link (business_type, bill_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_plm_bill_object_link_object
    ON plm_bill_object_link (object_id);

CREATE TABLE IF NOT EXISTS plm_revision_diff (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    before_revision_id VARCHAR(64),
    after_revision_id VARCHAR(64),
    diff_kind VARCHAR(32) NOT NULL,
    diff_summary VARCHAR(1000),
    diff_payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_revision_diff_bill
    ON plm_revision_diff (business_type, bill_id, object_id, diff_kind);

CREATE TABLE IF NOT EXISTS plm_implementation_task (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    task_type VARCHAR(64),
    owner_user_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    planned_start_at TIMESTAMP,
    planned_end_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    result_summary VARCHAR(4000),
    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_implementation_task_bill_status
    ON plm_implementation_task (business_type, bill_id, status, sort_order);

CREATE INDEX IF NOT EXISTS idx_plm_implementation_task_owner_due
    ON plm_implementation_task (owner_user_id, planned_end_at);
