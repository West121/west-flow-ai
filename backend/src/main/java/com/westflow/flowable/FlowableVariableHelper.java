package com.westflow.flowable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FlowableVariableHelper {

    private final FlowableEngineFacade engineFacade;

    public FlowableVariableHelper(FlowableEngineFacade engineFacade) {
        this.engineFacade = engineFacade;
    }

    public Map<String, Object> variables(String processInstanceId) {
        return copyVariables(engineFacade.runtimeService().getVariables(processInstanceId));
    }

    public Optional<Object> variable(String processInstanceId, String variableName) {
        return Optional.ofNullable(engineFacade.runtimeService().getVariable(processInstanceId, variableName));
    }

    public void setVariable(String processInstanceId, String variableName, Object value) {
        engineFacade.runtimeService().setVariable(processInstanceId, variableName, value);
    }

    public void setVariables(String processInstanceId, Map<String, Object> variables) {
        engineFacade.runtimeService().setVariables(processInstanceId, variables);
    }

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
