package com.westflow.system.org.company.response;

/**
 * 公司详情响应。
 */
public record SystemCompanyDetailResponse(
        // 公司主键。
        String companyId,
        // 公司名称。
        String companyName,
        // 是否启用。
        boolean enabled
) {
}
