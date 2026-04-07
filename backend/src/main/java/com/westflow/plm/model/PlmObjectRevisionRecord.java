package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 深度对象版本记录。
 */
public record PlmObjectRevisionRecord(
        String id,
        String objectId,
        String revisionCode,
        String versionLabel,
        String versionStatus,
        String checksum,
        String summaryJson,
        String snapshotJson,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
