package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 驾驶舱 - 卡住的同步项。
 */
public record PlmStuckSyncItemResponse(
        String id,
        String billId,
        String billNo,
        String businessType,
        String businessTitle,
        String systemCode,
        String systemName,
        String connectorName,
        String status,
        long pendingCount,
        long failedCount,
        String ownerDisplayName,
        String summary,
        LocalDateTime updatedAt
) {
}
