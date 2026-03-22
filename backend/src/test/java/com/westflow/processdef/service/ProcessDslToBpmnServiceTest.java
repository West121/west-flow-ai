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
                "description=\"开始\"",
                "initiatorEditable=\"false\"",
                "<userTask",
                "id=\"approve_1\"",
                "name=\"审批\"",
                "description=\"审批\"",
                "approvalPolicyType=\"VOTE\"",
                "voteThreshold=\"60\"",
                "<exclusiveGateway",
                "defaultEdgeId=\"edge_default\"",
                "<serviceTask",
                "targetMode=\"USER\"",
                "<transition",
                "id=\"edge_branch\"",
                "source=\"condition_1\"",
                "target=\"approve_1\"",
                "priority=\"3\"",
                "conditionType=\"EXPRESSION\"",
                "conditionExpression=\"amount &gt; 1000\""
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
