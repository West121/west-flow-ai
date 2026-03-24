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
    void shouldRejectInclusiveSplitWithoutJoin() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1"),
                        edge("edge_3", "inclusive_split_1", "approve_2"),
                        edge("edge_4", "approve_1", "end_1"),
                        edge("edge_5", "approve_2", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 与 inclusive_join 必须成对出现");
    }

    @Test
    void shouldRejectInclusiveSplitWithoutBranchExpressions() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1"),
                        edge("edge_3", "inclusive_split_1", "approve_2"),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 分支必须配置 condition.type");
    }

    @Test
    void shouldRejectInclusiveSplitWithInvalidMergePolicy() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of("branchMergePolicy", "NOT_SUPPORTED")),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1", expressionCondition("amount > 10000")),
                        edge("edge_3", "inclusive_split_1", "approve_2", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 节点 branchMergePolicy 不合法");
    }

    @Test
    void shouldRejectInclusiveSplitWithRequiredCountExceedingBranches() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of(
                                "requiredBranchCount", 3,
                                "branchMergePolicy", "REQUIRED_COUNT"
                        )),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1", expressionCondition("amount > 10000")),
                        edge("edge_3", "inclusive_split_1", "approve_2", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 节点 requiredBranchCount 不合法");
    }

    @Test
    void shouldRejectInclusiveSplitWithNonPositiveBranchPriority() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        new ProcessDslPayload.Edge("edge_2", "inclusive_split_1", "approve_1", -1, "金额超限", expressionCondition("amount > 10000")),
                        new ProcessDslPayload.Edge("edge_3", "inclusive_split_1", "approve_2", 10, "长假", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 分支 branchPriority 必须为正整数");
    }

    @Test
    void shouldRejectInclusiveSplitWithoutBranchPriority() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        new ProcessDslPayload.Edge("edge_2", "inclusive_split_1", "approve_1", null, "金额超限", expressionCondition("amount > 10000")),
                        new ProcessDslPayload.Edge("edge_3", "inclusive_split_1", "approve_2", 10, "长假", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "inclusive_split 分支 branchPriority 必须为正整数");
    }

    @Test
    void shouldAcceptInclusiveSplitWithMergePolicyAndDefaultBranch() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of(
                                "defaultBranchId", "edge_2",
                                "requiredBranchCount", 1,
                                "branchMergePolicy", "DEFAULT_BRANCH"
                        )),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1", expressionCondition("amount > 10000")),
                        edge("edge_3", "inclusive_split_1", "approve_2", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptPairedInclusiveNodes() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("inclusive_split_1", "inclusive_split", Map.of()),
                        approverNode("approve_1"),
                        approverNode("approve_2"),
                        node("inclusive_join_1", "inclusive_join", Map.of()),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "inclusive_split_1"),
                        edge("edge_2", "inclusive_split_1", "approve_1", expressionCondition("amount > 10000")),
                        edge("edge_3", "inclusive_split_1", "approve_2", expressionCondition("urgent == true")),
                        edge("edge_4", "approve_1", "inclusive_join_1"),
                        edge("edge_5", "approve_2", "inclusive_join_1"),
                        edge("edge_6", "inclusive_join_1", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptFieldAndFormulaBranchConditionsWithMixedAssignments() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("condition_1", "condition", Map.of("defaultEdgeId", "edge_short")),
                        approverNodeWithConfig("approve_short", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("role_manager"),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "SINGLE",
                                "operations", List.of("APPROVE", "REJECT")
                        )),
                        approverNodeWithConfig("approve_long", Map.of(
                                "assignment", Map.of(
                                        "mode", "DEPARTMENT",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "dept_002",
                                        "formFieldKey", ""
                                ),
                                "approvalMode", "SINGLE",
                                "operations", List.of("APPROVE", "REJECT")
                        )),
                        approverNodeWithConfig("approve_formula", Map.of(
                                "assignment", Map.of(
                                        "mode", "FORMULA",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", "leaveDays >= 5 ? 'usr_005' : managerUserId"
                                ),
                                "approvalMode", "SINGLE",
                                "operations", List.of("APPROVE", "REJECT")
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "condition_1"),
                        edge("edge_short", "condition_1", "approve_short"),
                        edge("edge_long", "condition_1", "approve_long", fieldCondition("leaveDays", "GT", 3)),
                        edge("edge_formula", "condition_1", "approve_formula", formulaCondition("urgent == true || leaveDays >= 5")),
                        edge("edge_2", "approve_short", "end_1"),
                        edge("edge_3", "approve_long", "end_1"),
                        edge("edge_4", "approve_formula", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFieldBranchWithoutOperator() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("condition_1", "condition", Map.of("defaultEdgeId", "edge_short")),
                        approverNode("approve_short"),
                        approverNode("approve_long"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "condition_1"),
                        edge("edge_short", "condition_1", "approve_short"),
                        edge("edge_long", "condition_1", "approve_long", Map.of(
                                "type", "FIELD",
                                "fieldKey", "leaveDays",
                                "value", 3
                        )),
                        edge("edge_2", "approve_short", "end_1"),
                        edge("edge_3", "approve_long", "end_1")
                )
        );

        assertValidationFailure(dsl, "FIELD 类型 operator 不合法");
    }

    @Test
    void shouldRejectFormulaBranchWithoutExpression() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("condition_1", "condition", Map.of("defaultEdgeId", "edge_short")),
                        approverNode("approve_short"),
                        approverNode("approve_formula"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "condition_1"),
                        edge("edge_short", "condition_1", "approve_short"),
                        edge("edge_formula", "condition_1", "approve_formula", Map.of(
                                "type", "FORMULA"
                        )),
                        edge("edge_2", "approve_short", "end_1"),
                        edge("edge_3", "approve_formula", "end_1")
                )
        );

        assertValidationFailure(dsl, "FORMULA 类型必须配置 expression");
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
    void shouldRejectSubprocessWithInvalidJoinMode() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("subprocess_1", "subprocess", Map.of(
                                "calledProcessKey", "oa_leave_subflow",
                                "calledVersionPolicy", "LATEST_PUBLISHED",
                                "businessBindingMode", "INHERIT_PARENT",
                                "terminatePolicy", "TERMINATE_SUBPROCESS_ONLY",
                                "childFinishPolicy", "RETURN_TO_PARENT",
                                "joinMode", "INVALID_JOIN_MODE"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1"),
                        edge("edge_2", "subprocess_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "subprocess 节点 joinMode 不合法");
    }

    @Test
    void shouldRejectSceneBindingSubprocessWithFixedVersionPolicy() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("subprocess_1", "subprocess", Map.of(
                                "calledProcessKey", "oa_leave_subflow",
                                "calledVersionPolicy", "FIXED_VERSION",
                                "calledVersion", 1,
                                "businessBindingMode", "INHERIT_PARENT",
                                "terminatePolicy", "TERMINATE_SUBPROCESS_ONLY",
                                "childFinishPolicy", "RETURN_TO_PARENT",
                                "childStartStrategy", "SCENE_BINDING",
                                "sceneCode", "long_leave"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1"),
                        edge("edge_2", "subprocess_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "subprocess 节点 SCENE_BINDING 模式仅支持 LATEST_PUBLISHED");
    }

    @Test
    void shouldRejectSceneBindingSubprocessWithoutSceneCode() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("subprocess_1", "subprocess", Map.of(
                                "calledProcessKey", "oa_leave_subflow",
                                "calledVersionPolicy", "LATEST_PUBLISHED",
                                "businessBindingMode", "INHERIT_PARENT",
                                "terminatePolicy", "TERMINATE_SUBPROCESS_ONLY",
                                "childFinishPolicy", "RETURN_TO_PARENT",
                                "childStartStrategy", "SCENE_BINDING"
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "subprocess_1"),
                        edge("edge_2", "subprocess_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "subprocess 节点 SCENE_BINDING 模式必须配置 sceneCode");
    }

    @Test
    void shouldRejectDynamicBuilderWithoutBuildMode() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("sourceMode", "RULE");
        dynamicConfig.put("ruleExpression", "days > 3");
        dynamicConfig.put("appendPolicy", "SERIAL_AFTER_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 3);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "dynamic-builder 节点 buildMode 不合法");
    }

    @Test
    void shouldRejectDynamicBuilderWithoutRuleExpressionForRuleMode() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "APPROVER_TASKS");
        dynamicConfig.put("sourceMode", "RULE");
        dynamicConfig.put("appendPolicy", "SERIAL_AFTER_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 3);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "dynamic-builder 节点 ruleExpression 不能为空");
    }

    @Test
    void shouldAcceptValidDynamicBuilderDsl() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "APPROVER_TASKS");
        dynamicConfig.put("sourceMode", "MANUAL_TEMPLATE");
        dynamicConfig.put("manualTemplateCode", "tmpl_append_001");
        dynamicConfig.put("appendPolicy", "PARALLEL_WITH_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 5);
        dynamicConfig.put("terminatePolicy", "TERMINATE_PARENT_AND_GENERATED");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        node("approve_1", "approver", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", Map.of(
                                        "type", "SINGLE"
                                ),
                                "operations", List.of("APPROVE", "REJECT")
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "approve_1"),
                        edge("edge_3", "approve_1", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptModelDrivenDynamicBuilderDslBySceneCode() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "SUBPROCESS_CALLS");
        dynamicConfig.put("sourceMode", "MODEL_DRIVEN");
        dynamicConfig.put("sceneCode", "oa_leave_long_vacation");
        dynamicConfig.put("appendPolicy", "SERIAL_AFTER_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 1);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        node("approve_1", "approver", Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", Map.of("type", "SINGLE"),
                                "operations", List.of("APPROVE")
                        )),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "approve_1"),
                        edge("edge_3", "approve_1", "end_1")
                )
        );

        assertThatCode(() -> validator.validate(dsl)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDynamicBuilderRoleTargetsWithoutRoleCodes() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "APPROVER_TASKS");
        dynamicConfig.put("sourceMode", "RULE");
        dynamicConfig.put("ruleExpression", "leaveDays > 3");
        dynamicConfig.put("appendPolicy", "SERIAL_AFTER_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 1);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        dynamicConfig.put("targets", Map.of(
                "mode", "ROLE",
                "roleCodes", List.of()
        ));
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "approve_1"),
                        edge("edge_3", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "dynamic-builder 节点 ROLE 默认目标不能为空");
    }

    @Test
    void shouldRejectDynamicBuilderFixedVersionFallbackWithoutCalledVersion() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "SUBPROCESS_CALLS");
        dynamicConfig.put("sourceMode", "MODEL_DRIVEN");
        dynamicConfig.put("sceneCode", "oa_leave_long_vacation");
        dynamicConfig.put("calledProcessKey", "oa_sub_review");
        dynamicConfig.put("calledVersionPolicy", "FIXED_VERSION");
        dynamicConfig.put("appendPolicy", "SERIAL_AFTER_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 1);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        approverNode("approve_1"),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "approve_1"),
                        edge("edge_3", "approve_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "dynamic-builder 节点 FIXED_VERSION 模式必须配置 calledVersion");
    }

    @Test
    void shouldRejectDynamicBuilderTaskModeThatEndsImmediately() {
        Map<String, Object> dynamicConfig = new LinkedHashMap<>();
        dynamicConfig.put("buildMode", "APPROVER_TASKS");
        dynamicConfig.put("sourceMode", "RULE");
        dynamicConfig.put("ruleExpression", "days > 3");
        dynamicConfig.put("appendPolicy", "PARALLEL_WITH_CURRENT");
        dynamicConfig.put("maxGeneratedCount", 2);
        dynamicConfig.put("terminatePolicy", "TERMINATE_GENERATED_ONLY");
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        node("dynamic_1", "dynamic-builder", dynamicConfig),
                        node("end_1", "end", Map.of())
                ),
                List.of(
                        edge("edge_1", "start_1", "dynamic_1"),
                        edge("edge_2", "dynamic_1", "end_1")
                )
        );

        assertValidationFailure(dsl, "dynamic-builder 节点 APPROVER_TASKS 模式后续必须保留活跃等待节点");
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

    @Test
    void shouldAcceptRoleBasedCountersignDsl() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("OA_USER"),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", ""
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

    @Test
    void shouldAcceptFormulaBasedCountersignDsl() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "FORMULA",
                                        "userIds", List.of(),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", "ifElse(leaveDays >= 3, 'usr_001,usr_002', 'usr_002,usr_003')"
                                ),
                                "approvalMode", "OR_SIGN",
                                "autoFinishRemaining", true
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

    @Test
    void shouldAcceptRoleBasedVoteCountersignWithoutExplicitWeights() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("OA_USER"),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", ""
                                ),
                                "approvalMode", "VOTE",
                                "voteRule", Map.of(
                                        "thresholdPercent", 60,
                                        "weights", List.of()
                                )
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

    @Test
    void shouldRejectCustomVoteWeightsForRoleBasedVoteCountersign() {
        ProcessDslPayload dsl = withNodesAndEdges(
                validDsl(),
                List.of(
                        node("start_1", "start", Map.of("initiatorEditable", true)),
                        approverNodeWithConfig("approve_1", Map.of(
                                "assignment", Map.of(
                                        "mode", "ROLE",
                                        "userIds", List.of(),
                                        "roleCodes", List.of("OA_USER"),
                                        "departmentRef", "",
                                        "formFieldKey", "",
                                        "formulaExpression", ""
                                ),
                                "approvalMode", "VOTE",
                                "voteRule", Map.of(
                                        "thresholdPercent", 60,
                                        "weights", List.of(
                                                Map.of("userId", "usr_001", "weight", 40),
                                                Map.of("userId", "usr_002", "weight", 60)
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

        assertValidationFailure(dsl, "非指定人员票签不允许配置自定义权重");
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

    private ProcessDslPayload.Edge edge(String id, String source, String target, Map<String, Object> condition) {
        return new ProcessDslPayload.Edge(id, source, target, 10, id, condition);
    }

    private Map<String, Object> expressionCondition(String expression) {
        return Map.of(
                "type", "EXPRESSION",
                "expression", expression
        );
    }

    private Map<String, Object> fieldCondition(String fieldKey, String operator, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "FIELD");
        condition.put("fieldKey", fieldKey);
        condition.put("operator", operator);
        condition.put("value", value);
        return condition;
    }

    private Map<String, Object> formulaCondition(String expression) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "FORMULA");
        condition.put("formulaExpression", expression);
        return condition;
    }
}
