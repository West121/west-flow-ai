package com.westflow.system.org.company.model;

/**
 * 公司表实体。
 */
public record SystemCompanyRecord(
        String id,
        String companyName,
        Boolean enabled
) {
}
