package com.westflow.processdef.service;

import com.westflow.processdef.model.ProcessDslPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
