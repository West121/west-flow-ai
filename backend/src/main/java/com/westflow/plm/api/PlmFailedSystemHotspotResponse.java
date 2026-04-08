package com.westflow.plm.api;

/**
 * PLM 驾驶舱 - 失败系统热点。
 */
public record PlmFailedSystemHotspotResponse(
        String systemCode,
        String systemName,
        long failedCount,
        long pendingCount,
        long blockedBillCount,
        String summary
) {
}
