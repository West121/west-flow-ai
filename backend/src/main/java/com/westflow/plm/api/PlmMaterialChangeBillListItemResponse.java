package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 物料主数据变更申请列表项。
 */
public record PlmMaterialChangeBillListItemResponse(
        String billId,
        String billNo,
        String sceneCode,
        String materialCode,
        String materialName,
        String changeType,
        String changeReason,
        String specificationChange,
        String uom,
        String processInstanceId,
        String status,
        String detailSummary,
        String approvalSummary,
        String creatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
