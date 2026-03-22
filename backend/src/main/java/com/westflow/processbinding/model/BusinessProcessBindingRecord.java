package com.westflow.processbinding.model;

public record BusinessProcessBindingRecord(
        String id,
        String businessType,
        String sceneCode,
        String processKey,
        String processDefinitionId,
        Boolean enabled,
        Integer priority
) {
}
