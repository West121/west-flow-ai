package com.westflow.system.org.company.model;

/**
 * 公司表实体。
 */
public record SystemCompanyRecord(
        // 公司主键。
        String id,
        // 公司名称。
        String companyName,
        // 是否启用。
        Boolean enabled
) {
}
