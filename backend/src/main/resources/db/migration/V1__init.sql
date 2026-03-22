CREATE TABLE IF NOT EXISTS wf_company (
    id VARCHAR(64) PRIMARY KEY,
    company_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_department (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    parent_department_id VARCHAR(64),
    department_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_menu (
    id VARCHAR(64) PRIMARY KEY,
    parent_menu_id VARCHAR(64),
    menu_name VARCHAR(128) NOT NULL,
    menu_type VARCHAR(32) NOT NULL,
    route_path VARCHAR(255),
    component_path VARCHAR(255),
    permission_code VARCHAR(255),
    icon_name VARCHAR(64),
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_role (
    id VARCHAR(64) PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(128) NOT NULL,
    role_category VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_user_post (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    post_id VARCHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_user_role (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_role_menu (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_role_data_scope (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_value VARCHAR(64) NOT NULL,
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

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workbench',
    NULL,
    '工作台',
    'DIRECTORY',
    '/workbench',
    NULL,
    NULL,
    'FolderKanban',
    10,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workbench');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workbench_dashboard',
    'menu_workbench',
    '平台总览',
    'MENU',
    '/',
    'dashboard/overview',
    'dashboard:overview:view',
    'FolderKanban',
    10,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workbench_dashboard');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workbench_todo',
    'menu_workbench',
    '待办列表',
    'MENU',
    '/workbench/todos/list',
    'workbench/todos/list',
    'todo:list:view',
    'ListTodo',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workbench_todo');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system',
    NULL,
    '系统管理',
    'DIRECTORY',
    '/system',
    NULL,
    NULL,
    'ShieldCheck',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_user',
    'menu_system',
    '用户管理',
    'MENU',
    '/system/users/list',
    'system/users/list',
    'system:user:view',
    'Users',
    10,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_user');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_company',
    'menu_system',
    '公司管理',
    'MENU',
    '/system/companies/list',
    'system/companies/list',
    'system:company:view',
    'Building2',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_company');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_role',
    'menu_system',
    '角色管理',
    'MENU',
    '/system/roles/list',
    'system/roles/list',
    'system:role:view',
    'ShieldCheck',
    30,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_role');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_department',
    'menu_system',
    '部门管理',
    'MENU',
    '/system/departments/list',
    'system/departments/list',
    'system:department:view',
    'Building2',
    40,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_department');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_post',
    'menu_system',
    '岗位管理',
    'MENU',
    '/system/posts/list',
    'system/posts/list',
    'system:post:view',
    'CircleUserRound',
    50,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_post');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_menu',
    'menu_system',
    '菜单管理',
    'MENU',
    '/system/menus/list',
    'system/menus/list',
    'system:menu:view',
    'SquareMenu',
    60,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_menu');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_system_permission_probe',
    'menu_system_menu',
    '权限探针',
    'BUTTON',
    NULL,
    NULL,
    'system:permission-probe',
    'ShieldCheck',
    999,
    FALSE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_permission_probe');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workflow',
    NULL,
    '流程平台',
    'DIRECTORY',
    '/workflow',
    NULL,
    NULL,
    'Network',
    30,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workflow_definition',
    'menu_workflow',
    '流程定义',
    'MENU',
    '/workflow/definitions/list',
    'workflow/definitions/list',
    'workflow:definition:view',
    'Network',
    10,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_definition');

INSERT INTO wf_menu (
    id,
    parent_menu_id,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon_name,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at
)
SELECT
    'menu_workflow_designer',
    'menu_workflow',
    '流程设计器',
    'MENU',
    '/workflow/designer',
    'workflow/designer',
    'workflow:designer:view',
    'FolderKanban',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_designer');

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
    'role_oa_user',
    'OA_USER',
    'OA 普通用户',
    'SYSTEM',
    '默认业务发起与待办处理角色',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_oa_user');

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
    'role_dept_manager',
    'DEPT_MANAGER',
    '部门经理',
    'SYSTEM',
    '具备部门与子部门数据权限的管理角色',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_dept_manager');

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
    'role_process_admin',
    'PROCESS_ADMIN',
    '流程管理员',
    'SYSTEM',
    '负责流程定义与发布管理',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_process_admin');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_001', 'role_oa_user', 'menu_workbench_dashboard', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_001');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_002', 'role_oa_user', 'menu_workbench_todo', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_002');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_003', 'role_dept_manager', 'menu_system_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_003');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_004', 'role_dept_manager', 'menu_system_permission_probe', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_004');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_005', 'role_process_admin', 'menu_workflow_definition', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_005');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_006', 'role_process_admin', 'menu_workflow_designer', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_006');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_001', 'role_oa_user', 'SELF', '*', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_001');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_002', 'role_dept_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_001', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_002');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_003', 'role_process_admin', 'COMPANY', 'cmp_001', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_003');

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

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_001', 'usr_001', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_001');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_002', 'usr_001', 'role_dept_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_002');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_003', 'usr_002', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_003');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_004', 'usr_003', 'role_process_admin', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_004');

INSERT INTO wf_delegation (id, principal_user_id, delegate_user_id, status, created_at)
SELECT 'dlg_001', 'usr_002', 'usr_001', 'ACTIVE', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_delegation WHERE id = 'dlg_001');

CREATE TABLE IF NOT EXISTS wf_business_process_binding (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    process_key VARCHAR(128) NOT NULL,
    process_definition_id VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_business_process_link (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(64) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(64) NOT NULL,
    start_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oa_leave_bill (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    days INTEGER NOT NULL,
    reason VARCHAR(512) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oa_expense_bill (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oa_common_request_bill (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO wf_business_process_binding (
    id,
    business_type,
    scene_code,
    process_key,
    process_definition_id,
    enabled,
    priority,
    created_at,
    updated_at
)
SELECT
    'bind_seed_leave_default',
    'OA_LEAVE',
    'default',
    'oa_leave',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_leave_default');

INSERT INTO wf_business_process_binding (
    id,
    business_type,
    scene_code,
    process_key,
    process_definition_id,
    enabled,
    priority,
    created_at,
    updated_at
)
SELECT
    'bind_seed_expense_default',
    'OA_EXPENSE',
    'default',
    'oa_expense',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_expense_default');

INSERT INTO wf_business_process_binding (
    id,
    business_type,
    scene_code,
    process_key,
    process_definition_id,
    enabled,
    priority,
    created_at,
    updated_at
)
SELECT
    'bind_seed_common_default',
    'OA_COMMON',
    'default',
    'oa_common',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_common_default');
