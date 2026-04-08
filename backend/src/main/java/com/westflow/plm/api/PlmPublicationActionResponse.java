package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 发布动作响应。
 */
public record PlmPublicationActionResponse(
        String businessType,
        String billId,
        String targetType,
        String targetId,
        String targetName,
        String status,
        String message,
        LocalDateTime actedAt
) {
}
