package com.westflow.processbinding.model;

/**
 * 业务实例和流程实例的关联记录。
 */
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
