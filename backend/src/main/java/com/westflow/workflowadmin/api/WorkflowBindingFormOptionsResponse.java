package com.westflow.workflowadmin.api;

import java.util.List;

/**
 * 业务流程绑定表单选项。
 */
public record WorkflowBindingFormOptionsResponse(
        List<BusinessTypeOption> businessTypes,
        List<ProcessDefinitionOption> processDefinitions
) {

    public record BusinessTypeOption(String value, String label) {
    }

    public record ProcessDefinitionOption(
            String processDefinitionId,
            String processKey,
            String processName,
            int version
    ) {
    }
}
