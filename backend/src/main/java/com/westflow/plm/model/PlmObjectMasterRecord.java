package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 深度对象主数据记录。
 */
public record PlmObjectMasterRecord(
        String id,
        String objectType,
        String objectCode,
        String objectName,
        String ownerUserId,
        String domainCode,
        String lifecycleState,
        String sourceSystem,
        String externalRef,
        String latestRevision,
        String latestVersionLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
