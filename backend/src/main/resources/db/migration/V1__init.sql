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
    password_hash VARCHAR(255) NOT NULL DEFAULT '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    login_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    password_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS wf_user_ai_capability (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    capability_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, capability_code)
);

CREATE TABLE IF NOT EXISTS wf_ai_conversation (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    preview VARCHAR(2000),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    context_tags_json TEXT NOT NULL DEFAULT '[]',
    message_count INTEGER NOT NULL DEFAULT 0,
    operator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_message (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    author_name VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    blocks_json TEXT NOT NULL DEFAULT '[]',
    operator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_tool_call (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    tool_key VARCHAR(128) NOT NULL,
    tool_type VARCHAR(32) NOT NULL,
    tool_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    arguments_json TEXT NOT NULL DEFAULT '{}',
    result_json TEXT NOT NULL DEFAULT '{}',
    summary VARCHAR(2000),
    confirmation_id VARCHAR(64),
    operator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_confirmation (
    id VARCHAR(64) PRIMARY KEY,
    tool_call_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    comment VARCHAR(2000),
    resolved_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_audit (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64),
    tool_call_id VARCHAR(64),
    action_type VARCHAR(64) NOT NULL,
    summary VARCHAR(2000),
    operator_user_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_agent_registry (
    id VARCHAR(64) PRIMARY KEY,
    agent_code VARCHAR(128) NOT NULL,
    agent_name VARCHAR(255) NOT NULL,
    capability_code VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system_prompt TEXT NOT NULL DEFAULT '',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_code)
);

CREATE TABLE IF NOT EXISTS wf_ai_tool_registry (
    id VARCHAR(64) PRIMARY KEY,
    tool_code VARCHAR(128) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    tool_category VARCHAR(32) NOT NULL,
    action_mode VARCHAR(16) NOT NULL,
    required_capability_code VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tool_code)
);

CREATE TABLE IF NOT EXISTS wf_ai_mcp_registry (
    id VARCHAR(64) PRIMARY KEY,
    mcp_code VARCHAR(128) NOT NULL,
    mcp_name VARCHAR(255) NOT NULL,
    endpoint_url VARCHAR(512),
    transport_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    required_capability_code VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (mcp_code)
);

CREATE TABLE IF NOT EXISTS wf_ai_skill_registry (
    id VARCHAR(64) PRIMARY KEY,
    skill_code VARCHAR(128) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    skill_path VARCHAR(512),
    required_capability_code VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_code)
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
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (principal_user_id, delegate_user_id)
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

CREATE TABLE IF NOT EXISTS wf_dict_type (
    id VARCHAR(64) PRIMARY KEY,
    type_code VARCHAR(128) NOT NULL UNIQUE,
    type_name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_dict_item (
    id VARCHAR(64) PRIMARY KEY,
    dict_type_id VARCHAR(64) NOT NULL,
    item_code VARCHAR(128) NOT NULL,
    item_label VARCHAR(255) NOT NULL,
    item_value VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 10,
    remark VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_system_message (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_user_ids_json TEXT,
    target_department_ids_json TEXT,
    sender_user_id VARCHAR(64) NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_system_message_read (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_file (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    bucket_name VARCHAR(128) NOT NULL,
    object_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    remark VARCHAR(500),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_notification_template (
    id VARCHAR(64) PRIMARY KEY,
    template_code VARCHAR(128) NOT NULL UNIQUE,
    template_name VARCHAR(255) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    title_template VARCHAR(255) NOT NULL,
    content_template TEXT NOT NULL,
    remark VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    publisher_user_id VARCHAR(64),
    deployment_id VARCHAR(64),
    flowable_definition_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (process_key, version)
);

CREATE TABLE IF NOT EXISTS wf_task_group (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    approval_mode VARCHAR(32) NOT NULL,
    reapprove_policy VARCHAR(32),
    group_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_task_group_member (
    id VARCHAR(64) PRIMARY KEY,
    task_group_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(64),
    assignee_user_id VARCHAR(64) NOT NULL,
    sequence_no INTEGER NOT NULL,
    vote_weight INTEGER,
    member_status VARCHAR(32) NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_task_vote_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    threshold_percent INTEGER NOT NULL,
    total_weight INTEGER NOT NULL,
    approved_weight INTEGER NOT NULL DEFAULT 0,
    rejected_weight INTEGER NOT NULL DEFAULT 0,
    decision_status VARCHAR(32),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (process_instance_id, node_id)
);

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'oa_leave:1',
    'oa_leave',
    '请假审批',
    'OA',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"oa_leave","processName":"请假审批","category":"OA","processFormKey":"oa-leave-start-form","processFormVersion":"1.1.0","formFields":[{"fieldKey":"leaveType","label":"请假类型","valueType":"STRING","required":true},{"fieldKey":"leaveDays","label":"请假天数","valueType":"NUMBER","required":true},{"fieldKey":"urgent","label":"是否紧急","valueType":"BOOLEAN","required":false},{"fieldKey":"managerUserId","label":"直属负责人","valueType":"USER","required":false}],"settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"condition_leave_split","type":"condition","name":"请假天数分支","position":{"x":320,"y":100},"config":{"defaultEdgeId":"edge_leave_short"},"ui":{"width":240,"height":88}},{"id":"approve_manager_role","type":"approver","name":"部门经理审批","position":{"x":560,"y":40},"config":{"assignment":{"mode":"ROLE","userIds":[],"roleCodes":["role_manager"],"departmentRef":"","formFieldKey":"","formulaExpression":""},"approvalMode":"SINGLE","approvalPolicy":{"type":"SINGLE","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"approve_dept_lead","type":"approver","name":"部门负责人审批","position":{"x":560,"y":140},"config":{"assignment":{"mode":"DEPARTMENT","userIds":[],"roleCodes":[],"departmentRef":"dept_002","formFieldKey":"","formulaExpression":""},"approvalMode":"SINGLE","approvalPolicy":{"type":"SINGLE","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"approve_hr_specialist","type":"approver","name":"HR 专员审批","position":{"x":560,"y":240},"config":{"assignment":{"mode":"ROLE","userIds":[],"roleCodes":["role_hr"],"departmentRef":"","formFieldKey":"","formulaExpression":""},"approvalMode":"SINGLE","approvalPolicy":{"type":"SINGLE","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"approve_manager_field","type":"approver","name":"负责人确认","position":{"x":800,"y":90},"config":{"assignment":{"mode":"FORM_FIELD","userIds":[],"roleCodes":[],"departmentRef":"","formFieldKey":"managerUserId","formulaExpression":""},"approvalMode":"SINGLE","approvalPolicy":{"type":"SINGLE","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"approve_director_formula","type":"approver","name":"总监确认","position":{"x":800,"y":210},"config":{"assignment":{"mode":"FORMULA","userIds":[],"roleCodes":[],"departmentRef":"","formFieldKey":"","formulaExpression":"leaveDays >= 5 ? ''usr_005'' : (managerUserId != null ? managerUserId : ''usr_002'')"},"approvalMode":"SINGLE","approvalPolicy":{"type":"SINGLE","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":1060,"y":150},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"condition_leave_split","priority":10,"label":"提交"},{"id":"edge_leave_short","source":"condition_leave_split","target":"approve_manager_role","priority":10,"label":"短假"},{"id":"edge_leave_long","source":"condition_leave_split","target":"approve_dept_lead","priority":20,"label":"长假","condition":{"type":"FIELD","fieldKey":"leaveDays","operator":"GT","value":3}},{"id":"edge_leave_urgent","source":"condition_leave_split","target":"approve_hr_specialist","priority":30,"label":"紧急假","condition":{"type":"FORMULA","formulaExpression":"urgent == true || leaveDays >= 5"}},{"id":"edge_2","source":"approve_manager_role","target":"approve_manager_field","priority":10,"label":"直属确认"},{"id":"edge_3","source":"approve_dept_lead","target":"approve_manager_field","priority":10,"label":"直属确认"},{"id":"edge_4","source":"approve_hr_specialist","target":"approve_director_formula","priority":10,"label":"总监确认"},{"id":"edge_5","source":"approve_manager_field","target":"approve_director_formula","priority":10,"label":"进入总监确认"},{"id":"edge_6","source":"approve_director_formula","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:westflow="https://westflow.dev/schema/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="oa_leave" name="请假审批" isExecutable="true"><startEvent id="start_1" name="开始"/><exclusiveGateway id="condition_leave_split" name="请假天数分支" default="edge_leave_short"/><userTask id="approve_manager_role" name="部门经理审批" flowable:candidateGroups="role_manager"/><userTask id="approve_dept_lead" name="部门负责人审批" flowable:candidateGroups="dept_002"/><userTask id="approve_hr_specialist" name="HR 专员审批" flowable:candidateGroups="role_hr"/><userTask id="approve_manager_field" name="负责人确认" flowable:assignee="managerUserId"/><userTask id="approve_director_formula" name="总监确认" flowable:assignee="' || '$' || '{leaveDays >= 5 ? ''usr_005'' : (managerUserId != null ? managerUserId : ''usr_002'')}' || '"/><endEvent id="end_1" name="结束"/><sequenceFlow id="edge_1" sourceRef="start_1" targetRef="condition_leave_split" name="提交"/><sequenceFlow id="edge_leave_short" sourceRef="condition_leave_split" targetRef="approve_manager_role" name="短假"/><sequenceFlow id="edge_leave_long" sourceRef="condition_leave_split" targetRef="approve_dept_lead" name="长假" westflow:conditionType="FIELD" westflow:conditionFieldKey="leaveDays" westflow:conditionOperator="GT" westflow:conditionValue="3"><conditionExpression>' || '$' || '{leaveDays > 3}</conditionExpression></sequenceFlow><sequenceFlow id="edge_leave_urgent" sourceRef="condition_leave_split" targetRef="approve_hr_specialist" name="紧急假" westflow:conditionType="FORMULA" westflow:conditionFormulaExpression="urgent == true || leaveDays >= 5"><conditionExpression>' || '$' || '{urgent == true || leaveDays >= 5}</conditionExpression></sequenceFlow><sequenceFlow id="edge_2" sourceRef="approve_manager_role" targetRef="approve_manager_field" name="直属确认"/><sequenceFlow id="edge_3" sourceRef="approve_dept_lead" targetRef="approve_manager_field" name="直属确认"/><sequenceFlow id="edge_4" sourceRef="approve_hr_specialist" targetRef="approve_director_formula" name="总监确认"/><sequenceFlow id="edge_5" sourceRef="approve_manager_field" targetRef="approve_director_formula" name="进入总监确认"/><sequenceFlow id="edge_6" sourceRef="approve_director_formula" targetRef="end_1" name="通过"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'oa_leave:1');

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'oa_expense:1',
    'oa_expense',
    '报销审批',
    'OA',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"oa_expense","processName":"报销审批","category":"OA","processFormKey":"oa-expense-start-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_manager","type":"approver","name":"财务复核审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":540,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_manager","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="oa_expense" name="报销审批" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_manager" name="财务复核审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/><sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'oa_expense:1');

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'oa_common:1',
    'oa_common',
    '通用申请审批',
    'OA',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"oa_common","processName":"通用申请审批","category":"OA","processFormKey":"oa-common-start-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_manager","type":"approver","name":"业务负责人审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":540,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_manager","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="oa_common" name="通用申请审批" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_manager" name="业务负责人审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/><sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'oa_common:1');

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'plm_ecr:1',
    'plm_ecr',
    'ECR 变更申请',
    'PLM',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"plm_ecr","processName":"ECR 变更申请","category":"PLM","processFormKey":"plm-ecr-start-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_manager","type":"approver","name":"PLM 负责人审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":540,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_manager","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="plm_ecr" name="ECR 变更申请" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_manager" name="PLM 负责人审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/><sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'plm_ecr:1');

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'plm_eco:1',
    'plm_eco',
    'ECO 变更执行',
    'PLM',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"plm_eco","processName":"ECO 变更执行","category":"PLM","processFormKey":"plm-eco-start-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_manager","type":"approver","name":"执行负责人审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":540,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_manager","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="plm_eco" name="ECO 变更执行" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_manager" name="执行负责人审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/><sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'plm_eco:1');

INSERT INTO wf_process_definition (
    id, process_key, process_name, category, version, status, dsl_json, bpmn_xml,
    publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
)
SELECT
    'plm_material_change:1',
    'plm_material_change',
    '物料主数据变更申请',
    'PLM',
    1,
    'PUBLISHED',
    '{"dslVersion":"1.0.0","processKey":"plm_material_change","processName":"物料主数据变更申请","category":"PLM","processFormKey":"plm-material-start-form","processFormVersion":"1.0.0","settings":{"allowWithdraw":true,"allowUrge":true,"allowTransfer":true},"nodes":[{"id":"start_1","type":"start","name":"开始","position":{"x":100,"y":100},"config":{"initiatorEditable":true},"ui":{"width":240,"height":88}},{"id":"approve_manager","type":"approver","name":"物料数据负责人审批","position":{"x":320,"y":100},"config":{"assignment":{"mode":"USER","userIds":["usr_002"],"roleCodes":[],"departmentRef":"","formFieldKey":""},"approvalPolicy":{"type":"SEQUENTIAL","voteThreshold":null},"operations":["APPROVE","REJECT","RETURN"],"commentRequired":false},"ui":{"width":240,"height":88}},{"id":"end_1","type":"end","name":"结束","position":{"x":540,"y":100},"config":{},"ui":{"width":240,"height":88}}],"edges":[{"id":"edge_1","source":"start_1","target":"approve_manager","priority":10,"label":"提交"},{"id":"edge_2","source":"approve_manager","target":"end_1","priority":10,"label":"通过"}]}',
    '<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://www.westflow.com/bpmn"><process id="plm_material_change" name="物料主数据变更申请" isExecutable="true"><startEvent id="start_1" name="开始"/><userTask id="approve_manager" name="物料数据负责人审批" flowable:assignee="usr_002"/><endEvent id="end_1" name="结束"/><sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/><sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/></process></definitions>',
    'usr_admin',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_process_definition WHERE id = 'plm_material_change:1');

ALTER TABLE wf_process_definition ADD COLUMN IF NOT EXISTS publisher_user_id VARCHAR(64);
ALTER TABLE wf_process_definition ADD COLUMN IF NOT EXISTS deployment_id VARCHAR(64);
ALTER TABLE wf_process_definition ADD COLUMN IF NOT EXISTS flowable_definition_id VARCHAR(128);

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
    '工作台',
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
    'menu_org',
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
    'menu_system_agent',
    'menu_system',
    '代理关系管理',
    'MENU',
    '/system/agents/list',
    'system/agents/list',
    'system:agent:view',
    'Link',
    70,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_agent');

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
    'menu_system_handover',
    'menu_system',
    '离职转办执行',
    'MENU',
    '/system/handover/execute',
    'system/handover/execute',
    'system:handover:view',
    'ArrowRightLeft',
    80,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_handover');

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
    'menu_system_dict_type',
    'menu_system',
    '字典类型',
    'MENU',
    '/system/dict-types/list',
    'system/dict-types/list',
    'system:dict-type:view',
    'BookText',
    90,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_dict_type');

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
    'menu_system_dict_item',
    'menu_system',
    '字典项',
    'MENU',
    '/system/dict-items/list',
    'system/dict-items/list',
    'system:dict-item:view',
    'BookText',
    100,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_dict_item');

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
    'menu_system_message',
    'menu_system',
    '消息管理',
    'MENU',
    '/system/messages/list',
    'system/messages/list',
    'system:message:view',
    'Mail',
    110,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_message');

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
    'menu_system_file',
    'menu_system',
    '文件管理',
    'MENU',
    '/system/files/list',
    'system/files/list',
    'system:file:view',
    'FolderKanban',
    120,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_file');

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
    'menu_system_notification_template',
    'menu_system',
    '通知模板',
    'MENU',
    '/system/notifications/templates/list',
    'system/notifications/templates/list',
    'system:notification:template:view',
    'Mail',
    130,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_notification_template');

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
    'menu_system_notification_record',
    'menu_system',
    '通知记录',
    'MENU',
    '/system/notifications/records/list',
    'system/notifications/records/list',
    'system:notification:record:view',
    'Mail',
    140,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_notification_record');

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
    'menu_system_log',
    'menu_system',
    '日志管理',
    'MENU',
    '/system/logs/audit/list',
    'system/logs/audit/list',
    'system:log:view',
    'ScrollText',
    150,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_log');

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
    'menu_system_monitor',
    'menu_system',
    '监控管理',
    'MENU',
    '/system/monitor/orchestrator-scans/list',
    'system/monitor/orchestrator-scans/list',
    'system:monitor:view',
    'Gauge',
    160,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_monitor');

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
    'role_system_admin',
    'SYSTEM_ADMIN',
    '平台管理员',
    'SYSTEM',
    '具备全局系统管理与流程管理权限',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role WHERE id = 'role_system_admin');

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

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_007', 'role_process_admin', 'menu_system_agent', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_007');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_008', 'role_process_admin', 'menu_system_handover', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_008');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_019', 'role_oa_user', 'menu_workbench', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_019');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_020', 'role_dept_manager', 'menu_org', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_020');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_021', 'role_process_admin', 'menu_workbench', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_021');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_022', 'role_process_admin', 'menu_workflow', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_022');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_023', 'role_process_admin', 'menu_system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_023');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_001', 'role_oa_user', 'SELF', '*', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_001');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_002', 'role_dept_manager', 'DEPARTMENT_AND_CHILDREN', 'dept_001', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_002');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_003', 'role_process_admin', 'COMPANY', 'cmp_001', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_003');

