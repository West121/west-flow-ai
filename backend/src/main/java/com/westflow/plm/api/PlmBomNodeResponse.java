package com.westflow.plm.api;

/**
 * PLM BOM 结构节点响应。
 */
public record PlmBomNodeResponse(
        String id,
        String businessType,
        String billId,
        String parentNodeId,
        String objectId,
        String nodeCode,
        String nodeName,
        String nodeType,
        Double quantity,
        String unit,
        String effectivity,
        String changeAction,
        Integer hierarchyLevel,
        Integer sortOrder
) {
}
