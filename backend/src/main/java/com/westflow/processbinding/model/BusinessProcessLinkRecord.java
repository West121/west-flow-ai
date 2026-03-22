package com.westflow.processbinding.model;

public record BusinessProcessLinkRecord(
        String id,
        String businessType,
        String businessId,
        String processInstanceId,
        String processDefinitionId,
        String startUserId,
        String status
) {
}
