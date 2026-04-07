package com.westflow.plm.model;

/**
 * PLM 业务单生命周期快照。
 */
public record PlmBillLifecycleRecord(
        String billId,
        String billNo,
        String sceneCode,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
