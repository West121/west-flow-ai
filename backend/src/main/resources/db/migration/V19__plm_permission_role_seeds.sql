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
    'role_plm_change_manager',
    'PLM_CHANGE_MANAGER',
    'PLM 变更经理',
    'SYSTEM',
    '负责 PLM 变更单据的统筹管理、实施协调与权限治理',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_change_manager');

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
    'role_plm_domain_owner',
    'PLM_DOMAIN_OWNER',
    'PLM 领域负责人',
    'SYSTEM',
    '负责对应产品域下对象变更的阅读、实施与管理',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_domain_owner');

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
    'role_plm_doc_controller',
    'PLM_DOC_CONTROLLER',
    'PLM 文控负责人',
    'SYSTEM',
    '负责图纸与文档对象的发布、阅读与变更维护',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_doc_controller');

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
    'role_plm_quality_owner',
    'PLM_QUALITY_OWNER',
    'PLM 质量负责人',
    'SYSTEM',
    '负责 PLM 变更验证与关闭前质量确认',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_quality_owner');

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
    'role_plm_manufacturing_owner',
    'PLM_MANUFACTURING_OWNER',
    'PLM 制造负责人',
    'SYSTEM',
    '负责实施阶段制造侧任务与同步确认',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_manufacturing_owner');

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
    'role_plm_data_steward',
    'PLM_DATA_STEWARD',
    'PLM 主数据管理员',
    'SYSTEM',
    '负责物料与主数据变更的质量与字段治理',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_data_steward');

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
    'role_plm_erp_owner',
    'PLM_ERP_OWNER',
    'PLM ERP 接口负责人',
    'SYSTEM',
    '负责 ERP 同步、回写和主数据集成校验',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_plm_erp_owner');
