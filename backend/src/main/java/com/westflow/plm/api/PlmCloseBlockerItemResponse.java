package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 驾驶舱 - 关闭阻塞项。
 */
public record PlmCloseBlockerItemResponse(
        String id,
        String billId,
        String billNo,
        String businessType,
        String businessTitle,
        String blockerType,
        String blockerTitle,
        long blockerCount,
        String ownerDisplayName,
        String summary,
        LocalDateTime dueAt
) {
}
