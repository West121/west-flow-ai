package com.westflow.workflowadmin.api;

import java.time.Instant;
import java.util.List;

/**
 * 审批意见配置详情。
 */
public record ApprovalOpinionConfigDetailResponse(
        String configId,
        String configCode,
        String configName,
        String status,
        List<String> quickOpinions,
        List<String> toolbarActions,
        List<ButtonStrategy> buttonStrategies,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {

    public record ButtonStrategy(String actionType, boolean requireOpinion) {
    }
}
