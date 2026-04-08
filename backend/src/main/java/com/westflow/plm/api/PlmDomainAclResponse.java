package com.westflow.plm.api;

/**
 * PLM 域级 ACL 响应。
 */
public record PlmDomainAclResponse(
        String id,
        String businessType,
        String billId,
        String domainCode,
        String roleCode,
        String permissionCode,
        String accessScope,
        String policySource,
        Integer sortOrder
) {
}
