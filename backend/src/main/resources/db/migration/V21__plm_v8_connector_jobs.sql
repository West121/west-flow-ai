CREATE TABLE IF NOT EXISTS plm_connector_registry (
    id VARCHAR(64) PRIMARY KEY,
    connector_code VARCHAR(128) NOT NULL UNIQUE,
    system_code VARCHAR(64) NOT NULL,
    system_name VARCHAR(255) NOT NULL,
    direction_code VARCHAR(32) NOT NULL,
    handler_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    supports_retry BOOLEAN NOT NULL DEFAULT TRUE,
    supported_events_json TEXT,
    config_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_connector_registry_system
    ON plm_connector_registry (system_code, direction_code, enabled);

CREATE TABLE IF NOT EXISTS plm_connector_job (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    integration_id VARCHAR(64) NOT NULL,
    connector_registry_id VARCHAR(64) NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_payload_json TEXT,
    external_ref VARCHAR(255),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_run_at TIMESTAMP,
    last_dispatched_at TIMESTAMP,
    last_ack_at TIMESTAMP,
    last_error VARCHAR(500),
    created_by VARCHAR(64),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_connector_job_bill
    ON plm_connector_job (business_type, bill_id, sort_order, created_at);

CREATE INDEX IF NOT EXISTS idx_plm_connector_job_integration
    ON plm_connector_job (integration_id, status, next_run_at);

CREATE TABLE IF NOT EXISTS plm_connector_dispatch_log (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_payload_json TEXT,
    response_payload_json TEXT,
    error_message VARCHAR(500),
    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_connector_dispatch_log_job
    ON plm_connector_dispatch_log (job_id, sort_order, happened_at);

CREATE TABLE IF NOT EXISTS plm_external_ack (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    ack_status VARCHAR(32) NOT NULL,
    ack_code VARCHAR(64),
    external_ref VARCHAR(255),
    message VARCHAR(500),
    payload_json TEXT,
    source_system VARCHAR(64),
    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_external_ack_job
    ON plm_external_ack (job_id, sort_order, happened_at);

INSERT INTO plm_connector_registry (
    id, connector_code, system_code, system_name, direction_code, handler_key, enabled, supports_retry,
    supported_events_json, config_json, created_at, updated_at
)
SELECT
    'conn_plm_erp_sync',
    'PLM_ERP_SYNC',
    'ERP',
    'ERP 主数据',
    'DOWNSTREAM',
    'plm.connector.erp.stub',
    TRUE,
    TRUE,
    '["BILL_SUBMITTED","IMPLEMENTATION_STARTED","VALIDATION_SUBMITTED","BILL_CLOSED","BILL_CANCELLED"]',
    '{"transport":"stub","target":"ERP"}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_connector_registry WHERE id = 'conn_plm_erp_sync');

INSERT INTO plm_connector_registry (
    id, connector_code, system_code, system_name, direction_code, handler_key, enabled, supports_retry,
    supported_events_json, config_json, created_at, updated_at
)
SELECT
    'conn_plm_mes_sync',
    'PLM_MES_SYNC',
    'MES',
    'MES 制造执行',
    'DOWNSTREAM',
    'plm.connector.mes.stub',
    TRUE,
    TRUE,
    '["BILL_SUBMITTED","IMPLEMENTATION_STARTED","VALIDATION_SUBMITTED","BILL_CLOSED","BILL_CANCELLED"]',
    '{"transport":"stub","target":"MES"}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_connector_registry WHERE id = 'conn_plm_mes_sync');

INSERT INTO plm_connector_registry (
    id, connector_code, system_code, system_name, direction_code, handler_key, enabled, supports_retry,
    supported_events_json, config_json, created_at, updated_at
)
SELECT
    'conn_plm_pdm_release',
    'PLM_PDM_RELEASE',
    'PDM',
    'PDM 文档库',
    'DOWNSTREAM',
    'plm.connector.pdm.stub',
    TRUE,
    TRUE,
    '["BILL_SUBMITTED","IMPLEMENTATION_STARTED","VALIDATION_SUBMITTED","BILL_CLOSED","BILL_CANCELLED"]',
    '{"transport":"stub","target":"PDM"}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_connector_registry WHERE id = 'conn_plm_pdm_release');

INSERT INTO plm_connector_registry (
    id, connector_code, system_code, system_name, direction_code, handler_key, enabled, supports_retry,
    supported_events_json, config_json, created_at, updated_at
)
SELECT
    'conn_plm_cad_publish',
    'PLM_CAD_PUBLISH',
    'CAD',
    'CAD 图档中心',
    'DOWNSTREAM',
    'plm.connector.cad.stub',
    TRUE,
    TRUE,
    '["BILL_SUBMITTED","IMPLEMENTATION_STARTED","VALIDATION_SUBMITTED","BILL_CLOSED","BILL_CANCELLED"]',
    '{"transport":"stub","target":"CAD"}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_connector_registry WHERE id = 'conn_plm_cad_publish');
