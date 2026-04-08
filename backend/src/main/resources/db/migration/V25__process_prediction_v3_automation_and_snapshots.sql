INSERT INTO wf_notification_channel (
    id,
    channel_code,
    channel_type,
    channel_name,
    enabled,
    mock_mode,
    config_json,
    remark,
    created_at,
    updated_at
)
SELECT
    'nch_prediction_in_app_default',
    'in_app_default',
    'IN_APP',
    '流程预测站内通知',
    TRUE,
    FALSE,
    '{"senderUserId":"system"}',
    '流程预测自动动作默认站内通知渠道',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM wf_notification_channel WHERE channel_code = 'in_app_default'
);

CREATE TABLE IF NOT EXISTS wf_process_prediction_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    process_key VARCHAR(128) NOT NULL,
    current_node_id VARCHAR(128),
    business_type VARCHAR(64),
    assignee_user_id VARCHAR(64),
    organization_profile VARCHAR(255),
    working_day_profile VARCHAR(64),
    sample_profile VARCHAR(255),
    sample_tier VARCHAR(64),
    overdue_risk_level VARCHAR(32),
    confidence VARCHAR(32),
    historical_sample_size INTEGER NOT NULL DEFAULT 0,
    outlier_filtered_sample_size INTEGER NOT NULL DEFAULT 0,
    remaining_duration_minutes BIGINT,
    current_elapsed_minutes BIGINT,
    current_node_duration_p50_minutes BIGINT,
    current_node_duration_p75_minutes BIGINT,
    current_node_duration_p90_minutes BIGINT,
    predicted_finish_time TIMESTAMP,
    predicted_risk_threshold_time TIMESTAMP,
    feature_json TEXT NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prediction_snapshot_instance
    ON wf_process_prediction_snapshot (process_instance_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_prediction_snapshot_task
    ON wf_process_prediction_snapshot (task_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS wf_process_prediction_aggregate (
    id VARCHAR(64) PRIMARY KEY,
    stat_date DATE NOT NULL,
    process_key VARCHAR(128) NOT NULL,
    current_node_id VARCHAR(128),
    business_type VARCHAR(64),
    organization_profile VARCHAR(255),
    working_day_profile VARCHAR(64),
    sample_tier VARCHAR(64),
    overdue_risk_level VARCHAR(32),
    sample_count INTEGER NOT NULL DEFAULT 0,
    avg_remaining_duration_minutes BIGINT,
    avg_current_elapsed_minutes BIGINT,
    latest_p50_minutes BIGINT,
    latest_p75_minutes BIGINT,
    latest_p90_minutes BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_prediction_aggregate_bucket
    ON wf_process_prediction_aggregate (
        stat_date,
        process_key,
        current_node_id,
        business_type,
        organization_profile,
        working_day_profile,
        sample_tier,
        overdue_risk_level
    );
