package com.westflow.plm.api;

import java.time.LocalDate;

/**
 * PLM 仪表盘趋势项响应。
 */
public record PlmDashboardTrendResponse(
        LocalDate date,
        long count
) {
}
