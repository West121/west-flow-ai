package com.westflow.processdef.service;

import com.westflow.processdef.api.ProcessDefinitionDetailResponse;
import com.westflow.processdef.api.ProcessRuleMetadataResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessRuleMetadataServiceTest {

    @Mock
    private ProcessDefinitionService processDefinitionService;

    @InjectMocks
    private ProcessRuleMetadataService processRuleMetadataService;

    @Test
    void shouldBuildDefaultRuleMetadataWithoutProcessDefinition() {
        ProcessRuleMetadataResponse response = processRuleMetadataService.build(null, null);

        assertThat(response.variables()).extracting(ProcessRuleMetadataResponse.RuleVariable::key)
                .contains("form", "subtable", "process", "node", "system");
        assertThat(response.functions()).extracting(ProcessRuleMetadataResponse.RuleFunction::name)
                .contains("ifElse", "contains", "daysBetween", "isBlank");
        assertThat(response.snippets()).extracting(ProcessRuleMetadataResponse.RuleSnippet::key)
                .contains("boolean-template", "if-else", "contains", "days-between", "is-blank");
    }

    @Test
    void shouldBuildMetadataFromDslAndSelectedNode() {
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "oa_leave",
                "请假审批",
                "OA",
                "oa_leave_form",
                "1.0.0",
                List.of(
                        new ProcessDslPayload.FormField("days", "请假天数", "number", true),
                        new ProcessDslPayload.FormField("reason", "请假原因", "string", true)
                ),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of(), Map.of(), Map.of()),
                        new ProcessDslPayload.Node("approve_manager", "approver", "部门负责人审批", null, Map.of(), Map.of(), Map.of()),
                        new ProcessDslPayload.Node("end_1", "end", "结束", null, Map.of(), Map.of(), Map.of())
                ),
                List.of()
        );
        when(processDefinitionService.detail("oa_leave:draft")).thenReturn(new ProcessDefinitionDetailResponse(
                "oa_leave:draft",
                "oa_leave",
                "请假审批",
                "OA",
                0,
                "DRAFT",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                payload,
                ""
        ));

        ProcessRuleMetadataResponse response = processRuleMetadataService.build("oa_leave:draft", "approve_manager");

        ProcessRuleMetadataResponse.RuleVariable formGroup = findVariable(response.variables(), "form");
        ProcessRuleMetadataResponse.RuleVariable nodeGroup = findVariable(response.variables(), "node");
        ProcessRuleMetadataResponse.RuleVariable processGroup = findVariable(response.variables(), "process");
        ProcessRuleMetadataResponse.RuleVariable systemGroup = findVariable(response.variables(), "system");

        assertThat(formGroup.children()).extracting(ProcessRuleMetadataResponse.RuleVariable::key)
                .contains("days", "reason");
        assertThat(nodeGroup.children()).extracting(ProcessRuleMetadataResponse.RuleVariable::key)
                .contains("taskId", "currentNodeId", "currentNodeName", "currentNodeType");
        assertThat(processGroup.children()).extracting(ProcessRuleMetadataResponse.RuleVariable::key)
                .contains("processKey", "processName", "initiatorName");
        assertThat(systemGroup.children()).extracting(ProcessRuleMetadataResponse.RuleVariable::key)
                .contains("currentUserId", "now", "today");
        assertThat(response.functions()).extracting(ProcessRuleMetadataResponse.RuleFunction::signature)
                .contains("ifElse(condition, whenTrue, whenFalse)");
    }

    private ProcessRuleMetadataResponse.RuleVariable findVariable(
            List<ProcessRuleMetadataResponse.RuleVariable> variables,
            String key
    ) {
        for (ProcessRuleMetadataResponse.RuleVariable variable : variables) {
            if (key.equals(variable.key())) {
                return variable;
            }
        }
        throw new AssertionError("未找到变量：" + key);
    }
}
