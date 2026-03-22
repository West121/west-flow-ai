package com.westflow.system.org.company.api;

import java.time.OffsetDateTime;

/**
 * 公司列表项响应。
 */
public record SystemCompanyListItemResponse(
        String companyId,
        String companyName,
        String status,
        OffsetDateTime createdAt
) {
}
