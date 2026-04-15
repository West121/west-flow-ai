package com.westflow.plm.model;

/**
 * PLM 项目关联对象记录。
 */
public record PlmProjectLinkRecord(
        String id,
        String projectId,
        String linkType,
        String targetBusinessType,
        String targetId,
        String targetNo,
        String targetTitle,
        String targetStatus,
        String targetHref,
        String summary,
        int sortOrder
) {
}
