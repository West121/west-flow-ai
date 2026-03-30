package com.westflow.system.org.company.response;

import java.time.OffsetDateTime;

/**
 * 公司列表项响应。
 */
public record SystemCompanyListItemResponse(
        // 公司主键。
        String companyId,
        // 公司名称。
        String companyName,
        // 启用状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
