CREATE TABLE IF NOT EXISTS plm_project (
    id VARCHAR(64) PRIMARY KEY,
    project_no VARCHAR(64) NOT NULL,
    project_code VARCHAR(128) NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    project_type VARCHAR(64) NOT NULL,
    project_level VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    phase_code VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64),
    sponsor_user_id VARCHAR(64),
    domain_code VARCHAR(64),
    priority_level VARCHAR(32),
    target_release VARCHAR(128),
    start_date DATE,
    target_end_date DATE,
    actual_end_date DATE,
    summary TEXT,
    business_goal TEXT,
    risk_summary TEXT,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_project_no
    ON plm_project (project_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_project_code
    ON plm_project (project_code);

CREATE INDEX IF NOT EXISTS idx_plm_project_owner
    ON plm_project (owner_user_id);

CREATE INDEX IF NOT EXISTS idx_plm_project_phase
    ON plm_project (phase_code);

CREATE INDEX IF NOT EXISTS idx_plm_project_status
    ON plm_project (status);

CREATE TABLE IF NOT EXISTS plm_project_member (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_label VARCHAR(128) NOT NULL,
    responsibility_summary TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_project_member_project
    ON plm_project_member (project_id);

CREATE INDEX IF NOT EXISTS idx_plm_project_member_user
    ON plm_project_member (user_id);

CREATE TABLE IF NOT EXISTS plm_project_milestone (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    milestone_code VARCHAR(128) NOT NULL,
    milestone_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_user_id VARCHAR(64),
    planned_at TIMESTAMP,
    actual_at TIMESTAMP,
    summary TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_project_milestone_project
    ON plm_project_milestone (project_id);

CREATE INDEX IF NOT EXISTS idx_plm_project_milestone_status
    ON plm_project_milestone (status);

CREATE TABLE IF NOT EXISTS plm_project_link (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    link_type VARCHAR(64) NOT NULL,
    target_business_type VARCHAR(32),
    target_id VARCHAR(64) NOT NULL,
    target_no VARCHAR(128),
    target_title VARCHAR(255),
    target_status VARCHAR(64),
    target_href VARCHAR(500),
    summary TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_project_link_project
    ON plm_project_link (project_id);

CREATE INDEX IF NOT EXISTS idx_plm_project_link_target
    ON plm_project_link (target_id);

CREATE TABLE IF NOT EXISTS plm_project_stage_event (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    from_phase_code VARCHAR(64),
    to_phase_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    comment TEXT,
    changed_by VARCHAR(64) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_project_stage_event_project
    ON plm_project_stage_event (project_id, changed_at DESC);
