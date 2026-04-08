CREATE TABLE IF NOT EXISTS plm_bom_node (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    parent_node_id VARCHAR(64),
    object_id VARCHAR(64),
    node_code VARCHAR(128) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    quantity DECIMAL(18,4),
    unit VARCHAR(32),
    effectivity VARCHAR(255),
    change_action VARCHAR(32),
    hierarchy_level INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_bom_node_bill
    ON plm_bom_node (business_type, bill_id, hierarchy_level, sort_order);

CREATE TABLE IF NOT EXISTS plm_document_asset (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64),
    document_code VARCHAR(128) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    version_label VARCHAR(128),
    vault_state VARCHAR(32) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(32),
    source_system VARCHAR(64),
    external_ref VARCHAR(255),
    change_action VARCHAR(32),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_document_asset_bill
    ON plm_document_asset (business_type, bill_id, document_type, sort_order);

CREATE TABLE IF NOT EXISTS plm_configuration_baseline (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    baseline_code VARCHAR(128) NOT NULL,
    baseline_name VARCHAR(255) NOT NULL,
    baseline_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    released_at TIMESTAMP,
    summary_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_configuration_baseline_bill_code
    ON plm_configuration_baseline (business_type, bill_id, baseline_code);

CREATE TABLE IF NOT EXISTS plm_configuration_baseline_item (
    id VARCHAR(64) PRIMARY KEY,
    baseline_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64),
    object_code VARCHAR(128) NOT NULL,
    object_name VARCHAR(255) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    before_revision_code VARCHAR(128),
    after_revision_code VARCHAR(128),
    effectivity VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_configuration_baseline_item_baseline
    ON plm_configuration_baseline_item (baseline_id, sort_order);

CREATE TABLE IF NOT EXISTS plm_object_acl (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    object_id VARCHAR(64),
    subject_type VARCHAR(32) NOT NULL,
    subject_code VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    access_scope VARCHAR(64) NOT NULL,
    inherited BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_object_acl_bill
    ON plm_object_acl (business_type, bill_id, subject_type, sort_order);
