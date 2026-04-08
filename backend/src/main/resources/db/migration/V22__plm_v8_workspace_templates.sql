CREATE TABLE IF NOT EXISTS plm_implementation_template_dependency (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    predecessor_template_code VARCHAR(128) NOT NULL,
    successor_template_code VARCHAR(128) NOT NULL,
    dependency_type VARCHAR(32) NOT NULL DEFAULT 'FINISH_TO_START',
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_acceptance_checklist_template (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    check_code VARCHAR(128) NOT NULL,
    check_name VARCHAR(255) NOT NULL,
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_ecr_1', 'PLM_ECR', 'default', 'ECR_IMPL_PLAN', 'ECR_DOC_RELEASE', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_ecr_1');

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_ecr_2', 'PLM_ECR', 'default', 'ECR_DOC_RELEASE', 'ECR_QUALITY_VERIFY', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_ecr_2');

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_eco_1', 'PLM_ECO', 'default', 'ECO_MANUFACTURING_ROLLOUT', 'ECO_QUALITY_VERIFY', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_eco_1');

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_eco_2', 'PLM_ECO', 'default', 'ECO_QUALITY_VERIFY', 'ECO_ERP_SYNC', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_eco_2');

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_mat_1', 'PLM_MATERIAL', 'default', 'MAT_MASTER_UPDATE', 'MAT_ERP_SYNC', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_mat_1');

INSERT INTO plm_implementation_template_dependency (
    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_dep_mat_2', 'PLM_MATERIAL', 'default', 'MAT_ERP_SYNC', 'MAT_CHANGE_CONFIRM', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_implementation_template_dependency WHERE id = 'plm_dep_mat_2');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_ecr_scope', 'PLM_ECR', 'default', 'SCOPE_CONFIRMED', '实施范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_ecr_scope');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_ecr_evidence', 'PLM_ECR', 'default', 'EVIDENCE_ARCHIVED', '实施证据已归档', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_ecr_evidence');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_ecr_external', 'PLM_ECR', 'default', 'EXTERNAL_SYNC_VERIFIED', '外部系统同步结果已核对', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_ecr_external');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_eco_scope', 'PLM_ECO', 'default', 'ROLLOUT_CONFIRMED', '生产切换范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_eco_scope');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_eco_quality', 'PLM_ECO', 'default', 'QUALITY_ACCEPTED', '质量确认已完成', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_eco_quality');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_eco_sync', 'PLM_ECO', 'default', 'ERP_SYNC_ACCEPTED', 'ERP / MES 同步已确认', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_eco_sync');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_mat_scope', 'PLM_MATERIAL', 'default', 'MATERIAL_SCOPE_CONFIRMED', '物料变更范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_mat_scope');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_mat_sync', 'PLM_MATERIAL', 'default', 'MATERIAL_SYNC_ACCEPTED', '主数据同步结果已核对', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_mat_sync');

INSERT INTO plm_acceptance_checklist_template (
    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
)
SELECT 'plm_chk_mat_close', 'PLM_MATERIAL', 'default', 'MATERIAL_CLOSE_READY', '关闭条件已确认', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM plm_acceptance_checklist_template WHERE id = 'plm_chk_mat_close');
