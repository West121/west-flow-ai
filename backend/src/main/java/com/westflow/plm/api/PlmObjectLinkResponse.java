package com.westflow.plm.api;

/**
 * PLM 单据对象关联响应。
 */
public record PlmObjectLinkResponse(
        String id,
        String businessType,
        String billId,
        String objectId,
        String objectRevisionId,
        String objectType,
        String objectCode,
        String objectName,
        String roleCode,
        String changeAction,
        String beforeRevisionCode,
        String afterRevisionCode,
        String remark,
        Integer sortOrder
) {
}
