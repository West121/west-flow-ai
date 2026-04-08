package com.westflow.plm.api;

/**
 * PLM 对象权限响应。
 */
public record PlmObjectAclResponse(
        String id,
        String businessType,
        String billId,
        String objectId,
        String objectCode,
        String objectName,
        String subjectType,
        String subjectCode,
        String permissionCode,
        String accessScope,
        Boolean inherited,
        Integer sortOrder
) {
}
