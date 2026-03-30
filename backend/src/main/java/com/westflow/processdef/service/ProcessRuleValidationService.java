package com.westflow.processdef.service;

import com.googlecode.aviator.Expression;
import com.googlecode.aviator.exception.CompileExpressionErrorException;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.westflow.processdef.api.ProcessRuleMetadataResponse;
import com.westflow.processdef.api.ProcessRuleValidationRequest;
import com.westflow.processdef.api.ProcessRuleValidationResponse;
import com.westflow.processdef.api.ProcessRuleValidationResponse.ValidationError;
import com.westflow.processruntime.support.WorkflowFormulaEvaluator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 分支规则公式的校验服务。
@Service
@RequiredArgsConstructor
public class ProcessRuleValidationService {

    private static final Pattern SYNTAX_ERROR_POSITION_PATTERN = Pattern.compile(" at (\\d+), lineNumber: (\\d+)");

    private final ProcessRuleMetadataService processRuleMetadataService;

    // 校验并试算公式。
    public ProcessRuleValidationResponse validate(ProcessRuleValidationRequest request) {
        String expression = request == null ? null : request.expression();
        List<String> availableFunctions = WorkflowFormulaEvaluator.functionMetadata().stream()
                .map(WorkflowFormulaEvaluator.FunctionMetadata::name)
                .toList();
        if (expression == null || expression.isBlank()) {
            return invalid(null, "公式不能为空", List.of(), availableFunctions);
        }

        String normalizedExpression = WorkflowFormulaEvaluator.normalizeExpression(expression);

        try {
            WorkflowFormulaEvaluator.validateExpression(normalizedExpression);
            Expression compiled = WorkflowFormulaEvaluator.compile(normalizedExpression);
            Object previewResult = previewResult(compiled, request);
            return new ProcessRuleValidationResponse(
                    true,
                    normalizedExpression,
                    previewResult == null ? "语法校验通过" : "试算结果：%s".formatted(String.valueOf(previewResult)),
                    List.of(),
                    availableFunctions
            );
        } catch (ExpressionSyntaxErrorException | CompileExpressionErrorException exception) {
            return invalid(
                    normalizedExpression,
                    exception.getMessage(),
                    List.of(toValidationError(exception.getMessage())),
                    availableFunctions
            );
        } catch (RuntimeException exception) {
            return invalid(
                    normalizedExpression,
                    exception.getMessage(),
                    List.of(new ValidationError(exception.getMessage(), null, null, null, null)),
                    availableFunctions
            );
        }
    }

    private Object previewResult(Expression expression, ProcessRuleValidationRequest request) {
        Map<String, Object> variables = buildPreviewVariables(request);
        if (variables.isEmpty()) {
            return null;
        }
        try {
            return expression.execute(variables);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> buildPreviewVariables(ProcessRuleValidationRequest request) {
        ProcessRuleMetadataResponse metadata = processRuleMetadataService.build(
                request == null ? null : request.processDefinitionId(),
                request == null ? null : request.nodeId()
        );
        Map<String, Object> variables = new LinkedHashMap<>();
        for (ProcessRuleMetadataResponse.RuleVariable variable : metadata.variables()) {
            collectPreviewVariables(variable, variables);
        }
        return variables;
    }

    private void collectPreviewVariables(ProcessRuleMetadataResponse.RuleVariable variable, Map<String, Object> variables) {
        if (variable == null) {
            return;
        }
        if ("group".equals(variable.valueType())) {
            if (variable.children() != null) {
                for (ProcessRuleMetadataResponse.RuleVariable child : variable.children()) {
                    collectPreviewVariables(child, variables);
                }
            }
            return;
        }
        if (variable.key() == null || variable.key().isBlank()) {
            return;
        }
        variables.putIfAbsent(variable.key(), sampleValue(variable));
    }

    private Object sampleValue(ProcessRuleMetadataResponse.RuleVariable variable) {
        String key = variable.key() == null ? "" : variable.key().toLowerCase();
        String type = variable.valueType() == null ? "" : variable.valueType().toLowerCase();
        if (type.contains("boolean")) {
            return Boolean.TRUE;
        }
        if (type.contains("number") || key.contains("count") || key.contains("days") || key.contains("amount")) {
            return 5;
        }
        if (type.contains("date") && !type.contains("time")) {
            return "2026-03-30";
        }
        if (type.contains("datetime") || type.contains("time")) {
            return "2026-03-30T10:00:00+08:00";
        }
        return variable.expression() == null || variable.expression().isBlank() ? variable.label() : variable.expression();
    }

    private ProcessRuleValidationResponse invalid(
            String normalizedExpression,
            String message,
            List<ValidationError> errors,
            List<String> availableFunctions
    ) {
        return new ProcessRuleValidationResponse(
                false,
                normalizedExpression,
                message,
                errors,
                availableFunctions
        );
    }

    private ValidationError toValidationError(String message) {
        Integer position = extractPosition(message);
        Integer line = extractLine(message);
        Integer column = position == null ? null : position + 1;
        Integer startOffset = position;
        Integer endOffset = position == null ? null : position + 1;
        return new ValidationError(message, line, column, startOffset, endOffset);
    }

    private Integer extractPosition(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = SYNTAX_ERROR_POSITION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    private Integer extractLine(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = SYNTAX_ERROR_POSITION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(2));
    }
}
