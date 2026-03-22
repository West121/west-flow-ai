package com.westflow.plm.model;

/**
 * PLM 物料主数据变更申请记录。
 */
public record PlmMaterialChangeBillRecord(
        String id,
        String billNo,
        String sceneCode,
        String materialCode,
        String materialName,
        String changeReason,
        String changeType,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
