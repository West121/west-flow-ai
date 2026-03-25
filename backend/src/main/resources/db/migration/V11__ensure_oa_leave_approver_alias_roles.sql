INSERT INTO wf_role (
    id,
    role_code,
    role_name,
    role_category,
    description,
    enabled,
    created_at,
    updated_at
)
SELECT
    'role_manager',
    'MANAGER',
    '部门经理（请假流程兼容）',
    'SYSTEM',
    '兼容请假流程草稿与已发布流程中的 role_manager 审批角色',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_manager');

INSERT INTO wf_role (
    id,
    role_code,
    role_name,
    role_category,
    description,
    enabled,
    created_at,
    updated_at
)
SELECT
    'role_hr',
    'HR',
    'HR 专员（请假流程兼容）',
    'SYSTEM',
    '兼容请假流程草稿与已发布流程中的 role_hr 审批角色',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_hr');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_compat_role_manager', 'usr_001', 'role_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_compat_role_manager');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_compat_role_hr', 'usr_004', 'role_hr', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_compat_role_hr');
