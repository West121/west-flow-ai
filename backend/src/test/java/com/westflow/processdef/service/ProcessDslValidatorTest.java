package com.westflow.processdef.service;

import com.westflow.common.error.ContractException;
import com.westflow.processdef.model.ProcessDslPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessDslValidatorTest {

    private final ProcessDslValidator validator = new ProcessDslValidator();

    @Test
    void shouldAcceptMinimalValidDsl() {
        assertThatCode(() -> validator.validate(validDsl()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMultipleStartNodes() {
        ProcessDslPayload dsl = withNodes(validDsl(), List.of(
                node("start_1", "start", Map.of("initiatorEditable", true)),
                node("start_2", "start", Map.of("initiatorEditable", true)),
                approverNode("approve_1"),
                node("end_1", "end", Map.of())
        ));

        assertValidationFailure(dsl, "必须且只能有一个 start");
    }

    @Test
    void shouldRejectDslWithoutEndNode() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1")
                ),
                List.of(edge("edge_1", "start_1", "approve_1"))
        );

        assertValidationFailure(dsl, "必须至少有一个 end");
    }

    @Test
    void shouldRejectUnreachableNodes() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1"),
                        node("cc_1", "cc", Map.of("targets", Map.of("mode", "USER", "userIds", List.of("usr_003")))),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1"),
                        edge("edge_3", "cc_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "存在不可达节点");
    }

    @Test
    void shouldRejectIsolatedNodes() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1"),
                        node("isolated_1", "cc", Map.of("targets", Map.of("mode", "USER", "userIds", List.of("usr_003")))),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "存在孤立节点");
    }

    @Test
    void shouldRejectConditionNodesWithoutTwoOutgoingEdges() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("condition_1", "condition", Map.of("defaultEdgeId", "edge_default")),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_start", "start_1", "condition_1"),
                        edge("edge_default", "condition_1", "approve_1"),
                        edge("edge_end", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "condition 节点至少需要两条出边");
    }

    @Test
    void shouldRejectUnpairedParallelNodes() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("split_1", "parallel_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "split_1"),
                        edge("edge_2", "split_1", "approve_1"),
                        edge("edge_3", "split_1", "approve_2"),
                        edge("edge_4", "approve_1", "end_1"),
                        edge("edge_5", "approve_2", "end_1")
                )
        );

        assertValidationFailure(dsl, "parallel_split 与 parallel_join 必须成对出现");
    }

    @Test
    void shouldRejectApproverWithoutAssignment() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("approve_1", "approver", Map.of(
                                "approvalPolicy", approvalPolicy(),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "approver 节点必须配置 assignment");
    }

    @Test
    void shouldRejectStartWithoutInitiatorEditable() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of()),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "start 节点必须配置 initiatorEditable");
    }

    @Test
    void shouldRejectCcWithoutTargets() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1"),
                        node("cc_1", "cc", Map.of("readRequired", false)),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "cc_1"),
                        edge("edge_3", "cc_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "cc 节点必须配置 targets");
    }

    @Test
    void shouldRejectStartNodeWithoutInitiatorEditable() {
        ProcessDslPayload dsl = withNodes(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of()),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                )
        );

        assertValidationFailure(dsl, "start 节点必须配置 initiatorEditable");
    }

    @Test
    void shouldRejectCcNodeWithoutUserTargets() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1"),
                        node("cc_1", "cc", Map.of(
                                "targets", Map.of("mode", "USER", "userIds", List.of()),
                                "readRequired", false
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "cc_1"),
                        edge("edge_3", "cc_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "cc 节点 USER 目标不能为空");
    }

    @Test
    void shouldRejectApproverTimeoutPolicyWithoutDurationMinutes() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("approve_1", "approver", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", approvalPolicy(),
                                "operations", List.of("APPROVE", "REJECT", "RETURN"),
                                "commentRequired", false,
                                "timeoutPolicy", Map.of(
                                        "enabled", true,
                                        "action", "APPROVE"
                                )
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "approver 节点 timeoutPolicy.durationMinutes 不能为空");
    }

    @Test
    void shouldRejectSubprocessWithoutCalledProcessKey() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("subprocess_1", "subprocess", Map.of(
                                "calledVersionPolicy", "LATEST_PUBLISHED",
                                "businessBindingMode", "INHERIT_PARENT",
                                "terminatePolicy", "TERMINATE_SUBPROCESS_ONLY",
                                "childFinishPolicy", "RETURN_TO_PARENT"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1"),
                        edge("edge_2", "subprocess_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "subprocess 节点必须配置 calledProcessKey");
    }

    @Test
    void shouldRejectSubprocessWithoutFixedVersionWhenPolicyRequiresIt() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("subprocess_1", "subprocess", Map.of(
                                "calledProcessKey", "oa_leave_subflow",
                                "calledVersionPolicy", "FIXED_VERSION",
                                "businessBindingMode", "INHERIT_PARENT",
                                "terminatePolicy", "TERMINATE_SUBPROCESS_ONLY",
                                "childFinishPolicy", "RETURN_TO_PARENT"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1"),
                        edge("edge_2", "subprocess_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "subprocess 节点 FIXED_VERSION 模式必须配置 calledVersion");
    }

    @Test
    void shouldRejectTimerNodeWithoutScheduleConfig() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("timer_1", "timer", Map.of(
                                "scheduleType", "RELATIVE_TO_ARRIVAL"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "timer_1"),
                        edge("edge_2", "timer_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "timer 节点 delayMinutes 不能为空");
    }

    @Test
    void shouldRejectTriggerNodeWithoutTriggerKey() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("trigger_1", "trigger", Map.of(
                                "triggerMode", "IMMEDIATE",
                                "retryTimes", 2,
                                "retryIntervalMinutes", 5,
                                "payloadTemplate", "{\"biz\":\"value\"}"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "trigger_1"),
                        edge("edge_2", "trigger_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "trigger 节点 triggerKey 不能为空");
    }

    @Test
    void shouldRejectCountersignModeWithoutAtLeastTwoAssignees() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "SEQUENTIAL"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "至少配置 2 名处理人");
    }

    @Test
    void shouldRejectVoteModeWithoutThresholdPercent() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002", "usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "VOTE",
                                "voteRule", Map.of(
                                        "weights", List.of(
                                                Map.of("userId", "usr_002", "weight", 40),
                                                Map.of("userId", "usr_003", "weight", 60)
                                        )
                                )
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "票签阈值必须在 1-100 之间");
    }

    @Test
    void shouldRejectVoteModeWithoutWeightsForAllAssignees() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002", "usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "VOTE",
                                "voteRule", Map.of(
                                        "thresholdPercent", 60,
                                        "weights", List.of(
                                                Map.of("userId", "usr_002", "weight", 100)
                                        )
                                )
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "票签权重必须覆盖所有处理人");
    }

    @Test
    void shouldRejectOrSignModeWithoutAutoFinishRemaining() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002", "usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "OR_SIGN",
                                "autoFinishRemaining", false
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "或签必须启用自动结束剩余任务");
    }

    @Test
    void shouldAcceptValidCountersignDsl() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002", "usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "PARALLEL",
                                "reapprovePolicy", "RESTART_ALL"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    private void assertValidationFailure(ProcessDslPayload dsl, String messagePart) {
        assertThatThrownBy(() -> validator.validate(dsl))
                .isInstanceOf(ContractException.class)
                .satisfies(throwable -> {
                    ContractException exception = (ContractException) throwable;
                    assertThat(exception.getCode()).isEqualTo("VALIDATION.REQUEST_INVALID");
                    assertThat(exception.getMessage()).contains(messagePart);
                });
    }

    private ProcessDslPayload validDsl() {
        return new ProcessDslPayload(
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
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "approve_1"),
                        edge("edge_2", "approve_1", "end_1")
                )
        );
    }

    private ProcessDslPayload withNodes(ProcessDslPayload source, List<ProcessDslPayload.Node> nodes) {
        return new ProcessDslPayload(
                source.dslVersion(),
                source.processKey(),
                source.processName(),
                source.category(),
                source.processFormKey(),
                source.processFormVersion(),
                List.of(),
                source.settings(),
                nodes,
                source.edges()
        );
    }

    private ProcessDslPayload withNodesAndEdges(
            ProcessDslPayload source,
            List<ProcessDslPayload.Node> nodes,
            List<ProcessDslPayload.Edge> edges
    ) {
        return new ProcessDslPayload(
                source.dslVersion(),
                source.processKey(),
                source.processName(),
                source.category(),
                source.processFormKey(),
                source.processFormVersion(),
                List.of(),
                source.settings(),
                nodes,
                edges
        );
    }

    private ProcessDslPayload.Node approverNode(String id) {
        return approverNodeWithConfig(id, Map.of());
    }

    private ProcessDslPayload.Node approverNodeWithConfig(String id, Map<String, Object> overrides) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("assignment", Map.of(
                "mode", "USER",
                "userIds", List.of("usr_002"),
                "roleCodes", List.of(),
                "departmentRef", "",
                "formFieldKey", ""
        ));
        config.put("approvalPolicy", approvalPolicy());
        config.put("operations", List.of("APPROVE", "REJECT", "RETURN"));
        config.put("commentRequired", false);
        config.putAll(overrides);
        return node(id, "approver", config);
    }

    private Map<String, Object> approvalPolicy() {
        Map<String, Object> approvalPolicy = new LinkedHashMap<>();
        approvalPolicy.put("type", "SEQUENTIAL");
        approvalPolicy.put("voteThreshold", null);
        return approvalPolicy;
    }

    private ProcessDslPayload.Node node(String id, String type, Map<String, Object> config) {
        return new ProcessDslPayload.Node(
                id,
                type,
                id,
                String.valueOf(config.getOrDefault("description", id)),
                Map.of("x", 120, "y", 80),
                config,
                Map.of("width", 240, "height", 88)
        );
    }

    private ProcessDslPayload.Edge edge(String id, String source, String target) {
        return new ProcessDslPayload.Edge(id, source, target, 10, id, null);
    }
}
