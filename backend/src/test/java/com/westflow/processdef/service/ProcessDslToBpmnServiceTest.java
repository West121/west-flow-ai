package com.westflow.processdef.service;

import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDslPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProcessDslToBpmnServiceTest {

    private final ProcessDslToBpmnService service = new ProcessDslToBpmnService();

    @Test
    void shouldConvertNodeConfigAndEdgeConditionIntoBpmnXml() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_leave",
                "请假审批",
                "OA",
                "oa-leave-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of(
                                "initiatorEditable", false
                        )),
                        node("approve_1", "approver", "审批", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "roleCodes", List.of("role_manager"),
                                        "userIds", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("VOTE", 60),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", true
                        )),
                        node("condition_1", "condition", "条件", Map.of(
                                "defaultEdgeId", "edge_default"
                        )),
                        node("cc_1", "cc", "抄送", Map.of(
                                "targets", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_cc"),
                                        "roleCodes", List.of(),
                                        "departmentRef", ""
                                ),
                                "readRequired", false
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_start", "start_1", "approve_1", 1, null),
                        edge("edge_default", "condition_1", "cc_1", 2, null),
                        edge("edge_branch", "condition_1", "approve_1", 3, Map.of(
                                "type", "EXPRESSION",
                                "expression", "amount > 1000"
                        )),
                        edge("edge_cc_end", "cc_1", "end_1", 4, null),
                        edge("edge_approve_end", "approve_1", "end_1", 5, null)
                )
        );

        String xml = service.convert(payload, "oa_leave:1", 1);

        assertThat(xml).contains(
                "<startEvent",
                "id=\"start_1\"",
                "name=\"开始\"",
                "<userTask",
                "id=\"approve_1\"",
                "name=\"审批\"",
                "<exclusiveGateway",
                "default=\"edge_default\"",
                "<userTask",
                "id=\"cc_1\"",
                "<sequenceFlow",
                "id=\"edge_branch\"",
                "sourceRef=\"condition_1\"",
                "targetRef=\"approve_1\"",
                "<conditionExpression"
        );
        assertThat(xml).contains("amount > 1000");
    }

    @Test
    void shouldConvertAutomationNodeConfigIntoBpmnXml() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_auto",
                "自动化审批",
                "OA",
                "oa-auto-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of(
                                "initiatorEditable", true
                        )),
                        node("approve_1", "approver", "审批", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SEQUENTIAL", null),
                                "operations", List.of("APPROVE", "REJECT"),
                                "commentRequired", false,
                                "timeoutPolicy", Map.of(
                                        "enabled", true,
                                        "durationMinutes", 30,
                                        "action", "APPROVE"
                                ),
                                "reminderPolicy", Map.of(
                                        "enabled", true,
                                        "firstReminderAfterMinutes", 10,
                                        "repeatIntervalMinutes", 5,
                                        "maxTimes", 3,
                                        "channels", List.of("IN_APP", "EMAIL")
                                )
                        )),
                        node("timer_1", "timer", "定时等待", Map.of(
                                "scheduleType", "RELATIVE_TO_ARRIVAL",
                                "delayMinutes", 15,
                                "comment", "等待 15 分钟"
                        )),
                        node("trigger_1", "trigger", "调用业务触发器", Map.of(
                                "triggerMode", "SCHEDULED",
                                "scheduleType", "ABSOLUTE_TIME",
                                "runAt", "2026-03-22T10:00:00+08:00",
                                "triggerKey", "leave_sync",
                                "retryTimes", 2,
                                "retryIntervalMinutes", 5,
                                "payloadTemplate", "{\"billNo\":\"${billNo}\"}"
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1", 1, null),
                        edge("edge_2", "approve_1", "timer_1", 2, null),
                        edge("edge_3", "timer_1", "trigger_1", 3, null),
                        edge("edge_4", "trigger_1", "end_1", 4, null)
                )
        );

        String xml = service.convert(payload, "oa_auto:1", 1);

        assertThat(xml).contains(
                "timeoutEnabled=\"true\"",
                "timeoutDurationMinutes=\"30\"",
                "timeoutAction=\"APPROVE\"",
                "reminderEnabled=\"true\"",
                "reminderFirstReminderAfterMinutes=\"10\"",
                "reminderRepeatIntervalMinutes=\"5\"",
                "reminderMaxTimes=\"3\"",
                "reminderChannels=\"IN_APP,EMAIL\"",
                "<intermediateCatchEvent",
                "id=\"timer_1\"",
                "<timeDuration>PT15M</timeDuration>",
                "<serviceTask",
                "id=\"trigger_1\"",
                "flowable:delegateExpression=\"${flowableTriggerDelegate}\""
        );
    }

    @Test
    void shouldConvertSequentialCountersignIntoMultiInstanceUserTask() {
        ProcessDslPayload payload = countersignPayload("SEQUENTIAL");

        String xml = service.convert(payload, "oa_countersign:1", 1);

        assertThat(xml).contains(
                "id=\"approve_countersign\"",
                "<multiInstanceLoopCharacteristics",
                "isSequential=\"true\"",
                "flowable:collection=\"${wfCountersignAssignees_approve_countersign}\"",
                "flowable:elementVariable=\"wfCountersignAssignee_approve_countersign\"",
                "flowable:assignee=\"${wfCountersignAssignee_approve_countersign}\""
        );
    }

    @Test
    void shouldConvertParallelCountersignIntoMultiInstanceUserTask() {
        ProcessDslPayload payload = countersignPayload("PARALLEL");

        String xml = service.convert(payload, "oa_countersign:1", 1);

        assertThat(xml).contains(
                "id=\"approve_countersign\"",
                "<multiInstanceLoopCharacteristics",
                "isSequential=\"false\"",
                "flowable:collection=\"${wfCountersignAssignees_approve_countersign}\"",
                "flowable:elementVariable=\"wfCountersignAssignee_approve_countersign\"",
                "flowable:assignee=\"${wfCountersignAssignee_approve_countersign}\""
        );
    }

    @Test
    void shouldConvertRoleBasedParallelCountersignIntoMultiInstanceUserTask() {
        ProcessDslPayload payload = countersignPayload("PARALLEL", Map.of(
                "mode", "ROLE",
                "userIds", List.of(),
                "roleCodes", List.of("OA_USER"),
                "departmentRef", "",
                "formFieldKey", "",
                "formulaExpression", ""
        ));

        String xml = service.convert(payload, "oa_countersign:1", 1);

        assertThat(xml).contains(
                "id=\"approve_countersign\"",
                "<multiInstanceLoopCharacteristics",
                "isSequential=\"false\"",
                "flowable:collection=\"${wfCountersignAssignees_approve_countersign}\"",
                "flowable:elementVariable=\"wfCountersignAssignee_approve_countersign\"",
                "flowable:assignee=\"${wfCountersignAssignee_approve_countersign}\"",
                "assignmentMode=\"ROLE\"",
                "roleCodes=\"OA_USER\""
        );
    }

    @Test
    void shouldConvertOrSignCountersignIntoConditionalMultiInstanceUserTask() {
        ProcessDslPayload payload = countersignPayload("OR_SIGN");

        String xml = service.convert(payload, "oa_countersign:1", 1);

        assertThat(xml).contains(
                "id=\"approve_countersign\"",
                "<multiInstanceLoopCharacteristics",
                "isSequential=\"false\"",
                "flowable:collection=\"${wfCountersignAssignees_approve_countersign}\"",
                "flowable:elementVariable=\"wfCountersignAssignee_approve_countersign\"",
                "${wfCountersignDecision_approve_countersign == 'APPROVED'}",
                "<completionCondition>"
        );
    }

    @Test
    void shouldConvertVoteCountersignIntoConditionalMultiInstanceUserTask() {
        ProcessDslPayload payload = countersignPayload("VOTE");

        String xml = service.convert(payload, "oa_countersign:1", 1);

        assertThat(xml).contains(
                "id=\"approve_countersign\"",
                "<multiInstanceLoopCharacteristics",
                "isSequential=\"false\"",
                "flowable:collection=\"${wfCountersignAssignees_approve_countersign}\"",
                "flowable:elementVariable=\"wfCountersignAssignee_approve_countersign\"",
                "${wfCountersignDecision_approve_countersign == 'APPROVED' || wfCountersignDecision_approve_countersign == 'REJECTED'}",
                "voteThresholdPercent=\"60\""
        );
    }

    @Test
    void shouldConvertSubprocessNodeIntoCallActivity() {
        ProcessDefinitionMapper mapper = mock(ProcessDefinitionMapper.class);
        ProcessDslToBpmnService service = new ProcessDslToBpmnService(mapper);
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_parent",
                "主流程",
                "OA",
                "oa-parent-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of(
                                "initiatorEditable", true
                        )),
                        node("subprocess_1", "subprocess", "采购复核子流程", Map.ofEntries(
                                Map.entry("calledProcessKey", "plm_purchase_review"),
                                Map.entry("calledVersionPolicy", "LATEST_PUBLISHED"),
                                Map.entry("businessBindingMode", "OVERRIDE"),
                                Map.entry("terminatePolicy", "TERMINATE_PARENT_AND_SUBPROCESS"),
                                Map.entry("childFinishPolicy", "TERMINATE_PARENT"),
                                Map.entry("callScope", "CHILD_AND_DESCENDANTS"),
                                Map.entry("joinMode", "WAIT_PARENT_CONFIRM"),
                                Map.entry("childStartStrategy", "SCENE_BINDING"),
                                Map.entry("sceneCode", "plm_purchase_review_default"),
                                Map.entry("parentResumeStrategy", "WAIT_PARENT_CONFIRM"),
                                Map.entry("inputMappings", List.of(
                                        Map.of("source", "billNo", "target", "sourceBillNo")
                                )),
                                Map.entry("outputMappings", List.of(
                                        Map.of("source", "approvedResult", "target", "purchaseResult")
                                ))
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1", 1, null),
                        edge("edge_2", "subprocess_1", "end_1", 2, null)
                )
        );

        String xml = service.convert(payload, "oa_parent:1", 1);

        assertThat(xml).contains(
                "<callActivity",
                "id=\"subprocess_1\"",
                "name=\"采购复核子流程\"",
                "calledElement=\"${wfSubprocessCalledElement_subprocess_1}\"",
                "<flowable:in",
                "source=\"billNo\"",
                "target=\"sourceBillNo\"",
                "<flowable:out",
                "source=\"approvedResult\"",
                "target=\"purchaseResult\"",
                "calledProcessKey=\"plm_purchase_review\"",
                "calledVersionPolicy=\"LATEST_PUBLISHED\"",
                "terminatePolicy=\"TERMINATE_PARENT_AND_SUBPROCESS\"",
                "childFinishPolicy=\"TERMINATE_PARENT\"",
                "callScope=\"CHILD_AND_DESCENDANTS\"",
                "joinMode=\"WAIT_PARENT_CONFIRM\"",
                "childStartStrategy=\"SCENE_BINDING\"",
                "sceneCode=\"plm_purchase_review_default\"",
                "parentResumeStrategy=\"WAIT_PARENT_CONFIRM\""
        );
    }

    @Test
    void shouldConvertInclusiveGatewayNodesIntoBpmnXml() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_inclusive",
                "包容分支审批",
                "OA",
                "oa-inclusive-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of()),
                        node("inclusive_split_1", "inclusive_split", "包容分支", Map.of(
                                "defaultBranchId", "edge_2",
                                "requiredBranchCount", 1,
                                "branchMergePolicy", "DEFAULT_BRANCH"
                        )),
                        node("approve_1", "approver", "审批A", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE")
                        )),
                        node("approve_2", "approver", "审批B", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE")
                        )),
                        node("inclusive_join_1", "inclusive_join", "包容汇聚", Map.of()),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1", 1, null),
                        edge("edge_2", "inclusive_split_1", "approve_1", 2, Map.of("type", "EXPRESSION", "expression", "amount > 1000")),
                        edge("edge_3", "inclusive_split_1", "approve_2", 3, Map.of("type", "EXPRESSION", "expression", "days > 3")),
                        edge("edge_4", "approve_1", "inclusive_join_1", 4, null),
                        edge("edge_5", "approve_2", "inclusive_join_1", 5, null),
                        edge("edge_6", "inclusive_join_1", "end_1", 6, null)
                )
        );

        String xml = service.convert(payload, "oa_inclusive:1", 1);

        assertThat(xml).contains(
                "<inclusiveGateway",
                "id=\"inclusive_split_1\"",
                "id=\"inclusive_join_1\"",
                "<flowable:executionListener",
                "inclusiveBranchSelectionListener",
                "<westflow:defaultBranchId",
                "edge_2",
                "<westflow:requiredBranchCount",
                "1",
                "<westflow:branchMergePolicy",
                "DEFAULT_BRANCH",
                "<westflow:branchPriority",
                "2",
                "westflowInclusiveSelected_edge_2",
                "branchConditionExpression"
        );
        assertThat(xml).contains("amount > 1000", "days > 3");
    }

    @Test
    void shouldConvertRoleDepartmentAndFieldAssignmentsWithFieldAndFormulaBranchesIntoBpmnXml() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_leave",
                "请假审批",
                "OA",
                "oa-leave-form",
                "1.1.0",
                List.of(
                        new ProcessDslPayload.FormField("leaveType", "请假类型", "STRING", true),
                        new ProcessDslPayload.FormField("leaveDays", "请假天数", "NUMBER", true),
                        new ProcessDslPayload.FormField("urgent", "是否紧急", "BOOLEAN", false),
                        new ProcessDslPayload.FormField("managerUserId", "直属负责人", "USER", false)
                ),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of(
                                "initiatorEditable", true
                        )),
                        node("condition_leave_split", "condition", "请假天数分支", Map.of(
                                "defaultEdgeId", "edge_leave_short"
                        )),
                        node("approve_manager_role", "approver", "部门经理审批", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("role_manager"),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("approve_dept_lead", "approver", "部门负责人审批", Map.of(
                                "assignment", Map.of(
                                        "mode", "DEPARTMENT",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "dept_002",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("approve_hr_specialist", "approver", "HR 专员审批", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("role_hr"),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("approve_manager_field", "approver", "负责人确认", Map.of(
                                "assignment", Map.of(
                                        "mode", "FORM_FIELD",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", "managerUserId",
                                        "formulaExpression", ""
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("approve_director_formula", "approver", "总监确认", Map.of(
                                "assignment", Map.of(
                                        "mode", "FORMULA",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", "leaveDays >= 5 ? 'usr_005' : managerUserId"
                                ),
                                "approvalPolicy", approvalPolicy("SINGLE", null),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "condition_leave_split", 1, null),
                        edge("edge_leave_short", "condition_leave_split", "approve_manager_role", 10, null),
                        edge("edge_leave_long", "condition_leave_split", "approve_dept_lead", 20, Map.of(
                                "type", "FIELD",
                                "fieldKey", "leaveDays",
                                "operator", "GT",
                                "value", 3
                        )),
                        edge("edge_leave_urgent", "condition_leave_split", "approve_hr_specialist", 30, Map.of(
                                "type", "FORMULA",
                                "formulaExpression", "urgent == true || leaveDays >= 5"
                        )),
                        edge("edge_2", "approve_manager_role", "approve_manager_field", 40, null),
                        edge("edge_3", "approve_dept_lead", "approve_manager_field", 50, null),
                        edge("edge_4", "approve_hr_specialist", "approve_director_formula", 60, null),
                        edge("edge_5", "approve_manager_field", "approve_director_formula", 70, null),
                        edge("edge_6", "approve_director_formula", "end_1", 80, null)
                )
        );

        String xml = service.convert(payload, "oa_leave:1", 1);

        assertThat(xml).contains(
                "<exclusiveGateway",
                "id=\"condition_leave_split\"",
                "default=\"edge_leave_short\"",
                "flowable:candidateGroups=\"role_manager\"",
                "flowable:candidateGroups=\"dept_002\"",
                "flowable:candidateGroups=\"role_hr\"",
                "flowable:assignee=\"${managerUserId}\"",
                "westflow:formulaExpression=\"leaveDays &gt;= 5 ? 'usr_005' : managerUserId\"",
                "leaveDays > 3",
                "urgent == true || leaveDays >= 5"
        );
    }

    @Test
    void shouldConvertDynamicBuilderNodeIntoPlaceholderServiceTask() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_dynamic",
                "动态构建流程",
                "OA",
                "oa-dynamic-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of(
                                "initiatorEditable", true
                        )),
                        node("dynamic_1", "dynamic-builder", "追加构建", Map.of(
                                "buildMode", "APPROVER_TASKS",
                                "sourceMode", "RULE_DRIVEN",
                                "ruleExpression", "days > 3",
                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                "maxGeneratedCount", 4,
                                "terminatePolicy", "TERMINATE_GENERATED_ONLY"
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1", 1, null),
                        edge("edge_2", "dynamic_1", "end_1", 2, null)
                )
        );

        String xml = service.convert(payload, "oa_dynamic:1", 1);

        assertThat(xml).contains(
                "<serviceTask",
                "id=\"dynamic_1\"",
                "name=\"追加构建\"",
                "flowable:delegateExpression=\"${flowableDynamicBuilderDelegate}\""
        );
    }

    private ProcessDslPayload.Node node(
            String id,
            String type,
            String name,
            Map<String, Object> config
    ) {
        return new ProcessDslPayload.Node(
                id,
                type,
                name,
                name,
                Map.of("x", 120, "y", 80),
                config,
                Map.of("width", 240, "height", 88)
        );
    }

    private Map<String, Object> approvalPolicy(String type, Integer voteThreshold) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("type", type);
        policy.put("voteThreshold", voteThreshold);
        return policy;
    }

    private ProcessDslPayload.Edge edge(
            String id,
            String source,
            String target,
            Integer priority,
            Map<String, Object> condition
    ) {
        return new ProcessDslPayload.Edge(id, source, target, priority, id, condition);
    }

    private ProcessDslPayload countersignPayload(String approvalMode) {
        return countersignPayload(approvalMode, Map.of(
                "mode", "USER",
                "userIds", List.of("usr_002", "usr_003"),
                "roleCodes", List.of(),
                "departmentRef", "",
                "formFieldKey", "",
                "formulaExpression", ""
        ));
    }

    private ProcessDslPayload countersignPayload(String approvalMode, Map<String, Object> assignment) {
        return new ProcessDslPayload(
                "1.0.0",
                "oa_countersign",
                "会签审批",
                "OA",
                "oa-countersign-form",
                "1.0.0",
                List.of(),
                Map.of(
                        "allowWithdraw", true,
                        "allowUrge", true,
                        "allowTransfer", true
                ),
                List.of(
                        node("start_1", "start", "开始", Map.of()),
                        node("approve_countersign", "approver", "会签审批", Map.of(
                                "approvalMode", approvalMode,
                                "reapprovePolicy", "RESTART_ALL",
                                "autoFinishRemaining", !"SEQUENTIAL".equals(approvalMode) && !"PARALLEL".equals(approvalMode),
                                "assignment", assignment,
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "voteRule", "VOTE".equals(approvalMode)
                                        ? Map.of(
                                                "thresholdPercent", 60,
                                                "passCondition", "GREATER_THAN_OR_EQUAL",
                                                "rejectCondition", "GREATER_THAN_OR_EQUAL",
                                                "weights", List.of(
                                                        Map.of("userId", "usr_002", "weight", 40),
                                                        Map.of("userId", "usr_003", "weight", 60)
                                                )
                                        )
                                        : Map.of()
                        )),
                        node("end_1", "end", "结束", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_countersign", 1, null),
                        edge("edge_2", "approve_countersign", "end_1", 2, null)
                )
        );
    }
}
