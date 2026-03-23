CREATE TABLE IF NOT EXISTS wf_process_termination_audit (
    id VARCHAR(64) PRIMARY KEY,
    root_instance_id VARCHAR(64) NOT NULL,
    target_instance_id VARCHAR(64) NOT NULL,
    parent_instance_id VARCHAR(64),
    target_kind VARCHAR(32) NOT NULL,
    terminate_scope VARCHAR(32) NOT NULL,
    propagation_policy VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_user_id VARCHAR(64) NOT NULL,
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_process_termination_audit_root_instance
    ON wf_process_termination_audit (root_instance_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_wf_process_termination_audit_target_instance
    ON wf_process_termination_audit (target_instance_id, created_at DESC, id DESC);
