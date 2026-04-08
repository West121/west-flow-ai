package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM 单据维度连接器健康摘要。
 */
public record PlmConnectorHealthSummaryResponse(
        String businessType,
        String billId,
        Integer totalJobs,
        Integer pendingCount,
        Integer retryPendingCount,
        Integer dispatchedCount,
        Integer ackPendingCount,
        Integer ackedCount,
        Integer failedCount,
        Integer unhealthyCount,
        LocalDateTime latestDispatchedAt,
        LocalDateTime latestAckAt,
        List<PlmConnectorSystemHealthResponse> systems
) {
}
