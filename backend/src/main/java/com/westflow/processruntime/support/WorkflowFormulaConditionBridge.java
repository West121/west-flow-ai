package com.westflow.processruntime.support;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;

/**
 * 将流程定义中的公式条件桥接到 Aviator，避免排他分支直接依赖 JUEL 函数解析。
 */
@Service("workflowFormulaConditionBridge")
public class WorkflowFormulaConditionBridge {

    /**
     * 用当前执行上下文变量校验规则表达式。
     */
    public boolean evaluate(DelegateExecution execution, String expression) {
        if (execution == null || expression == null || expression.isBlank()) {
            return false;
        }
        Map<String, Object> variables = new LinkedHashMap<>(execution.getVariables());
        Object result = WorkflowFormulaEvaluator.execute(expression, variables);
        if (result instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (result instanceof Number numberValue) {
            return numberValue.doubleValue() != 0D;
        }
        if (result == null) {
            return false;
        }
        return !String.valueOf(result).isBlank();
    }
}
