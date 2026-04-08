package com.westflow.plm.api;

/**
 * PLM 图纸 / 文档资产响应。
 */
public record PlmDocumentAssetResponse(
        String id,
        String businessType,
        String billId,
        String objectId,
        String documentCode,
        String documentName,
        String documentType,
        String versionLabel,
        String vaultState,
        String fileName,
        String fileType,
        String sourceSystem,
        String externalRef,
        String changeAction,
        Integer sortOrder
) {
}
