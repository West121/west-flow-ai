package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM 配置基线响应。
 */
public record PlmConfigurationBaselineResponse(
        String id,
        String businessType,
        String billId,
        String baselineCode,
        String baselineName,
        String baselineType,
        String status,
        LocalDateTime releasedAt,
        String summaryJson,
        List<PlmConfigurationBaselineItemResponse> items
) {
}
