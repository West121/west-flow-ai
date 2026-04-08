ALTER TABLE plm_implementation_task
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(64);

ALTER TABLE plm_implementation_task
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(128);

ALTER TABLE plm_implementation_task
    ADD COLUMN IF NOT EXISTS required_evidence_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS plm_implementation_template (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(128) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    default_task_title VARCHAR(255),
    default_owner_role_code VARCHAR(128),
    required_evidence_count INT NOT NULL DEFAULT 0,
    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_implementation_task_dependency (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    predecessor_task_id VARCHAR(64) NOT NULL,
    successor_task_id VARCHAR(64) NOT NULL,
    dependency_type VARCHAR(32) NOT NULL,
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_implementation_task_evidence (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    evidence_name VARCHAR(255) NOT NULL,
    evidence_ref VARCHAR(500),
    evidence_summary VARCHAR(1000),
    uploaded_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_acceptance_checklist (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    check_code VARCHAR(128) NOT NULL,
    check_name VARCHAR(255) NOT NULL,
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    result_summary VARCHAR(1000),
    checked_by VARCHAR(64),
    checked_at TIMESTAMP,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_ecr_plan', 'PLM_ECR', 'default', 'ECR_IMPL_PLAN', 'ECR 实施计划', 'IMPLEMENTATION', '工程变更实施',
       'PLM_CHANGE_MANAGER', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_ecr_plan');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_ecr_doc', 'PLM_ECR', 'default', 'ECR_DOC_RELEASE', 'ECR 文档发布', 'DOCUMENT', '图纸与文档发布',
       'PLM_DOC_CONTROLLER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_ecr_doc');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_ecr_quality', 'PLM_ECR', 'default', 'ECR_QUALITY_VERIFY', 'ECR 质量验证', 'VALIDATION', '质量验证与归档',
       'PLM_QUALITY_OWNER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_ecr_quality');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_eco_rollout', 'PLM_ECO', 'default', 'ECO_MANUFACTURING_ROLLOUT', 'ECO 生产切换', 'ROLLOUT', '生产切换与现场执行',
       'PLM_MANUFACTURING_OWNER', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_eco_rollout');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_eco_quality', 'PLM_ECO', 'default', 'ECO_QUALITY_VERIFY', 'ECO 质量确认', 'VALIDATION', '质量确认与放行',
       'PLM_QUALITY_OWNER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_eco_quality');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_eco_erp', 'PLM_ECO', 'default', 'ECO_ERP_SYNC', 'ECO ERP 同步', 'SYNC', 'ERP / MES 同步确认',
       'PLM_ERP_OWNER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_eco_erp');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_mat_data', 'PLM_MATERIAL', 'default', 'MAT_MASTER_UPDATE', '物料主数据更新', 'DATA_CHANGE', '主数据更新执行',
       'PLM_DATA_STEWARD', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_mat_data');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_mat_erp', 'PLM_MATERIAL', 'default', 'MAT_ERP_SYNC', '物料 ERP 同步', 'SYNC', 'ERP 编码与主数据同步',
       'PLM_ERP_OWNER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_mat_erp');

INSERT INTO plm_implementation_template (
    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_tpl_mat_confirm', 'PLM_MATERIAL', 'default', 'MAT_CHANGE_CONFIRM', '物料变更确认', 'CONFIRM', '变更确认与关闭准备',
       'PLM_CHANGE_MANAGER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template WHERE id = 'plm_tpl_mat_confirm');
