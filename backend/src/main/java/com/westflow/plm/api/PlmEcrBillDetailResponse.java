package com.westflow.plm.api;

/**
 * PLM ECR 变更申请详情。
 */
public record PlmEcrBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String changeTitle,
        String changeReason,
        String affectedProductCode,
        String priorityLevel,
        String processInstanceId,
        String status
) {
}
