package com.westflow.plm.api;

/**
 * PLM 配置基线条目响应。
 */
public record PlmConfigurationBaselineItemResponse(
        String id,
        String objectId,
        String objectCode,
        String objectName,
        String objectType,
        String beforeRevisionCode,
        String afterRevisionCode,
        String effectivity,
        Integer sortOrder
) {
}
