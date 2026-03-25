package com.westflow.system.org.company.response;

/**
 * 公司详情响应。
 */
public record SystemCompanyDetailResponse(
        String companyId,
        String companyName,
        boolean enabled
) {
}
