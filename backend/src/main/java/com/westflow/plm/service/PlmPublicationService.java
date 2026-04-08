package com.westflow.plm.service;

import com.westflow.common.error.ContractException;
import com.westflow.plm.api.PlmPublicationActionRequest;
import com.westflow.plm.api.PlmPublicationActionResponse;
import com.westflow.plm.api.PlmConfigurationBaselineResponse;
import com.westflow.plm.api.PlmDocumentAssetResponse;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布与基线读取服务。
 */
@Service
@RequiredArgsConstructor
public class PlmPublicationService {

    private final JdbcTemplate jdbcTemplate;
    private final PlmEnterpriseDepthService plmEnterpriseDepthService;
    private final PlmConnectorJobService plmConnectorJobService;

    public List<PlmDocumentAssetResponse> listDocumentAssets(String businessType, String billId) {
        return plmEnterpriseDepthService.listBillDocumentAssets(businessType, billId);
    }

    public List<PlmConfigurationBaselineResponse> listBaselines(String businessType, String billId) {
        return plmEnterpriseDepthService.listBillBaselines(businessType, billId);
    }

    @Transactional
    public PlmPublicationActionResponse releaseBaseline(
            String businessType,
            String billId,
            String baselineId,
            PlmPublicationActionRequest request,
            String operatorUserId
    ) {
        BaselineRecord baseline = requireBaseline(businessType, billId, baselineId);
        if ("RELEASED".equalsIgnoreCase(baseline.status())) {
            return new PlmPublicationActionResponse(
                    businessType,
                    billId,
                    "BASELINE",
                    baselineId,
                    baseline.baselineName(),
                    baseline.status(),
                    baseline.baselineName() + " 已处于发布状态。",
                    baseline.releasedAt()
            );
        }
        LocalDateTime actedAt = LocalDateTime.now();
        jdbcTemplate.update(
                """
                UPDATE plm_configuration_baseline
                SET status = 'RELEASED',
                    released_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                Timestamp.valueOf(actedAt),
                baselineId
        );
        String summaryMessage = resolveSummaryMessage(
                request == null ? null : request.summaryMessage(),
                baseline.baselineName() + " 已完成发布，等待外部系统消费基线变更。"
        );
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                businessType,
                billId,
                "BASELINE_RELEASED",
                "PENDING",
                summaryMessage,
                operatorUserId
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                businessType,
                billId,
                "BASELINE_RELEASED",
                operatorUserId,
                summaryMessage
        );
        return new PlmPublicationActionResponse(
                businessType,
                billId,
                "BASELINE",
                baselineId,
                baseline.baselineName(),
                "RELEASED",
                summaryMessage,
                actedAt
        );
    }

    @Transactional
    public PlmPublicationActionResponse releaseDocumentAsset(
            String businessType,
            String billId,
            String assetId,
            PlmPublicationActionRequest request,
            String operatorUserId
    ) {
        DocumentAssetRecord asset = requireDocumentAsset(businessType, billId, assetId);
        if ("RELEASED".equalsIgnoreCase(asset.vaultState())) {
            return new PlmPublicationActionResponse(
                    businessType,
                    billId,
                    "DOCUMENT_ASSET",
                    assetId,
                    asset.documentName(),
                    asset.vaultState(),
                    asset.documentName() + " 已处于受控发布状态。",
                    LocalDateTime.now()
            );
        }
        LocalDateTime actedAt = LocalDateTime.now();
        jdbcTemplate.update(
                """
                UPDATE plm_document_asset
                SET vault_state = 'RELEASED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                assetId
        );
        String summaryMessage = resolveSummaryMessage(
                request == null ? null : request.summaryMessage(),
                asset.documentName() + " 已受控发布，等待外部系统同步文档资产。"
        );
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                businessType,
                billId,
                "DOCUMENT_ASSET_RELEASED",
                "PENDING",
                summaryMessage,
                operatorUserId
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                businessType,
                billId,
                "DOCUMENT_ASSET_RELEASED",
                operatorUserId,
                summaryMessage
        );
        return new PlmPublicationActionResponse(
                businessType,
                billId,
                "DOCUMENT_ASSET",
                assetId,
                asset.documentName(),
                "RELEASED",
                summaryMessage,
                actedAt
        );
    }

    private BaselineRecord requireBaseline(String businessType, String billId, String baselineId) {
        List<BaselineRecord> rows = jdbcTemplate.query(
                """
                SELECT id, baseline_name, status, released_at
                FROM plm_configuration_baseline
                WHERE business_type = ?
                  AND bill_id = ?
                  AND id = ?
                """,
                (rs, rowNum) -> new BaselineRecord(
                        rs.getString("id"),
                        rs.getString("baseline_name"),
                        rs.getString("status"),
                        toLocalDateTime(rs, "released_at")
                ),
                businessType,
                billId,
                baselineId
        );
        if (rows.isEmpty()) {
            throw new ContractException(
                    "PLM.BASELINE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "配置基线不存在",
                    Map.of("businessType", businessType, "billId", billId, "baselineId", baselineId)
            );
        }
        return rows.getFirst();
    }

    private DocumentAssetRecord requireDocumentAsset(String businessType, String billId, String assetId) {
        List<DocumentAssetRecord> rows = jdbcTemplate.query(
                """
                SELECT id, document_name, vault_state
                FROM plm_document_asset
                WHERE business_type = ?
                  AND bill_id = ?
                  AND id = ?
                """,
                (rs, rowNum) -> new DocumentAssetRecord(
                        rs.getString("id"),
                        rs.getString("document_name"),
                        rs.getString("vault_state")
                ),
                businessType,
                billId,
                assetId
        );
        if (rows.isEmpty()) {
            throw new ContractException(
                    "PLM.DOCUMENT_ASSET_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "文档资产不存在",
                    Map.of("businessType", businessType, "billId", billId, "assetId", assetId)
            );
        }
        return rows.getFirst();
    }

    private String resolveSummaryMessage(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record BaselineRecord(
            String id,
            String baselineName,
            String status,
            LocalDateTime releasedAt
    ) {
    }

    private record DocumentAssetRecord(
            String id,
            String documentName,
            String vaultState
    ) {
    }
}
