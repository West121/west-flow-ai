package com.westflow.plm.api;

/**
 * PLM 仪表盘分布项响应。
 */
public record PlmDashboardDistributionResponse(
        String code,
        String label,
        long count
) {
}
