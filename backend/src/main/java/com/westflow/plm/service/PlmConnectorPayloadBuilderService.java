package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 为不同外部系统构建更接近生产集成形态的派发载荷。
 */
@Service
@RequiredArgsConstructor
public class PlmConnectorPayloadBuilderService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlmConnectorProperties connectorProperties;

    public String buildPayload(
            String businessType,
            String billId,
            String jobType,
            String connectorCode,
            String systemCode,
            String systemName,
            String endpointKey,
            String operatorUserId,
            String summaryMessage
    ) {
        BillSnapshot bill = loadBillSnapshot(businessType, billId);
        PlmConnectorProperties.ConnectorTarget target = connectorProperties.resolve(systemCode);

        List<Map<String, Object>> objectLinks = loadObjectLinks(businessType, billId);
        List<Map<String, Object>> bomHighlights = loadBomHighlights(businessType, billId);
        List<Map<String, Object>> documentAssets = loadDocumentAssets(businessType, billId);
        List<Map<String, Object>> baselines = loadBaselines(businessType, billId);
        ImplementationSnapshot implementation = loadImplementationSnapshot(businessType, billId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "plm.connector.v8");
        payload.put("businessType", businessType);
        payload.put("billId", billId);
        payload.put("jobType", jobType);
        payload.put("summaryMessage", summaryMessage);
        payload.put("operatorUserId", operatorUserId);
        payload.put("connectorCode", connectorCode);
        payload.put("systemCode", systemCode);
        payload.put("systemName", systemName);
        payload.put("endpointKey", endpointKey);

        Map<String, Object> dispatchProfile = new LinkedHashMap<>();
        dispatchProfile.put("mode", target.getMode());
        dispatchProfile.put("transport", target.getTransport());
        if (hasText(target.getEndpointUrl())) {
            dispatchProfile.put("endpointUrl", target.getEndpointUrl());
        }
        if (hasText(target.getEndpointPath())) {
            dispatchProfile.put("endpointPath", target.getEndpointPath());
        }
        if (hasText(target.getDescription())) {
            dispatchProfile.put("description", target.getDescription());
        }
        payload.put("dispatchProfile", dispatchProfile);

        Map<String, Object> billMap = new LinkedHashMap<>();
        billMap.put("billNo", bill.billNo());
        billMap.put("title", bill.title());
        billMap.put("status", bill.status());
        billMap.put("sceneCode", bill.sceneCode());
        billMap.put("creatorUserId", bill.creatorUserId());
        if (bill.effectiveDate() != null) {
            billMap.put("effectiveDate", bill.effectiveDate().toString());
        }
        payload.put("bill", billMap);

        Map<String, Object> affectedSummary = new LinkedHashMap<>();
        affectedSummary.put("objectLinkCount", objectLinks.size());
        affectedSummary.put("bomNodeCount", bomHighlights.size());
        affectedSummary.put("documentCount", documentAssets.size());
        affectedSummary.put("baselineCount", baselines.size());
        affectedSummary.put("objects", objectLinks);
        affectedSummary.put("bomHighlights", bomHighlights);
        affectedSummary.put("documents", documentAssets);
        affectedSummary.put("baselines", baselines);
        payload.put("affectedData", affectedSummary);

        Map<String, Object> implementationMap = new LinkedHashMap<>();
        implementationMap.put("taskCount", implementation.taskCount());
        implementationMap.put("pendingTaskCount", implementation.pendingTaskCount());
        implementationMap.put("blockedTaskCount", implementation.blockedTaskCount());
        implementationMap.put("verificationTaskCount", implementation.verificationTaskCount());
        implementationMap.put("requiredEvidenceCount", implementation.requiredEvidenceCount());
        implementationMap.put("evidenceCount", implementation.evidenceCount());
        implementationMap.put("acceptancePendingCount", implementation.acceptancePendingCount());
        implementationMap.put("tasks", implementation.tasks());
        payload.put("implementation", implementationMap);

        payload.put("systemPayload", buildSystemPayload(
                systemCode,
                jobType,
                bill,
                objectLinks,
                bomHighlights,
                documentAssets,
                baselines,
                implementation
        ));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化连接器任务请求", ex);
        }
    }

    private Map<String, Object> buildSystemPayload(
            String systemCode,
            String jobType,
            BillSnapshot bill,
            List<Map<String, Object>> objectLinks,
            List<Map<String, Object>> bomHighlights,
            List<Map<String, Object>> documentAssets,
            List<Map<String, Object>> baselines,
            ImplementationSnapshot implementation
    ) {
        String normalized = normalizeSystemCode(systemCode);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", switch (normalized) {
            case "ERP" -> "MASTER_DATA_SYNC";
            case "MES" -> "ROLL_OUT_SYNC";
            case "PDM" -> "DOCUMENT_RELEASE";
            case "CAD" -> "DRAWING_PUBLISH";
            default -> "PLM_SYNC";
        });
        payload.put("jobType", jobType);

        switch (normalized) {
            case "ERP" -> {
                payload.put("summary", "同步主数据对象、基线摘要与实施状态到 ERP。");
                payload.put("masterDataObjects", filterObjectsByType(objectLinks, List.of("PART", "MATERIAL", "PROCESS")));
                payload.put("baselineReleases", baselines);
                payload.put("syncTasks", filterTasksByType(implementation.tasks(), List.of("SYNC", "DATA_CHANGE", "CONFIRM")));
            }
            case "MES" -> {
                payload.put("summary", "同步实施任务、BOM 结构变化和生效条件到 MES。");
                payload.put("rolloutTasks", filterTasksByType(implementation.tasks(), List.of("ROLLOUT", "IMPLEMENTATION", "VALIDATION")));
                payload.put("bomChanges", bomHighlights);
                payload.put("effectivity", collectEffectivity(bomHighlights, baselines));
            }
            case "PDM" -> {
                payload.put("summary", "同步图纸文档与配置基线到 PDM。");
                payload.put("documentReleases", documentAssets);
                payload.put("baselinePackages", baselines);
                payload.put("controlledObjects", filterObjectsByType(objectLinks, List.of("DOCUMENT", "DRAWING", "BOM")));
            }
            case "CAD" -> {
                payload.put("summary", "发布图档与修订信息到 CAD 图档中心。");
                payload.put("drawingPackages", filterDocumentsByType(documentAssets, List.of("DRAWING", "CAD")));
                payload.put("releasedBaselines", baselines);
                payload.put("revisionTargets", filterObjectsByType(objectLinks, List.of("DRAWING", "DOCUMENT", "PART")));
            }
            default -> {
                payload.put("summary", "同步 PLM 变更主数据到外部系统。");
                payload.put("billTitle", bill.title());
                payload.put("objects", objectLinks);
            }
        }
        return payload;
    }

    private BillSnapshot loadBillSnapshot(String businessType, String billId) {
        return switch (normalizeBusinessType(businessType)) {
            case "PLM_ECR" -> jdbcTemplate.queryForObject(
                    """
                    SELECT bill_no, change_title AS title, status, scene_code, creator_user_id, NULL AS effective_date
                    FROM plm_ecr_change
                    WHERE id = ?
                    """,
                    (rs, rowNum) -> billSnapshot(rs),
                    billId
            );
            case "PLM_ECO" -> jdbcTemplate.queryForObject(
                    """
                    SELECT bill_no, execution_title AS title, status, scene_code, creator_user_id, effective_date
                    FROM plm_eco_execution
                    WHERE id = ?
                    """,
                    (rs, rowNum) -> billSnapshot(rs),
                    billId
            );
            case "PLM_MATERIAL" -> jdbcTemplate.queryForObject(
                    """
                    SELECT bill_no, material_name AS title, status, scene_code, creator_user_id, NULL AS effective_date
                    FROM plm_material_change
                    WHERE id = ?
                    """,
                    (rs, rowNum) -> billSnapshot(rs),
                    billId
            );
            default -> new BillSnapshot(billId, billId, "UNKNOWN", "default", null, null);
        };
    }

    private BillSnapshot billSnapshot(ResultSet rs) throws SQLException {
        return new BillSnapshot(
                rs.getString("bill_no"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("scene_code"),
                rs.getString("creator_user_id"),
                rs.getObject("effective_date", LocalDate.class)
        );
    }

    private List<Map<String, Object>> loadObjectLinks(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT master.object_code,
                       master.object_name,
                       master.object_type,
                       link.role_code,
                       link.change_action,
                       link.before_revision_code,
                       link.after_revision_code,
                       master.source_system
                FROM plm_bill_object_link link
                JOIN plm_object_master master
                  ON master.id = link.object_id
                WHERE link.business_type = ?
                  AND link.bill_id = ?
                ORDER BY link.sort_order ASC, link.created_at ASC
                LIMIT 6
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("objectCode", rs.getString("object_code"));
                    row.put("objectName", rs.getString("object_name"));
                    row.put("objectType", rs.getString("object_type"));
                    row.put("roleCode", rs.getString("role_code"));
                    row.put("changeAction", rs.getString("change_action"));
                    putIfHasText(row, "beforeRevisionCode", rs.getString("before_revision_code"));
                    putIfHasText(row, "afterRevisionCode", rs.getString("after_revision_code"));
                    putIfHasText(row, "sourceSystem", rs.getString("source_system"));
                    return row;
                },
                businessType,
                billId
        );
    }

    private List<Map<String, Object>> loadBomHighlights(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT node_code,
                       node_name,
                       node_type,
                       quantity,
                       unit,
                       effectivity,
                       change_action,
                       hierarchy_level
                FROM plm_bom_node
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY hierarchy_level ASC, sort_order ASC, created_at ASC
                LIMIT 6
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nodeCode", rs.getString("node_code"));
                    row.put("nodeName", rs.getString("node_name"));
                    row.put("nodeType", rs.getString("node_type"));
                    putIfNotNull(row, "quantity", rs.getBigDecimal("quantity"));
                    putIfHasText(row, "unit", rs.getString("unit"));
                    putIfHasText(row, "effectivity", rs.getString("effectivity"));
                    putIfHasText(row, "changeAction", rs.getString("change_action"));
                    putIfNotNull(row, "hierarchyLevel", rs.getInt("hierarchy_level"));
                    return row;
                },
                businessType,
                billId
        );
    }

    private List<Map<String, Object>> loadDocumentAssets(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT document_code,
                       document_name,
                       document_type,
                       version_label,
                       vault_state,
                       source_system,
                       external_ref,
                       change_action
                FROM plm_document_asset
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                LIMIT 6
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("documentCode", rs.getString("document_code"));
                    row.put("documentName", rs.getString("document_name"));
                    row.put("documentType", rs.getString("document_type"));
                    putIfHasText(row, "versionLabel", rs.getString("version_label"));
                    putIfHasText(row, "vaultState", rs.getString("vault_state"));
                    putIfHasText(row, "sourceSystem", rs.getString("source_system"));
                    putIfHasText(row, "externalRef", rs.getString("external_ref"));
                    putIfHasText(row, "changeAction", rs.getString("change_action"));
                    return row;
                },
                businessType,
                billId
        );
    }

    private List<Map<String, Object>> loadBaselines(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT baseline.id,
                       baseline.baseline_code,
                       baseline.baseline_name,
                       baseline.baseline_type,
                       baseline.status,
                       baseline.released_at,
                       COUNT(item.id) AS item_count
                FROM plm_configuration_baseline baseline
                LEFT JOIN plm_configuration_baseline_item item
                  ON item.baseline_id = baseline.id
                WHERE baseline.business_type = ?
                  AND baseline.bill_id = ?
                GROUP BY baseline.id, baseline.baseline_code, baseline.baseline_name,
                         baseline.baseline_type, baseline.status, baseline.released_at
                ORDER BY baseline.created_at ASC
                LIMIT 4
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("baselineId", rs.getString("id"));
                    row.put("baselineCode", rs.getString("baseline_code"));
                    row.put("baselineName", rs.getString("baseline_name"));
                    row.put("baselineType", rs.getString("baseline_type"));
                    row.put("status", rs.getString("status"));
                    putIfNotNull(row, "itemCount", rs.getInt("item_count"));
                    Timestamp releasedAt = rs.getTimestamp("released_at");
                    if (releasedAt != null) {
                        row.put("releasedAt", releasedAt.toLocalDateTime().toString());
                    }
                    return row;
                },
                businessType,
                billId
        );
    }

    private ImplementationSnapshot loadImplementationSnapshot(String businessType, String billId) {
        List<Map<String, Object>> tasks = jdbcTemplate.query(
                """
                SELECT task_no,
                       task_title,
                       task_type,
                       status,
                       owner_user_id,
                       required_evidence_count,
                       verification_required,
                       planned_end_at
                FROM plm_implementation_task
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                LIMIT 6
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("taskNo", rs.getString("task_no"));
                    row.put("taskTitle", rs.getString("task_title"));
                    putIfHasText(row, "taskType", rs.getString("task_type"));
                    row.put("status", rs.getString("status"));
                    putIfHasText(row, "ownerUserId", rs.getString("owner_user_id"));
                    putIfNotNull(row, "requiredEvidenceCount", rs.getInt("required_evidence_count"));
                    row.put("verificationRequired", rs.getBoolean("verification_required"));
                    Timestamp plannedEndAt = rs.getTimestamp("planned_end_at");
                    if (plannedEndAt != null) {
                        row.put("plannedEndAt", plannedEndAt.toLocalDateTime().toString());
                    }
                    return row;
                },
                businessType,
                billId
        );

        Integer taskCount = scalarInt(
                "SELECT COUNT(1) FROM plm_implementation_task WHERE business_type = ? AND bill_id = ?",
                businessType,
                billId
        );
        Integer pendingTaskCount = scalarInt(
                "SELECT COUNT(1) FROM plm_implementation_task WHERE business_type = ? AND bill_id = ? AND status IN ('PENDING', 'RUNNING')",
                businessType,
                billId
        );
        Integer blockedTaskCount = scalarInt(
                "SELECT COUNT(1) FROM plm_implementation_task WHERE business_type = ? AND bill_id = ? AND status = 'BLOCKED'",
                businessType,
                billId
        );
        Integer verificationTaskCount = scalarInt(
                "SELECT COUNT(1) FROM plm_implementation_task WHERE business_type = ? AND bill_id = ? AND verification_required = TRUE",
                businessType,
                billId
        );
        Integer requiredEvidenceCount = scalarInt(
                "SELECT COALESCE(SUM(required_evidence_count), 0) FROM plm_implementation_task WHERE business_type = ? AND bill_id = ?",
                businessType,
                billId
        );
        Integer evidenceCount = scalarInt(
                "SELECT COUNT(1) FROM plm_implementation_task_evidence WHERE business_type = ? AND bill_id = ?",
                businessType,
                billId
        );
        Integer acceptancePendingCount = scalarInt(
                "SELECT COUNT(1) FROM plm_acceptance_checklist WHERE business_type = ? AND bill_id = ? AND status <> 'ACCEPTED'",
                businessType,
                billId
        );
        return new ImplementationSnapshot(
                nullSafeInt(taskCount),
                nullSafeInt(pendingTaskCount),
                nullSafeInt(blockedTaskCount),
                nullSafeInt(verificationTaskCount),
                nullSafeInt(requiredEvidenceCount),
                nullSafeInt(evidenceCount),
                nullSafeInt(acceptancePendingCount),
                tasks
        );
    }

    private Integer scalarInt(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private List<Map<String, Object>> filterObjectsByType(List<Map<String, Object>> objects, List<String> types) {
        return objects.stream()
                .filter(item -> types.contains(String.valueOf(item.get("objectType"))))
                .toList();
    }

    private List<Map<String, Object>> filterDocumentsByType(List<Map<String, Object>> documents, List<String> types) {
        return documents.stream()
                .filter(item -> types.contains(String.valueOf(item.get("documentType"))))
                .toList();
    }

    private List<Map<String, Object>> filterTasksByType(List<Map<String, Object>> tasks, List<String> types) {
        return tasks.stream()
                .filter(item -> types.contains(String.valueOf(item.get("taskType"))))
                .toList();
    }

    private List<String> collectEffectivity(List<Map<String, Object>> bomHighlights, List<Map<String, Object>> baselines) {
        List<String> values = new ArrayList<>();
        for (Map<String, Object> bom : bomHighlights) {
            Object value = bom.get("effectivity");
            if (value instanceof String text && hasText(text) && !values.contains(text)) {
                values.add(text);
            }
        }
        for (Map<String, Object> baseline : baselines) {
            Object value = baseline.get("status");
            if (value instanceof String text && hasText(text) && !values.contains(text)) {
                values.add(text);
            }
        }
        return values;
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase();
    }

    private String normalizeSystemCode(String systemCode) {
        return systemCode == null ? "" : systemCode.trim().toUpperCase();
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record BillSnapshot(
            String billNo,
            String title,
            String status,
            String sceneCode,
            String creatorUserId,
            LocalDate effectiveDate
    ) {
    }

    private record ImplementationSnapshot(
            int taskCount,
            int pendingTaskCount,
            int blockedTaskCount,
            int verificationTaskCount,
            int requiredEvidenceCount,
            int evidenceCount,
            int acceptancePendingCount,
            List<Map<String, Object>> tasks
    ) {
    }
}
