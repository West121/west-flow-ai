CREATE TABLE IF NOT EXISTS wf_company (
    id VARCHAR(64) PRIMARY KEY,
    company_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_department (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    department_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    avatar VARCHAR(255) NOT NULL DEFAULT '',
    company_id VARCHAR(64) NOT NULL,
    active_department_id VARCHAR(64) NOT NULL,
    active_post_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_post (
    id VARCHAR(64) PRIMARY KEY,
    department_id VARCHAR(64) NOT NULL,
    post_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_user_post (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    post_id VARCHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_delegation (
    id VARCHAR(64) PRIMARY KEY,
    principal_user_id VARCHAR(64) NOT NULL,
    delegate_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_request_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    path VARCHAR(255) NOT NULL,
    method VARCHAR(16) NOT NULL,
    login_id VARCHAR(64),
    status_code INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_process_definition (
    id VARCHAR(64) PRIMARY KEY,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(255) NOT NULL,
    category VARCHAR(128),
    version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    dsl_json TEXT NOT NULL,
    bpmn_xml TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (process_key, version)
);

INSERT INTO wf_company (id, company_name, created_at)
SELECT 'cmp_001', '西流科技', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_company WHERE id = 'cmp_001');

INSERT INTO wf_department (id, company_id, department_name, created_at)
SELECT 'dept_001', 'cmp_001', '财务部', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_001');

INSERT INTO wf_department (id, company_id, department_name, created_at)
SELECT 'dept_002', 'cmp_001', '人力资源部', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_002');

INSERT INTO wf_department (id, company_id, department_name, created_at)
SELECT 'dept_003', 'cmp_001', '信息管理部', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_003');

INSERT INTO wf_post (id, department_id, post_name, created_at)
SELECT 'post_001', 'dept_001', '报销审核岗', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_001');

INSERT INTO wf_post (id, department_id, post_name, created_at)
SELECT 'post_002', 'dept_002', '请假复核岗', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_002');

INSERT INTO wf_post (id, department_id, post_name, created_at)
SELECT 'post_003', 'dept_003', '流程管理员', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_003');

INSERT INTO wf_user (
    id,
    username,
    display_name,
    mobile,
    email,
    avatar,
    company_id,
    active_department_id,
    active_post_id,
    enabled,
    created_at,
    updated_at
)
SELECT
    'usr_001',
    'zhangsan',
    '张三',
    '13800000000',
    'zhangsan@example.com',
    '',
    'cmp_001',
    'dept_001',
    'post_001',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_001');

INSERT INTO wf_user (
    id,
    username,
    display_name,
    mobile,
    email,
    avatar,
    company_id,
    active_department_id,
    active_post_id,
    enabled,
    created_at,
    updated_at
)
SELECT
    'usr_002',
    'lisi',
    '李四',
    '13900000000',
    'lisi@example.com',
    '',
    'cmp_001',
    'dept_002',
    'post_002',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_002');

INSERT INTO wf_user (
    id,
    username,
    display_name,
    mobile,
    email,
    avatar,
    company_id,
    active_department_id,
    active_post_id,
    enabled,
    created_at,
    updated_at
)
SELECT
    'usr_003',
    'wangwu',
    '王五',
    '13700000000',
    'wangwu@example.com',
    '',
    'cmp_001',
    'dept_003',
    'post_003',
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_003');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_001', 'usr_001', 'post_001', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_001');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_002', 'usr_002', 'post_002', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_002');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_003', 'usr_003', 'post_003', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_003');

INSERT INTO wf_delegation (id, principal_user_id, delegate_user_id, status, created_at)
SELECT 'dlg_001', 'usr_002', 'usr_001', 'ACTIVE', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_delegation WHERE id = 'dlg_001');
