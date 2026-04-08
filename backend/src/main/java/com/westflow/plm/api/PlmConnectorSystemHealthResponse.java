package com.westflow.plm.api;

/**
 * PLM 单据维度连接器系统健康摘要。
 */
public record PlmConnectorSystemHealthResponse(
        String systemCode,
        String systemName,
        Integer totalJobs,
        Integer pendingCount,
        Integer retryPendingCount,
        Integer dispatchedCount,
        Integer ackPendingCount,
        Integer ackedCount,
        Integer failedCount,
        Integer unhealthyCount
) {
}
