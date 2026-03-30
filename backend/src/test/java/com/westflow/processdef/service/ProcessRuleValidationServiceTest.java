package com.westflow.processdef.service;

import com.westflow.processdef.api.ProcessRuleMetadataResponse;
import com.westflow.processdef.api.ProcessRuleValidationRequest;
import com.westflow.processdef.api.ProcessRuleValidationResponse;
import com.westflow.processdef.model.ProcessDslPayload;
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
class ProcessRuleValidationServiceTest {

    @Mock
    private ProcessRuleMetadataService processRuleMetadataService;

    @InjectMocks
    private ProcessRuleValidationService processRuleValidationService;

    @Test
    void shouldValidateAndPreviewFormulaExpression() {
        when(processRuleMetadataService.build("oa_leave:draft", "approve_manager"))
                .thenReturn(metadata());

        ProcessRuleValidationResponse response = processRuleValidationService.validate(
                new ProcessRuleValidationRequest(
                        "oa_leave:draft",
                        "approve_manager",
                        "${ifElse(days > 3, true, false)}"
                )
        );

        assertThat(response.valid()).isTrue();
        assertThat(response.normalizedExpression()).isEqualTo("ifElse(days > 3, true, false)");
        assertThat(response.summary()).contains("试算结果");
        assertThat(response.errors()).isEmpty();
        assertThat(response.availableFunctions()).contains("ifElse", "contains", "daysBetween", "isBlank", "isLongLeave");
    }

    @Test
    void shouldValidateCustomBusinessFormulaFunction() {
        when(processRuleMetadataService.build("oa_leave:draft", "approve_manager"))
                .thenReturn(metadata());

        ProcessRuleValidationResponse response = processRuleValidationService.validate(
                new ProcessRuleValidationRequest(
                        "oa_leave:draft",
                        "approve_manager",
                        "isLongLeave($days)"
                )
        );

        assertThat(response.valid()).isTrue();
        assertThat(response.normalizedExpression()).isEqualTo("isLongLeave(days)");
        assertThat(response.summary()).contains("试算结果");
        assertThat(response.availableFunctions()).contains("isLongLeave");
    }

    @Test
    void shouldReturnCompileErrorWithPositionForInvalidFormula() {
        ProcessRuleValidationResponse response = processRuleValidationService.validate(
                new ProcessRuleValidationRequest(
                        "oa_leave:draft",
                        "approve_manager",
                        "ifElse(days >, true, false)"
                )
        );

        assertThat(response.valid()).isFalse();
        assertThat(response.normalizedExpression()).isEqualTo("ifElse(days >, true, false)");
        assertThat(response.summary()).isNotBlank();
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).message()).contains("Syntax error");
        assertThat(response.errors().get(0).line()).isEqualTo(1);
        assertThat(response.errors().get(0).column()).isPositive();
        assertThat(response.availableFunctions()).contains("ifElse");
    }

    private ProcessRuleMetadataResponse metadata() {
        return new ProcessRuleMetadataResponse(
                List.of(
                        new ProcessRuleMetadataResponse.RuleVariable(
                                "form",
                                "表单主表字段",
                                "group",
                                "form",
                                "表单主表字段。",
                                null,
                                List.of(
                                        new ProcessRuleMetadataResponse.RuleVariable(
                                                "days",
                                                "请假天数",
                                                "number",
                                                "form",
                                                "请假天数。",
                                                "days",
                                                List.of()
                                        )
                                )
                        ),
                        new ProcessRuleMetadataResponse.RuleVariable(
                                "process",
                                "流程上下文",
                                "group",
                                "process",
                                "流程上下文。",
                                null,
                                List.of()
                        )
                ),
                List.of(
                        new ProcessRuleMetadataResponse.RuleFunction(
                                "ifElse",
                                "条件分支",
                                "ifElse(condition, whenTrue, whenFalse)",
                                "条件分支函数。",
                                "基础函数",
                                "ifElse(days > 3, true, false)"
                        ),
                        new ProcessRuleMetadataResponse.RuleFunction(
                                "isLongLeave",
                                "长假判断",
                                "isLongLeave(days)",
                                "根据请假天数判断是否属于长假。",
                                "业务函数",
                                "isLongLeave($days)"
                        )
                ),
                List.of()
        );
    }
}
