package com.westflow.plm.api;

/**
 * PLM 物料主数据变更申请详情。
 */
public record PlmMaterialChangeBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String materialCode,
        String materialName,
        String changeReason,
        String changeType,
        String processInstanceId,
        String status
) {
}
