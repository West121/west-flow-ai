ALTER TABLE plm_project
    ADD COLUMN IF NOT EXISTS initiation_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE plm_project
    ADD COLUMN IF NOT EXISTS initiation_scene_code VARCHAR(64) NOT NULL DEFAULT 'default';

ALTER TABLE plm_project
    ADD COLUMN IF NOT EXISTS initiation_process_instance_id VARCHAR(64);

ALTER TABLE plm_project
    ADD COLUMN IF NOT EXISTS initiation_submitted_at TIMESTAMP;

ALTER TABLE plm_project
    ADD COLUMN IF NOT EXISTS initiation_decided_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_plm_project_initiation_status
    ON plm_project (initiation_status);

CREATE INDEX IF NOT EXISTS idx_plm_project_initiation_process_instance
    ON plm_project (initiation_process_instance_id);

INSERT INTO wf_process_definition (
    id,
    process_key,
    process_name,
    category,
    version,
    status,
    dsl_json,
    bpmn_xml,
    publisher_user_id,
    deployment_id,
    flowable_definition_id,
    created_at,
    updated_at
)
SELECT
    'plm_project_initiation:1',
    'plm_project_initiation',
    'PLM 项目立项审批',
    'PLM',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"plm_project_initiation","processName":"PLM 项目立项审批","category":"PLM","processFormKey":"plm-project-initiation-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_sponsor","type":"approver","name":"项目赞助人审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"FORM_FIELD","userIds":[],"roleCodes":[],"departmentRef":"","formFieldKey":"sponsorUserId"},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"approve_change_manager","type":"approver","name":"PLM 变更经理审批","position":{"x":540,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":760,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_sponsor","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_sponsor","target":"approve_change_manager","priority":10,"label":"通过"},{"id":"edge_3","source":"approve_change_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="plm_project_initiation" name="PLM 项目立项审批" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_sponsor" name="项目赞助人审批" flowable:assignee="sponsorUserId"/><userTask id="approve_change_manager" name="PLM 变更经理审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_sponsor"/><sequenceFlow id="flow_2" sourceRef="approve_sponsor" targetRef="approve_change_manager"/><sequenceFlow id="flow_3" sourceRef="approve_change_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'plm_project_initiation:1');

INSERT INTO wf_business_process_binding (
    id,
    business_type,
    scene_code,
    process_key,
    enabled,
    created_at,
    updated_at
)
SELECT
    'bind_seed_plm_project_default',
    'PLM_PROJECT',
    'default',
    'plm_project_initiation',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_plm_project_default');
