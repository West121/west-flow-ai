package com.westflow.aiadmin.support;

/**
 * AI 管理后台的注册表关联摘要。
 */
public record AiRegistryLinkResponse(
        String entityType,
        String entityId,
        String entityCode,
        String entityName,
        String capabilityCode,
        String status
) {
}
