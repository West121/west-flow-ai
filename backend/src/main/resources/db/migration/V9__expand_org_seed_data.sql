INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_021',
    'cmp_001',
    'dept_002',
    '招聘配置组',
    TRUE,
    'dept_002',
    2,
    'dept_002/dept_021',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_021');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_022',
    'cmp_001',
    'dept_002',
    '员工关系组',
    TRUE,
    'dept_002',
    2,
    'dept_002/dept_022',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_022');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_0221',
    'cmp_001',
    'dept_022',
    '组织发展组',
    TRUE,
    'dept_002',
    3,
    'dept_002/dept_022/dept_0221',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_0221');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_031',
    'cmp_001',
    'dept_003',
    '流程平台组',
    TRUE,
    'dept_003',
    2,
    'dept_003/dept_031',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_031');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_0311',
    'cmp_001',
    'dept_031',
    '流程研发一组',
    TRUE,
    'dept_003',
    3,
    'dept_003/dept_031/dept_0311',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_0311');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_032',
    'cmp_001',
    'dept_003',
    'PLM产品组',
    TRUE,
    'dept_003',
    2,
    'dept_003/dept_032',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_032');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_004',
    'cmp_001',
    NULL,
    '运营中心',
    TRUE,
    'dept_004',
    1,
    'dept_004',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_004');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_041',
    'cmp_001',
    'dept_004',
    '华东运营部',
    TRUE,
    'dept_004',
    2,
    'dept_004/dept_041',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_041');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_0411',
    'cmp_001',
    'dept_041',
    '上海运营组',
    TRUE,
    'dept_004',
    3,
    'dept_004/dept_041/dept_0411',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_0411');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_042',
    'cmp_001',
    'dept_004',
    '华南运营部',
    TRUE,
    'dept_004',
    2,
    'dept_004/dept_042',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_042');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_005',
    'cmp_001',
    NULL,
    '制造中心',
    TRUE,
    'dept_005',
    1,
    'dept_005',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_005');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_051',
    'cmp_001',
    'dept_005',
    '工程技术部',
    TRUE,
    'dept_005',
    2,
    'dept_005/dept_051',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_051');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_0511',
    'cmp_001',
    'dept_051',
    '工艺组',
    TRUE,
    'dept_005',
    3,
    'dept_005/dept_051/dept_0511',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_0511');

INSERT INTO wf_department (
    id,
    company_id,
    parent_department_id,
    department_name,
    enabled,
    root_department_id,
    tree_level,
    tree_path,
    created_at,
    updated_at
)
SELECT
    'dept_052',
    'cmp_001',
    'dept_005',
    '质量管理部',
    TRUE,
    'dept_005',
    2,
    'dept_005/dept_052',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_department WHERE id = 'dept_052');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_021', 'dept_021', '招聘专员', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_021');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_022', 'dept_022', 'HRBP', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_022');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_0221', 'dept_0221', '组织发展经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_0221');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_031', 'dept_031', '流程平台主管', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_031');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_0311', 'dept_0311', '流程研发工程师', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_0311');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_032', 'dept_032', 'PLM产品经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_032');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_041', 'dept_041', '运营经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_041');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_0411', 'dept_0411', '运营专员', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_0411');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_042', 'dept_042', '区域运营经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_042');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_051', 'dept_051', '工程经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_051');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_0511', 'dept_0511', '工艺工程师', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_0511');

INSERT INTO wf_post (id, department_id, post_name, enabled, created_at, updated_at)
SELECT 'post_052', 'dept_052', '质量经理', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_post WHERE id = 'post_052');

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
    'role_hr_manager',
    'HR_MANAGER',
    '人力经理',
    'SYSTEM',
    '负责人力条线审批与部门数据查看',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_hr_manager');

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
    'role_finance_manager',
    'FINANCE_MANAGER',
    '财务经理',
    'SYSTEM',
    '负责财务条线审批与部门数据查看',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_finance_manager');

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
    'role_it_manager',
    'IT_MANAGER',
    '信息化经理',
    'SYSTEM',
    '负责流程平台与 PLM 信息化相关审批',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_it_manager');

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
    'role_operations_manager',
    'OPERATIONS_MANAGER',
    '运营经理',
    'SYSTEM',
    '负责运营条线审批与部门数据查看',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_operations_manager');

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
    'role_quality_manager',
    'QUALITY_MANAGER',
    '质量经理',
    'SYSTEM',
    '负责制造与质量条线审批与部门数据查看',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_quality_manager');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_005', 'role_hr_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_002', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_005');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_006', 'role_finance_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_001', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_006');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_007', 'role_it_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_003', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_007');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_008', 'role_operations_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_004', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_008');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_009', 'role_quality_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_005', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_009');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_004',
    'zhaoliu',
    '赵六',
    '13500000001',
    'zhaoliu@example.com',
    '',
    'cmp_001',
    'dept_022',
    'post_022',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_004');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_005',
    'sunqi',
    '孙七',
    '13500000002',
    'sunqi@example.com',
    '',
    'cmp_001',
    'dept_0221',
    'post_0221',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_005');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_006',
    'zhouba',
    '周八',
    '13500000003',
    'zhouba@example.com',
    '',
    'cmp_001',
    'dept_031',
    'post_031',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_006');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_007',
    'wujiu',
    '吴九',
    '13500000004',
    'wujiu@example.com',
    '',
    'cmp_001',
    'dept_0311',
    'post_0311',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_007');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_008',
    'zhengshi',
    '郑十',
    '13500000005',
    'zhengshi@example.com',
    '',
    'cmp_001',
    'dept_032',
    'post_032',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_008');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_009',
    'qianyi',
    '钱一',
    '13500000006',
    'qianyi@example.com',
    '',
    'cmp_001',
    'dept_041',
    'post_041',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_009');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_010',
    'liuer',
    '刘二',
    '13500000007',
    'liuer@example.com',
    '',
    'cmp_001',
    'dept_0411',
    'post_0411',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_010');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_011',
    'guosan',
    '郭三',
    '13500000008',
    'guosan@example.com',
    '',
    'cmp_001',
    'dept_051',
    'post_051',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_011');

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
    password_hash,
    enabled,
    login_enabled,
    failed_login_count,
    locked_until,
    last_login_at,
    password_updated_at,
    created_at,
    updated_at
)
SELECT
    'usr_012',
    'tianer',
    '田二',
    '13500000009',
    'tianer@example.com',
    '',
    'cmp_001',
    'dept_052',
    'post_052',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_012');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_006', 'usr_004', 'post_022', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_006');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_007', 'usr_005', 'post_0221', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_007');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_008', 'usr_006', 'post_031', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_008');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_009', 'usr_007', 'post_0311', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_009');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_010', 'usr_008', 'post_032', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_010');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_011', 'usr_009', 'post_041', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_011');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_012', 'usr_010', 'post_0411', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_012');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_013', 'usr_011', 'post_051', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_013');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_014', 'usr_012', 'post_052', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_014');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_007', 'usr_004', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_007');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_008', 'usr_004', 'role_hr_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_008');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_009', 'usr_005', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_009');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_010', 'usr_006', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_010');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_011', 'usr_006', 'role_it_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_011');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_012', 'usr_007', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_012');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_013', 'usr_008', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_013');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_014', 'usr_009', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_014');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_015', 'usr_009', 'role_operations_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_015');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_016', 'usr_010', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_016');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_017', 'usr_011', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_017');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_018', 'usr_011', 'role_finance_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_018');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_019', 'usr_012', 'role_oa_user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_019');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_020', 'usr_012', 'role_quality_manager', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_020');
