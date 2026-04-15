package com.westflow.plm.api;

/**
 * 项目关联对象响应。
 */
public record PlmProjectLinkResponse(
        String id,
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
