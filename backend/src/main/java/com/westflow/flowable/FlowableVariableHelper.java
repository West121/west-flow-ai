package com.westflow.flowable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Flowable 变量读写辅助类。
 */
@Component
public class FlowableVariableHelper {

    private final FlowableEngineFacade engineFacade;

    public FlowableVariableHelper(FlowableEngineFacade engineFacade) {
        this.engineFacade = engineFacade;
    }

    /**
     * 读取流程变量。
     */
    public Map<String, Object> variables(String processInstanceId) {
        return copyVariables(engineFacade.runtimeService().getVariables(processInstanceId));
    }

    /**
     * 读取单个流程变量。
     */
    public Optional<Object> variable(String processInstanceId, String variableName) {
        return Optional.ofNullable(engineFacade.runtimeService().getVariable(processInstanceId, variableName));
    }

    /**
     * 设置单个流程变量。
     */
    public void setVariable(String processInstanceId, String variableName, Object value) {
        engineFacade.runtimeService().setVariable(processInstanceId, variableName, value);
    }

    /**
     * 批量设置流程变量。
     */
    public void setVariables(String processInstanceId, Map<String, Object> variables) {
        engineFacade.runtimeService().setVariables(processInstanceId, variables);
    }

    /**
     * 删除流程变量。
     */
    public void removeVariable(String processInstanceId, String variableName) {
        engineFacade.runtimeService().removeVariable(processInstanceId, variableName);
    }

    private Map<String, Object> copyVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(variables));
    }
}