INSERT INTO wf_role_data_scope (id, role_id, scope_type, scope_value, created_at)
SELECT 'rds_004', 'role_system_admin', 'ALL', '*', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_data_scope WHERE id = 'rds_004');

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
    'usr_001',
    'zhangsan',
    '张三',
    '13800000000',
    'zhangsan@example.com',
    '',
    'cmp_001',
    'dept_001',
    'post_001',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
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
    'usr_002',
    'lisi',
    '李四',
    '13900000000',
    'lisi@example.com',
    '',
    'cmp_001',
    'dept_002',
    'post_002',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
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
    'usr_003',
    'wangwu',
    '王五',
    '13700000000',
    'wangwu@example.com',
    '',
    'cmp_001',
    'dept_003',
    'post_003',
    '$2b$12$hnDyFmHcz5ztyI6c8Mf6Qup5rCPnQVe7sjR57nqM1UwzQ102bw5Ce',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_003');

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
    'usr_admin',
    'admin',
    '平台管理员',
    '13600000000',
    'admin@example.com',
    '',
    'cmp_001',
    'dept_001',
    'post_001',
    '$2b$12$UPsbruT4hka4D0Ce3keZC.BbXxsBD0TcWTRTVznkf.WVNwg9CAWou',
    TRUE,
    TRUE,
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user WHERE id = 'usr_admin');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_001', 'usr_001', 'post_001', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_001');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_002', 'usr_002', 'post_002', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_002');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_003', 'usr_003', 'post_003', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_003');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_004', 'usr_admin', 'post_001', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_004');

