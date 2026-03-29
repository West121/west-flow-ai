package com.westflow.processruntime.link;

/**
 * 业务单与流程实例关联快照。
 */
public record BusinessLinkSnapshot(
        String businessType,
        String businessId,
        String processInstanceId,
        String processDefinitionId,
        String startUserId,
        String status
) {
}