INSERT INTO wf_user_post (id, user_id, post_id, is_primary, created_at)
SELECT 'up_005', 'usr_001', 'post_002', FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_post WHERE id = 'up_005');

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

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_005', 'usr_admin', 'role_system_admin', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_005');

INSERT INTO wf_user_role (id, user_id, role_id, created_at)
SELECT 'ur_006', 'usr_admin', 'role_process_admin', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_role WHERE id = 'ur_006');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_001', 'usr_001', 'ai:copilot:open', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_001');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_002', 'usr_001', 'ai:process:start', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_002');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_003', 'usr_001', 'ai:task:handle', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_003');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_009', 'usr_001', 'ai:stats:query', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_009');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_004', 'usr_002', 'ai:copilot:open', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_004');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_005', 'usr_003', 'ai:copilot:open', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_005');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_006', 'usr_admin', 'ai:copilot:open', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_006');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_007', 'usr_admin', 'ai:process:start', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_007');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_008', 'usr_admin', 'ai:task:handle', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_008');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_010', 'usr_admin', 'ai:stats:query', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_010');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_011', 'usr_admin', 'ai:workflow:design', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_011');

INSERT INTO wf_user_ai_capability (id, user_id, capability_code, created_at)
SELECT 'uac_012', 'usr_admin', 'ai:plm:assist', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_user_ai_capability WHERE id = 'uac_012');

INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
SELECT 'ai_agent_001', 'workflow-design-agent', '流程设计智能体', 'ai:workflow:design', TRUE, '负责根据业务意图生成流程定义、节点建议和发布前检查。', '{"businessDomains":["OA","PLM"]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_agent_registry WHERE id = 'ai_agent_001');

INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
SELECT 'ai_agent_002', 'smart-form-agent', '智能填报智能体', 'ai:process:start', TRUE, '负责根据用户输入、语音转写和 OCR 结果生成表单草稿。', '{"supports":["text","voice","ocr"]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_agent_registry WHERE id = 'ai_agent_002');

INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
SELECT 'ai_agent_003', 'task-handle-agent', '待办处理智能体', 'ai:task:handle', TRUE, '负责解释待办、生成处理建议并触发受控操作卡。', '{"actionPolicy":"READ_DIRECT_WRITE_CONFIRM"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_agent_registry WHERE id = 'ai_agent_003');

INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
SELECT 'ai_agent_004', 'stats-agent', '统计问答智能体', 'ai:stats:query', TRUE, '负责查询流程中心、OA、PLM 的统计与图表摘要。', '{"widgets":["stats","chart"]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_agent_registry WHERE id = 'ai_agent_004');

INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
SELECT 'ai_agent_005', 'plm-assistant-agent', 'PLM 助手', 'ai:plm:assist', TRUE, '负责解释 PLM 变更流程、推荐流程模板并辅助定位历史变更单。', '{"businessDomains":["PLM"]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_agent_registry WHERE id = 'ai_agent_005');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_001', 'workflow.definition.list', '查询流程定义', 'PLATFORM', 'READ', 'ai:copilot:open', TRUE, '{"resource":"process-definition"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_001');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_002', 'process.start', '发起流程', 'PLATFORM', 'WRITE', 'ai:process:start', TRUE, '{"resource":"process-instance"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_002');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_003', 'task.query', '查询待办', 'PLATFORM', 'READ', 'ai:copilot:open', TRUE, '{"resource":"task"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_003');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_004', 'task.handle', '处理待办', 'PLATFORM', 'WRITE', 'ai:task:handle', TRUE, '{"resource":"task-action","confirmRequired":true}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_004');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_005', 'stats.query', '查询统计', 'PLATFORM', 'READ', 'ai:stats:query', TRUE, '{"resource":"stats"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_005');

INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_tool_006', 'plm.bill.query', '查询 PLM 单据', 'PLATFORM', 'READ', 'ai:plm:assist', TRUE, '{"resource":"plm-bill"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_tool_registry WHERE id = 'ai_tool_006');

INSERT INTO wf_ai_mcp_registry (id, mcp_code, mcp_name, endpoint_url, transport_type, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_mcp_001', 'westflow-internal-mcp', '平台内置 MCP 桥', NULL, 'INTERNAL', 'ai:copilot:open', TRUE, '{"description":"统一桥接平台内外部 MCP 工具"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_mcp_registry WHERE id = 'ai_mcp_001');

INSERT INTO wf_ai_mcp_registry (id, mcp_code, mcp_name, endpoint_url, transport_type, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT
    'ai_mcp_002',
    'westflow-demo-mcp',
    '本地外部 MCP Demo 服务',
    'http://localhost:8080/api/mcp-demo',
    'STREAMABLE_HTTP',
    'ai:copilot:open',
    TRUE,
    '{"description":"本地可直接演示的 Streamable HTTP MCP 服务","businessDomains":["OA","PLM"],"priority":95,"openConnectionOnStartup":true,"requestTimeoutSeconds":15,"connectTimeoutSeconds":5,"headers":{"X-WestFlow-MCP":"demo"}}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_mcp_registry WHERE id = 'ai_mcp_002');

INSERT INTO wf_ai_skill_registry (id, skill_code, skill_name, skill_path, required_capability_code, enabled, metadata_json, created_at, updated_at)
SELECT 'ai_skill_001', 'workflow-design-skill', '流程设计技能', '/Users/west/.agents/skills/using-superpowers/SKILL.md', 'ai:workflow:design', TRUE, '{"type":"local-skill"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_ai_skill_registry WHERE id = 'ai_skill_001');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT
    'rm_sysadmin_' || m.id,
    'role_system_admin',
    m.id,
    CURRENT_TIMESTAMP
FROM wf_menu m
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_role_menu rm
    WHERE rm.id = 'rm_sysadmin_' || m.id
);

INSERT INTO wf_delegation (id, principal_user_id, delegate_user_id, status, remark, created_at, updated_at)
SELECT 'dlg_001', 'usr_002', 'usr_001', 'ACTIVE', '默认代理关系', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

CREATE TABLE IF NOT EXISTS wf_process_link (
    id VARCHAR(64) PRIMARY KEY,
    root_instance_id VARCHAR(64) NOT NULL,
    parent_instance_id VARCHAR(64) NOT NULL,
    child_instance_id VARCHAR(64) NOT NULL UNIQUE,
    parent_node_id VARCHAR(64) NOT NULL,
    called_process_key VARCHAR(128) NOT NULL,
    called_definition_id VARCHAR(128) NOT NULL,
    link_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    terminate_policy VARCHAR(64) NOT NULL,
    child_finish_policy VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS wf_runtime_append_link (
    id VARCHAR(64) PRIMARY KEY,
    root_instance_id VARCHAR(64) NOT NULL,
    parent_instance_id VARCHAR(64) NOT NULL,
    source_task_id VARCHAR(64) NOT NULL,
    source_node_id VARCHAR(128) NOT NULL,
    append_type VARCHAR(32) NOT NULL,
    runtime_link_type VARCHAR(32) NOT NULL,
    policy VARCHAR(64) NOT NULL,
    target_task_id VARCHAR(64),
    target_instance_id VARCHAR(64),
    target_user_id VARCHAR(64),
    called_process_key VARCHAR(128),
    called_definition_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL,
    operator_user_id VARCHAR(64),
    comment_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS wf_workflow_operation_log (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    process_definition_id VARCHAR(64),
    flowable_definition_id VARCHAR(128),
    business_type VARCHAR(64),
    business_id VARCHAR(64),
    task_id VARCHAR(64),
    node_id VARCHAR(128),
    action_type VARCHAR(64) NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    action_category VARCHAR(64),
    operator_user_id VARCHAR(64),
    target_user_id VARCHAR(64),
    source_task_id VARCHAR(64),
    target_task_id VARCHAR(64),
    comment_text TEXT,
    detail_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_approval_opinion_config (
    id VARCHAR(64) PRIMARY KEY,
    config_code VARCHAR(128) NOT NULL,
    config_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quick_opinions_json TEXT NOT NULL DEFAULT '[]',
    toolbar_actions_json TEXT NOT NULL DEFAULT '[]',
    button_strategies_json TEXT NOT NULL DEFAULT '[]',
    remark VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (config_code)
);

INSERT INTO wf_approval_opinion_config (
    id,
    config_code,
    config_name,
    enabled,
    quick_opinions_json,
    toolbar_actions_json,
    button_strategies_json,
    remark,
    created_at,
    updated_at
)
SELECT
    'opcfg_001',
    'DEFAULT_APPROVAL',
    '默认审批意见配置',
    TRUE,
    '["同意","请补充材料后再提交","请优先线下沟通"]',
    '["quickOpinion","mention","attachment","history"]',
    '[{"actionType":"APPROVE","requireOpinion":false},{"actionType":"REJECT","requireOpinion":true},{"actionType":"RETURN","requireOpinion":true}]',
    '流程管理后台默认审批意见配置',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM wf_approval_opinion_config WHERE id = 'opcfg_001'
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

CREATE TABLE IF NOT EXISTS plm_ecr_change (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    change_title VARCHAR(256) NOT NULL,
    change_reason VARCHAR(2000) NOT NULL,
    affected_product_code VARCHAR(128) NOT NULL,
    priority_level VARCHAR(32) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_eco_execution (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    execution_title VARCHAR(256) NOT NULL,
    execution_plan VARCHAR(2000) NOT NULL,
    effective_date DATE,
    change_reason VARCHAR(2000) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    creator_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plm_material_change (
    id VARCHAR(64) PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    material_name VARCHAR(256) NOT NULL,
    change_reason VARCHAR(2000) NOT NULL,
    change_type VARCHAR(64) NOT NULL,
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
    'bind_seed_plm_ecr_default',
    'PLM_ECR',
    'default',
    'plm_ecr',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_plm_ecr_default');

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
    'bind_seed_plm_eco_default',
    'PLM_ECO',
    'default',
    'plm_eco',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_plm_eco_default');

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
    'bind_seed_plm_material_default',
    'PLM_MATERIAL',
    'default',
    'plm_material_change',
    NULL,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_business_process_binding WHERE id = 'bind_seed_plm_material_default');

CREATE TABLE IF NOT EXISTS wf_notification_channel (
    id VARCHAR(64) PRIMARY KEY,
    channel_code VARCHAR(128) NOT NULL UNIQUE,
    channel_type VARCHAR(64) NOT NULL,
    channel_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mock_mode BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT NOT NULL,
    remark VARCHAR(500),
    last_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_notification_log (
    id VARCHAR(64) PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    channel_code VARCHAR(128) NOT NULL,
    channel_type VARCHAR(64) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    response_message VARCHAR(1000),
    payload_json TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_trigger_definition (
    id VARCHAR(64) PRIMARY KEY,
    trigger_name VARCHAR(128) NOT NULL,
    trigger_key VARCHAR(128) NOT NULL UNIQUE,
    trigger_event VARCHAR(64) NOT NULL,
    business_type VARCHAR(64),
    channel_ids_json TEXT NOT NULL,
    condition_expression VARCHAR(1000),
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_orchestrator_job (
    id VARCHAR(64) PRIMARY KEY,
    automation_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_orchestrator_execution (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    automation_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    'menu_system_notification_channel',
    'menu_system',
    '通知渠道配置',
    'MENU',
    '/system/notification-channels/list',
    'system/notification-channels/list',
    'system:notification-channel:view',
    'BellRing',
    90,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_notification_channel');

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
    'menu_system_trigger',
    'menu_system',
    '触发器管理',
    'MENU',
    '/system/triggers/list',
    'system/triggers/list',
    'system:trigger:view',
    'Zap',
    100,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_trigger');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_009', 'role_process_admin', 'menu_system_notification_channel', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_009');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_010', 'role_process_admin', 'menu_system_trigger', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_010');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_011', 'role_process_admin', 'menu_system_dict_type', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_011');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_012', 'role_process_admin', 'menu_system_dict_item', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_012');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_013', 'role_process_admin', 'menu_system_message', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_013');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_014', 'role_process_admin', 'menu_system_file', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_014');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_015', 'role_process_admin', 'menu_system_notification_template', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_015');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_016', 'role_process_admin', 'menu_system_notification_record', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_016');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_017', 'role_process_admin', 'menu_system_log', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_017');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_018', 'role_process_admin', 'menu_system_monitor', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_018');

INSERT INTO wf_dict_type (id, type_code, type_name, description, enabled, created_at, updated_at)
SELECT 'dict_type_message_status', 'message_status', '消息状态', '系统消息状态字典', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_dict_type WHERE id = 'dict_type_message_status');

INSERT INTO wf_dict_item (id, dict_type_id, item_code, item_label, item_value, sort_order, remark, enabled, created_at, updated_at)
SELECT 'dict_item_message_status_draft', 'dict_type_message_status', 'DRAFT', '草稿', 'DRAFT', 10, '消息草稿状态', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_dict_item WHERE id = 'dict_item_message_status_draft');

INSERT INTO wf_dict_item (id, dict_type_id, item_code, item_label, item_value, sort_order, remark, enabled, created_at, updated_at)
SELECT 'dict_item_message_status_sent', 'dict_type_message_status', 'SENT', '已发送', 'SENT', 20, '消息发送完成状态', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_dict_item WHERE id = 'dict_item_message_status_sent');

INSERT INTO wf_system_message (
    id,
    title,
    content,
    status,
    target_type,
    target_user_ids_json,
    target_department_ids_json,
    sender_user_id,
    sent_at,
    created_at,
    updated_at
)
SELECT
    'msg_seed_001',
    '系统公告示例',
    '这是系统管理 Phase 2 的消息管理示例数据。',
    'SENT',
    'ALL',
    '[]',
    '[]',
    'usr_003',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_system_message WHERE id = 'msg_seed_001');

INSERT INTO wf_file (
    id,
    display_name,
    original_filename,
    bucket_name,
    object_name,
    content_type,
    file_size,
    remark,
    deleted,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    'fil_seed_001',
    '系统使用说明',
    'system-guide.txt',
    'west-flow-ai',
    'seed/system-guide.txt',
    'text/plain',
    1024,
    '文件管理示例数据',
    FALSE,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_file WHERE id = 'fil_seed_001');

INSERT INTO wf_notification_template (
    id,
    template_code,
    template_name,
    channel_type,
    title_template,
    content_template,
    remark,
    enabled,
    created_at,
    updated_at
)
SELECT
    'tpl_seed_notify_001',
    'PROCESS_URGE_IN_APP',
    '流程催办站内模板',
    'IN_APP',
    '流程催办提醒',
    '流程 {processName} 正等待您处理。',
    '通知模板示例数据',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_notification_template WHERE id = 'tpl_seed_notify_001');

UPDATE wf_menu
SET menu_type = 'PERMISSION',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_permission_probe'
  AND menu_type <> 'PERMISSION';

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
    'menu_org',
    NULL,
    '组织管理',
    'DIRECTORY',
    '/system/org',
    NULL,
    NULL,
    'Building2',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_org');

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
    'menu_workflow_center',
    NULL,
    '流程中心',
    'DIRECTORY',
    '/workbench',
    NULL,
    NULL,
    'CheckCircle2',
    40,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_center');

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
    'menu_oa',
    NULL,
    'OA',
    'DIRECTORY',
    '/oa',
    NULL,
    NULL,
    'FileText',
    60,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_oa');

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
    'menu_plm',
    NULL,
    'PLM',
    'DIRECTORY',
    '/plm',
    NULL,
    NULL,
    'Package2',
    70,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm');

UPDATE wf_menu
SET menu_name = '流程管理',
    sort_order = 50,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_workflow';

UPDATE wf_menu
SET sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_workbench';

UPDATE wf_menu
SET sort_order = 30,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system';

UPDATE wf_menu
SET parent_menu_id = 'menu_org',
    sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_user';

UPDATE wf_menu
SET parent_menu_id = 'menu_org',
    sort_order = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_company';

UPDATE wf_menu
SET parent_menu_id = 'menu_org',
    sort_order = 30,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_department';

UPDATE wf_menu
SET parent_menu_id = 'menu_org',
    sort_order = 40,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_post';

UPDATE wf_menu
SET parent_menu_id = 'menu_workflow_center',
    sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_workbench_todo';

UPDATE wf_menu
SET menu_name = '工作台',
    sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_workbench_dashboard';

UPDATE wf_menu
SET parent_menu_id = 'menu_org',
    sort_order = 50,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_role';

UPDATE wf_menu
SET sort_order = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_menu';

UPDATE wf_menu
SET sort_order = 30,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_dict_type';

UPDATE wf_menu
SET sort_order = 40,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_dict_item';

UPDATE wf_menu
SET sort_order = 50,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_agent';

UPDATE wf_menu
SET sort_order = 60,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_handover';

UPDATE wf_menu
SET sort_order = 70,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_message';

UPDATE wf_menu
SET sort_order = 80,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_file';

UPDATE wf_menu
SET sort_order = 90,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_notification_template';

UPDATE wf_menu
SET sort_order = 100,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_notification_record';

UPDATE wf_menu
SET sort_order = 110,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_notification_channel';

UPDATE wf_menu
SET sort_order = 120,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_trigger';

UPDATE wf_menu
SET sort_order = 130,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_log';

UPDATE wf_menu
SET sort_order = 140,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_system_monitor';

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
    'menu_workflow_center_done',
    'menu_workflow_center',
    '已办列表',
    'MENU',
    '/workbench/done/list',
    'workbench/done/list',
    'workbench:done:view',
    'CheckCircle2',
    20,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_center_done');

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
    'menu_workflow_center_initiated',
    'menu_workflow_center',
    '我发起',
    'MENU',
    '/workbench/initiated/list',
    'workbench/initiated/list',
    'workbench:initiated:view',
    'FileText',
    30,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_center_initiated');

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
    'menu_workflow_center_copied',
    'menu_workflow_center',
    '抄送我',
    'MENU',
    '/workbench/copied/list',
    'workbench/copied/list',
    'workbench:copied:view',
    'Mail',
    40,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_center_copied');

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
    'menu_workflow_center_start',
    'menu_workflow_center',
    '发起流程',
    'MENU',
    '/workbench/start',
    'workbench/start',
    'workbench:start:view',
    'FileText',
    50,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_center_start');

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
    'menu_workflow_version',
    'menu_workflow',
    '流程版本',
    'MENU',
    '/workflow/versions/list',
    'workflow/versions/list',
    'workflow:version:view',
    'ScrollText',
    30,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_version');

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
    'menu_workflow_publish_record',
    'menu_workflow',
    '发布记录',
    'MENU',
    '/workflow/publish-records/list',
    'workflow/publish-records/list',
    'workflow:publish-record:view',
    'BookText',
    40,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_publish_record');

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
    'menu_workflow_instance',
    'menu_workflow',
    '实例监控',
    'MENU',
    '/workflow/instances/list',
    'workflow/instances/list',
    'workflow:instance:view',
    'Gauge',
    50,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_instance');

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
    'menu_workflow_operation_log',
    'menu_workflow',
    '操作日志',
    'MENU',
    '/workflow/operation-logs/list',
    'workflow/operation-logs/list',
    'workflow:operation-log:view',
    'ScrollText',
    60,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_operation_log');

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
    'menu_workflow_opinion_config',
    'menu_workflow',
    '审批意见配置',
    'MENU',
    '/workflow/opinion-configs/list',
    'workflow/opinion-configs/list',
    'workflow:opinion-config:view',
    'SquareMenu',
    70,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_opinion_config');

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
    'menu_workflow_binding',
    'menu_workflow',
    '业务流程绑定',
    'MENU',
    '/workflow/bindings/list',
    'workflow/bindings/list',
    'workflow:binding:view',
    'ArrowRightLeft',
    80,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_binding');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_oa_leave', 'menu_oa', '请假申请', 'MENU', '/oa/leave/create', 'oa/leave/create', 'oa:leave:create', 'FileText', 10, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_oa_leave');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_oa_expense', 'menu_oa', '报销申请', 'MENU', '/oa/expense/create', 'oa/expense/create', 'oa:expense:create', 'ReceiptText', 20, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_oa_expense');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_oa_common', 'menu_oa', '通用申请', 'MENU', '/oa/common/create', 'oa/common/create', 'oa:common:create', 'NotebookText', 30, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_oa_common');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_oa_query', 'menu_oa', 'OA 流程查询', 'MENU', '/oa/query', 'oa/query', 'oa:query:view', 'Search', 40, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_oa_query');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_plm_start', 'menu_plm', 'PLM 发起中心', 'MENU', '/plm/start', 'plm/start', 'plm:start:view', 'Package2', 10, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm_start');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_plm_ecr', 'menu_plm', 'ECR 变更申请', 'MENU', '/plm/ecr/create', 'plm/ecr/create', 'plm:ecr:create', 'FileText', 20, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm_ecr');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_plm_eco', 'menu_plm', 'ECO 变更执行', 'MENU', '/plm/eco/create', 'plm/eco/create', 'plm:eco:create', 'ScrollText', 30, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm_eco');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_plm_material_master', 'menu_plm', '物料主数据变更申请', 'MENU', '/plm/material-master/create', 'plm/material-master/create', 'plm:material-master:create', 'NotebookText', 40, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm_material_master');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_plm_query', 'menu_plm', 'PLM 流程查询', 'MENU', '/plm/query', 'plm/query', 'plm:query:view', 'Search', 50, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_plm_query');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_user_create', 'menu_system_user', '新建用户', 'PERMISSION', '/system/users/create', 'system/users/create', 'system:user:create-page', 'Users', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_user_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_user_edit', 'menu_system_user', '编辑用户', 'PERMISSION', '/system/users/$userId/edit', 'system/users/$userId/edit', 'system:user:edit-page', 'Users', 20, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_user_edit');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_user_detail', 'menu_system_user', '用户详情', 'PERMISSION', '/system/users/$userId', 'system/users/$userId', 'system:user:detail-page', 'Users', 30, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_user_detail');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_company_create', 'menu_system_company', '新建公司', 'PERMISSION', '/system/companies/create', 'system/companies/create', 'system:company:create-page', 'Building2', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_company_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_department_create', 'menu_system_department', '新建部门', 'PERMISSION', '/system/departments/create', 'system/departments/create', 'system:department:create-page', 'Building2', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_department_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_post_create', 'menu_system_post', '新建岗位', 'PERMISSION', '/system/posts/create', 'system/posts/create', 'system:post:create-page', 'CircleUserRound', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_post_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_role_create', 'menu_system_role', '新建角色', 'PERMISSION', '/system/roles/create', 'system/roles/create', 'system:role:create-page', 'ShieldCheck', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_role_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_system_menu_create', 'menu_system_menu', '新建菜单', 'PERMISSION', '/system/menus/create', 'system/menus/create', 'system:menu:create-page', 'SquareMenu', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_menu_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_workflow_binding_create', 'menu_workflow_binding', '新建业务流程绑定', 'PERMISSION', '/workflow/bindings/create', 'workflow/bindings/create', 'workflow:binding:create-page', 'ArrowRightLeft', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_binding_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_workflow_opinion_config_create', 'menu_workflow_opinion_config', '新建审批意见配置', 'PERMISSION', '/workflow/opinion-configs/create', 'workflow/opinion-configs/create', 'workflow:opinion-config:create-page', 'SquareMenu', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workflow_opinion_config_create');

INSERT INTO wf_menu (
    id, parent_menu_id, menu_name, menu_type, route_path, component_path, permission_code, icon_name, sort_order, visible, enabled, created_at, updated_at
)
SELECT 'menu_workbench_todo_detail', 'menu_workbench_todo', '待办详情', 'PERMISSION', '/workbench/todos/$taskId', 'workbench/todos/$taskId', 'workbench:todo:detail-page', 'ListTodo', 10, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_workbench_todo_detail');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_oa_sidebar_' || m.id, 'role_oa_user', m.id, CURRENT_TIMESTAMP
FROM wf_menu m
WHERE m.id IN (
    'menu_workbench_dashboard',
    'menu_workbench_todo',
    'menu_workflow_center_done',
    'menu_workflow_center_initiated',
    'menu_workflow_center_copied',
    'menu_workflow_center_start',
    'menu_oa',
    'menu_oa_leave',
    'menu_oa_expense',
    'menu_oa_common',
    'menu_oa_query',
    'menu_plm',
    'menu_plm_start',
    'menu_plm_ecr',
    'menu_plm_eco',
    'menu_plm_material_master',
    'menu_plm_query'
)
AND NOT EXISTS (
    SELECT 1
    FROM wf_role_menu rm
    WHERE rm.id = 'rm_oa_sidebar_' || m.id
);

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_process_sidebar_' || m.id, 'role_process_admin', m.id, CURRENT_TIMESTAMP
FROM wf_menu m
WHERE m.id IN (
    'menu_org',
    'menu_system_user',
    'menu_system_company',
    'menu_system_department',
    'menu_system_post',
    'menu_workflow_center',
    'menu_workbench_todo',
    'menu_workflow_center_done',
    'menu_workflow_center_initiated',
    'menu_workflow_center_copied',
    'menu_workflow_center_start',
    'menu_workflow_definition',
    'menu_workflow_designer',
    'menu_workflow_version',
    'menu_workflow_publish_record',
    'menu_workflow_instance',
    'menu_workflow_operation_log',
    'menu_workflow_opinion_config',
    'menu_workflow_binding',
    'menu_oa',
    'menu_oa_leave',
    'menu_oa_expense',
    'menu_oa_common',
    'menu_oa_query',
    'menu_plm',
    'menu_plm_start',
    'menu_plm_ecr',
    'menu_plm_eco',
    'menu_plm_material_master',
    'menu_plm_query'
)
AND NOT EXISTS (
    SELECT 1
    FROM wf_role_menu rm
    WHERE rm.id = 'rm_process_sidebar_' || m.id
);

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT
    'rm_sysadmin_' || m.id,
    'role_system_admin',
    m.id,
    CURRENT_TIMESTAMP
FROM wf_menu m
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_role_menu rm
    WHERE rm.id = 'rm_sysadmin_' || m.id
);
