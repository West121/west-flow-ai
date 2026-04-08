package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.identity.mapper.IdentityAccessMapper;
import com.westflow.plm.api.PlmAffectedItemRequest;
import com.westflow.plm.api.PlmBomNodeResponse;
import com.westflow.plm.api.PlmConfigurationBaselineItemResponse;
import com.westflow.plm.api.PlmConfigurationBaselineResponse;
import com.westflow.plm.api.PlmDashboardCockpitResponse;
import com.westflow.plm.api.PlmDashboardDistributionResponse;
import com.westflow.plm.api.PlmCloseBlockerItemResponse;
import com.westflow.plm.api.PlmDomainAclResponse;
import com.westflow.plm.api.PlmDocumentAssetResponse;
import com.westflow.plm.api.PlmExternalIntegrationResponse;
import com.westflow.plm.api.PlmExternalSyncEventEnvelopeResponse;
import com.westflow.plm.api.PlmExternalSyncEventResponse;
import com.westflow.plm.api.PlmFailedSystemHotspotResponse;
import com.westflow.plm.api.PlmObjectAclResponse;
import com.westflow.plm.api.PlmPermissionSummaryResponse;
import com.westflow.plm.api.PlmRoleAssignmentResponse;
import com.westflow.plm.api.PlmStuckSyncItemResponse;
import com.westflow.plm.mapper.PlmObjectMasterMapper;
import com.westflow.plm.model.PlmBillObjectLinkRecord;
import com.westflow.plm.model.PlmObjectMasterRecord;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM v5 企业对象深度层：BOM、文档、配置基线与对象 ACL。
 */
@Service
@RequiredArgsConstructor
public class PlmEnterpriseDepthService {

    public static final String PERMISSION_BILL_READ = "PLM_BILL_READ";
    public static final String PERMISSION_BILL_MANAGE = "PLM_BILL_MANAGE";
    public static final String PERMISSION_OBJECT_READ = "PLM_OBJECT_READ";
    public static final String PERMISSION_OBJECT_CHANGE = "PLM_OBJECT_CHANGE";
    public static final String PERMISSION_BASELINE_READ = "PLM_BASELINE_READ";
    public static final String PERMISSION_TASK_OPERATE = "PLM_TASK_OPERATE";
    public static final String PERMISSION_ACL_ADMIN = "PLM_ACL_ADMIN";

    private static final String BILL_SCOPE_SQL = """
            SELECT 'PLM_ECR' AS business_type,
                   id AS bill_id,
                   bill_no,
                   change_title AS business_title,
                   creator_user_id,
                   status
            FROM plm_ecr_change
            UNION ALL
            SELECT 'PLM_ECO' AS business_type,
                   id AS bill_id,
                   bill_no,
                   execution_title AS business_title,
                   creator_user_id,
                   status
            FROM plm_eco_execution
            UNION ALL
            SELECT 'PLM_MATERIAL' AS business_type,
                   id AS bill_id,
                   bill_no,
                   material_name AS business_title,
                   creator_user_id,
                   status
            FROM plm_material_change
            """;

    private static final List<String> CREATOR_GRANTED_PERMISSIONS = List.of(
            PERMISSION_BILL_READ,
            PERMISSION_BILL_MANAGE,
            PERMISSION_OBJECT_READ,
            PERMISSION_OBJECT_CHANGE,
            PERMISSION_BASELINE_READ,
            PERMISSION_TASK_OPERATE,
            PERMISSION_ACL_ADMIN
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlmObjectMasterMapper plmObjectMasterMapper;
    private final IdentityAccessMapper identityAccessMapper;

    @Transactional
    public void syncBillEnterpriseDepth(
            String businessType,
            String billId,
            List<PlmAffectedItemRequest> affectedItems,
            List<PlmBillObjectLinkRecord> objectLinks,
            String currentUserId
    ) {
        deleteByBill("plm_bom_node", businessType, billId);
        deleteByBill("plm_document_asset", businessType, billId);
        deleteByBill("plm_configuration_baseline_item", businessType, billId, "baseline_id IN (SELECT id FROM plm_configuration_baseline WHERE business_type = ? AND bill_id = ?)");
        deleteByBill("plm_configuration_baseline", businessType, billId);
        deleteByBill("plm_object_acl", businessType, billId);
        deleteByBill("plm_domain_acl", businessType, billId);
        deleteByBill("plm_role_assignment", businessType, billId);
        deleteByBill("plm_external_sync_event", businessType, billId, "integration_id IN (SELECT id FROM plm_external_integration_record WHERE business_type = ? AND bill_id = ?)");
        deleteByBill("plm_external_integration_record", businessType, billId);

        if (affectedItems == null || affectedItems.isEmpty() || objectLinks == null || objectLinks.isEmpty()) {
            return;
        }

        List<LinkedObject> linkedObjects = new ArrayList<>();
        for (int index = 0; index < objectLinks.size(); index++) {
            PlmBillObjectLinkRecord link = objectLinks.get(index);
            PlmAffectedItemRequest item = index < affectedItems.size() ? affectedItems.get(index) : null;
            PlmObjectMasterRecord master = plmObjectMasterMapper.selectById(link.objectId());
            if (master == null) {
                continue;
            }
            linkedObjects.add(new LinkedObject(link, master, item));
        }

        syncBomNodes(businessType, billId, linkedObjects);
        syncDocumentAssets(businessType, billId, linkedObjects);
        syncConfigurationBaselines(businessType, billId, linkedObjects);
        syncObjectAcl(businessType, billId, linkedObjects, currentUserId);
        syncDomainAcl(businessType, billId, linkedObjects);
        syncRoleAssignments(businessType, billId, linkedObjects, currentUserId);
        syncExternalIntegrations(businessType, billId, linkedObjects);
    }

    public List<PlmBomNodeResponse> listBillBomNodes(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       parent_node_id,
                       object_id,
                       node_code,
                       node_name,
                       node_type,
                       quantity,
                       unit,
                       effectivity,
                       change_action,
                       hierarchy_level,
                       sort_order
                FROM plm_bom_node
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY hierarchy_level ASC, sort_order ASC, created_at ASC
                """,
                bomNodeMapper(),
                businessType,
                billId
        );
    }

    public List<PlmDocumentAssetResponse> listBillDocumentAssets(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       object_id,
                       document_code,
                       document_name,
                       document_type,
                       version_label,
                       vault_state,
                       file_name,
                       file_type,
                       source_system,
                       external_ref,
                       change_action,
                       sort_order
                FROM plm_document_asset
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """,
                documentAssetMapper(),
                businessType,
                billId
        );
    }

    public List<PlmConfigurationBaselineResponse> listBillBaselines(String businessType, String billId) {
        List<BaselineRecord> baselines = jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       baseline_code,
                       baseline_name,
                       baseline_type,
                       status,
                       released_at,
                       summary_json
                FROM plm_configuration_baseline
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new BaselineRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("baseline_code"),
                        rs.getString("baseline_name"),
                        rs.getString("baseline_type"),
                        rs.getString("status"),
                        rs.getTimestamp("released_at") == null ? null : rs.getTimestamp("released_at").toLocalDateTime(),
                        rs.getString("summary_json")
                ),
                businessType,
                billId
        );
        List<PlmConfigurationBaselineResponse> responses = new ArrayList<>();
        for (BaselineRecord baseline : baselines) {
            List<PlmConfigurationBaselineItemResponse> items = jdbcTemplate.query(
                    """
                    SELECT id,
                           object_id,
                           object_code,
                           object_name,
                           object_type,
                           before_revision_code,
                           after_revision_code,
                           effectivity,
                           sort_order
                    FROM plm_configuration_baseline_item
                    WHERE baseline_id = ?
                    ORDER BY sort_order ASC, created_at ASC
                    """,
                    (rs, rowNum) -> new PlmConfigurationBaselineItemResponse(
                            rs.getString("id"),
                            rs.getString("object_id"),
                            rs.getString("object_code"),
                            rs.getString("object_name"),
                            rs.getString("object_type"),
                            rs.getString("before_revision_code"),
                            rs.getString("after_revision_code"),
                            rs.getString("effectivity"),
                            rs.getInt("sort_order")
                    ),
                    baseline.id()
            );
            responses.add(new PlmConfigurationBaselineResponse(
                    baseline.id(),
                    baseline.businessType(),
                    baseline.billId(),
                    baseline.baselineCode(),
                    baseline.baselineName(),
                    baseline.baselineType(),
                    baseline.status(),
                    baseline.releasedAt(),
                    baseline.summaryJson(),
                    items
            ));
        }
        return responses;
    }

    public List<PlmObjectAclResponse> listBillObjectAcl(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT acl.id,
                       acl.business_type,
                       acl.bill_id,
                       acl.object_id,
                       master.object_code,
                       master.object_name,
                       acl.subject_type,
                       acl.subject_code,
                       acl.permission_code,
                       acl.access_scope,
                       acl.inherited,
                       acl.sort_order
                FROM plm_object_acl acl
                LEFT JOIN plm_object_master master
                  ON master.id = acl.object_id
                WHERE acl.business_type = ?
                  AND acl.bill_id = ?
                ORDER BY acl.sort_order ASC, acl.created_at ASC
                """,
                (rs, rowNum) -> new PlmObjectAclResponse(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("object_id"),
                        rs.getString("object_code"),
                        rs.getString("object_name"),
                        rs.getString("subject_type"),
                        rs.getString("subject_code"),
                        rs.getString("permission_code"),
                        rs.getString("access_scope"),
                        rs.getBoolean("inherited"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
    }

    public List<PlmDomainAclResponse> listBillDomainAcl(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       domain_code,
                       role_code,
                       permission_code,
                       access_scope,
                       policy_source,
                       sort_order
                FROM plm_domain_acl
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new PlmDomainAclResponse(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("domain_code"),
                        rs.getString("role_code"),
                        rs.getString("permission_code"),
                        rs.getString("access_scope"),
                        rs.getString("policy_source"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
    }

    public List<PlmRoleAssignmentResponse> listBillRoleAssignments(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       role_code,
                       role_label,
                       assignee_user_id,
                       assignee_display_name,
                       assignment_scope,
                       required_flag,
                       status,
                       sort_order
                FROM plm_role_assignment
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new PlmRoleAssignmentResponse(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("role_code"),
                        rs.getString("role_label"),
                        rs.getString("assignee_user_id"),
                        rs.getString("assignee_display_name"),
                        rs.getString("assignment_scope"),
                        rs.getBoolean("required_flag"),
                        rs.getString("status"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
    }

    public PlmPermissionSummaryResponse permissionSummary(
            String businessType,
            String billId,
            String creatorUserId,
            String currentUserId
    ) {
        List<String> currentRoleCodes = safeRoleCodes(currentUserId);
        Set<String> matchedRoleCodes = new LinkedHashSet<>();
        Set<String> grantedPermissionCodes = new LinkedHashSet<>();
        Set<String> domainCodes = new LinkedHashSet<>();
        if (Objects.equals(creatorUserId, currentUserId)) {
            matchedRoleCodes.add("PLM_CREATOR");
            grantedPermissionCodes.addAll(CREATOR_GRANTED_PERMISSIONS);
        }

        for (PlmDomainAclResponse row : listBillDomainAcl(businessType, billId)) {
            domainCodes.add(row.domainCode());
            if (currentRoleCodes.contains(row.roleCode())) {
                matchedRoleCodes.add(row.roleCode());
                grantedPermissionCodes.addAll(expandDomainPermission(row.permissionCode(), row.roleCode()));
            }
        }

        for (PlmObjectAclResponse row : listBillObjectAcl(businessType, billId)) {
            if ("ROLE".equalsIgnoreCase(row.subjectType()) && currentRoleCodes.contains(row.subjectCode())) {
                matchedRoleCodes.add(row.subjectCode());
                grantedPermissionCodes.addAll(expandObjectPermission(row.permissionCode()));
            }
            if ("USER".equalsIgnoreCase(row.subjectType()) && Objects.equals(currentUserId, row.subjectCode())) {
                grantedPermissionCodes.addAll(expandObjectPermission(row.permissionCode()));
            }
        }

        boolean canReadBill = grantedPermissionCodes.contains(PERMISSION_BILL_READ)
                || grantedPermissionCodes.contains(PERMISSION_BILL_MANAGE);
        boolean canManageBill = grantedPermissionCodes.contains(PERMISSION_BILL_MANAGE);
        boolean canReadObjects = grantedPermissionCodes.contains(PERMISSION_OBJECT_READ)
                || grantedPermissionCodes.contains(PERMISSION_OBJECT_CHANGE);
        boolean canChangeObjects = grantedPermissionCodes.contains(PERMISSION_OBJECT_CHANGE);
        boolean canReadBaselines = grantedPermissionCodes.contains(PERMISSION_BASELINE_READ)
                || canReadObjects;
        boolean canOperateImplementation = grantedPermissionCodes.contains(PERMISSION_TASK_OPERATE)
                || canManageBill;
        boolean canAdminAcl = grantedPermissionCodes.contains(PERMISSION_ACL_ADMIN);

        return new PlmPermissionSummaryResponse(
                businessType,
                billId,
                currentUserId,
                currentRoleCodes,
                List.copyOf(matchedRoleCodes),
                List.copyOf(domainCodes),
                List.copyOf(grantedPermissionCodes),
                canReadBill,
                canManageBill,
                canReadObjects,
                canChangeObjects,
                canReadBaselines,
                canOperateImplementation,
                canAdminAcl
        );
    }

    public void assertBillReadable(String businessType, String billId, String creatorUserId, String currentUserId) {
        if (!permissionSummary(businessType, billId, creatorUserId, currentUserId).canReadBill()) {
            throw forbidden(billId, "当前用户没有查看该 PLM 单据的权限");
        }
    }

    public void assertBillManageable(String businessType, String billId, String creatorUserId, String currentUserId) {
        if (!permissionSummary(businessType, billId, creatorUserId, currentUserId).canManageBill()) {
            throw forbidden(billId, "当前用户没有管理该 PLM 单据的权限");
        }
    }

    public List<PlmExternalIntegrationResponse> listBillExternalIntegrations(String businessType, String billId) {
        List<ExternalIntegrationRecord> integrations = jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       object_id,
                       system_code,
                       system_name,
                       direction_code,
                       integration_type,
                       status,
                       endpoint_key,
                       external_ref,
                       last_sync_at,
                       message,
                       sort_order
                FROM plm_external_integration_record
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new ExternalIntegrationRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("object_id"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("integration_type"),
                        rs.getString("status"),
                        rs.getString("endpoint_key"),
                        rs.getString("external_ref"),
                        rs.getTimestamp("last_sync_at") == null ? null : rs.getTimestamp("last_sync_at").toLocalDateTime(),
                        rs.getString("message"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
        List<PlmExternalIntegrationResponse> responses = new ArrayList<>();
        for (ExternalIntegrationRecord integration : integrations) {
            List<PlmExternalSyncEventResponse> events = jdbcTemplate.query(
                    """
                    SELECT id,
                           integration_id,
                           event_type,
                           status,
                           payload_json,
                           error_message,
                           happened_at,
                           sort_order
                    FROM plm_external_sync_event
                    WHERE integration_id = ?
                    ORDER BY sort_order ASC, happened_at DESC
                    """,
                    (rs, rowNum) -> new PlmExternalSyncEventResponse(
                            rs.getString("id"),
                            rs.getString("integration_id"),
                            rs.getString("event_type"),
                            rs.getString("status"),
                            rs.getString("payload_json"),
                            rs.getString("error_message"),
                            rs.getTimestamp("happened_at").toLocalDateTime(),
                            rs.getInt("sort_order")
                    ),
                    integration.id()
            );
            responses.add(new PlmExternalIntegrationResponse(
                    integration.id(),
                    integration.businessType(),
                    integration.billId(),
                    integration.objectId(),
                    integration.systemCode(),
                    integration.systemName(),
                    integration.directionCode(),
                    integration.integrationType(),
                    integration.status(),
                    integration.endpointKey(),
                    integration.externalRef(),
                    integration.lastSyncAt(),
                    integration.message(),
                    integration.sortOrder(),
                    events
            ));
        }
        return responses;
    }

    public List<PlmExternalSyncEventEnvelopeResponse> listBillExternalSyncEvents(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT event.id,
                       event.integration_id,
                       integration.business_type,
                       integration.bill_id,
                       integration.system_code,
                       integration.system_name,
                       integration.direction_code,
                       event.event_type,
                       event.status,
                       event.payload_json,
                       event.error_message,
                       event.happened_at,
                       event.sort_order
                FROM plm_external_sync_event event
                JOIN plm_external_integration_record integration
                  ON integration.id = event.integration_id
                WHERE integration.business_type = ?
                  AND integration.bill_id = ?
                ORDER BY event.happened_at DESC, event.sort_order ASC, event.created_at DESC
                """,
                (rs, rowNum) -> new PlmExternalSyncEventEnvelopeResponse(
                        rs.getString("id"),
                        rs.getString("integration_id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("event_type"),
                        rs.getString("status"),
                        rs.getString("payload_json"),
                        rs.getString("error_message"),
                        rs.getTimestamp("happened_at").toLocalDateTime(),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
    }

    @Transactional
    public void appendLifecycleSyncEvents(
            String businessType,
            String billId,
            String eventType,
            String eventStatus,
            String message,
            String triggeredBy
    ) {
        List<ExternalIntegrationRecord> integrations = jdbcTemplate.query(
                """
                SELECT id,
                       business_type,
                       bill_id,
                       object_id,
                       system_code,
                       system_name,
                       direction_code,
                       integration_type,
                       status,
                       endpoint_key,
                       external_ref,
                       last_sync_at,
                       message,
                       sort_order
                FROM plm_external_integration_record
                WHERE business_type = ?
                  AND bill_id = ?
                  AND direction_code = 'DOWNSTREAM'
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new ExternalIntegrationRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("object_id"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("integration_type"),
                        rs.getString("status"),
                        rs.getString("endpoint_key"),
                        rs.getString("external_ref"),
                        rs.getTimestamp("last_sync_at") == null ? null : rs.getTimestamp("last_sync_at").toLocalDateTime(),
                        rs.getString("message"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
        if (integrations.isEmpty()) {
            return;
        }

        LocalDateTime happenedAt = LocalDateTime.now();
        int sortOrder = 100;
        for (ExternalIntegrationRecord integration : integrations) {
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_external_sync_event (
                        id, integration_id, event_type, status, payload_json, error_message,
                        happened_at, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("evt"),
                    integration.id(),
                    eventType,
                    eventStatus,
                    toJson(Map.of(
                            "businessType", businessType,
                            "billId", billId,
                            "systemCode", integration.systemCode(),
                            "systemName", integration.systemName(),
                            "message", message,
                            "triggeredBy", triggeredBy
                    )),
                    "FAILED".equalsIgnoreCase(eventStatus) ? message : null,
                    Timestamp.valueOf(happenedAt),
                    sortOrder++
            );
            jdbcTemplate.update(
                    """
                    UPDATE plm_external_integration_record
                    SET status = ?,
                        last_sync_at = ?,
                        message = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    rollupIntegrationStatus(eventStatus),
                    Timestamp.valueOf(happenedAt),
                    message,
                    integration.id()
            );
        }
    }

    public PlmDashboardCockpitResponse dashboardCockpit(String userId) {
        List<PlmDashboardDistributionResponse> objectTypeDistribution = queryDistribution(
                """
                SELECT master.object_type AS code, COUNT(*) AS total_count
                FROM plm_bill_object_link link
                JOIN plm_object_master master ON master.id = link.object_id
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = link.business_type
                 AND bills.bill_id = link.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY master.object_type
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "PART", "零部件",
                        "BOM", "BOM",
                        "DOCUMENT", "文档",
                        "DRAWING", "图纸",
                        "MATERIAL", "物料",
                        "PROCESS", "工艺"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> domainDistribution = queryDistribution(
                """
                SELECT scene_code AS code, COUNT(*) AS total_count
                FROM (
                    SELECT scene_code, creator_user_id FROM plm_ecr_change
                    UNION ALL
                    SELECT scene_code, creator_user_id FROM plm_eco_execution
                    UNION ALL
                    SELECT scene_code, creator_user_id FROM plm_material_change
                ) bills
                WHERE creator_user_id = ?
                GROUP BY scene_code
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "default", "标准场景",
                        "pilot", "试点场景"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> baselineStatusDistribution = queryDistribution(
                """
                SELECT baseline.status AS code, COUNT(*) AS total_count
                FROM plm_configuration_baseline baseline
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = baseline.business_type
                 AND bills.bill_id = baseline.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY baseline.status
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "DRAFT", "草稿",
                        "ACTIVE", "生效中",
                        "SUPERSEDED", "已替代"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> integrationSystemDistribution = queryDistribution(
                """
                SELECT integration.system_code AS code, COUNT(*) AS total_count
                FROM plm_external_integration_record integration
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY integration.system_code
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "ERP", "ERP",
                        "MES", "MES",
                        "PDM", "PDM",
                        "CAD", "CAD",
                        "WEST_FLOW", "平台主数据"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> integrationStatusDistribution = queryDistribution(
                """
                SELECT integration.status AS code, COUNT(*) AS total_count
                FROM plm_external_integration_record integration
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY integration.status
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "PENDING", "待同步",
                        "SYNCED", "已同步",
                        "BLOCKED", "阻塞",
                        "FAILED", "失败"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> connectorStatusDistribution = queryDistribution(
                """
                SELECT job.status AS code, COUNT(*) AS total_count
                FROM plm_connector_job job
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = job.business_type
                 AND bills.bill_id = job.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY job.status
                ORDER BY total_count DESC, code ASC
                """,
                Map.of(
                        "PENDING", "待派发",
                        "RETRY_PENDING", "待重试",
                        "DISPATCHED", "已派发待回执",
                        "ACK_PENDING", "待确认",
                        "ACKED", "已确认",
                        "FAILED", "失败",
                        "CANCELLED", "已取消"
                ),
                userId
        );
        List<PlmDashboardDistributionResponse> implementationHealthDistribution = queryImplementationHealthDistribution(userId);
        List<PlmStuckSyncItemResponse> stuckSyncItems = queryStuckSyncItems(userId);
        List<PlmCloseBlockerItemResponse> closeBlockerItems = queryCloseBlockerItems(userId);
        List<PlmFailedSystemHotspotResponse> failedSystemHotspots = queryFailedSystemHotspots(userId);
        long blockedTaskCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_implementation_task task
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = task.business_type
                 AND bills.bill_id = task.bill_id
                WHERE bills.creator_user_id = ?
                  AND task.status = 'BLOCKED'
                """,
                userId
        );
        long overdueTaskCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_implementation_task task
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = task.business_type
                 AND bills.bill_id = task.bill_id
                WHERE bills.creator_user_id = ?
                  AND task.planned_end_at IS NOT NULL
                  AND task.planned_end_at < CURRENT_TIMESTAMP
                  AND task.status NOT IN ('COMPLETED', 'CANCELLED')
                """,
                userId
        );
        long readyToCloseCount = queryLong(
                """
                SELECT COUNT(*)
                FROM (
                """ + BILL_SCOPE_SQL + """
                ) bills
                WHERE creator_user_id = ?
                  AND status = 'VALIDATING'
                """,
                userId
        );
        long pendingIntegrationCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_external_integration_record integration
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                WHERE bills.creator_user_id = ?
                  AND integration.status IN ('PENDING', 'BLOCKED')
                """,
                userId
        );
        long failedSyncEventCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_external_sync_event event
                JOIN plm_external_integration_record integration ON integration.id = event.integration_id
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                WHERE bills.creator_user_id = ?
                  AND event.status = 'FAILED'
                """,
                userId
        );
        long connectorTaskBacklogCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_connector_job job
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = job.business_type
                 AND bills.bill_id = job.bill_id
                WHERE bills.creator_user_id = ?
                  AND job.status IN ('PENDING', 'RETRY_PENDING', 'FAILED')
                """,
                userId
        );
        long pendingReceiptCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_connector_job job
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = job.business_type
                 AND bills.bill_id = job.bill_id
                WHERE bills.creator_user_id = ?
                  AND job.status IN ('DISPATCHED', 'ACK_PENDING')
                """,
                userId
        );
        long acceptanceDueCount = queryLong(
                """
                SELECT COUNT(*)
                FROM plm_acceptance_checklist checklist
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = checklist.business_type
                 AND bills.bill_id = checklist.bill_id
                WHERE bills.creator_user_id = ?
                  AND checklist.status IN ('PENDING', 'FAILED')
                """,
                userId
        );
        double roleCoverageRate = queryRoleCoverageRate(userId);
        double averageClosureHours = queryAverageClosureHours(userId);
        long activeImplementationScope = blockedTaskCount + overdueTaskCount
                + distributionTotal(implementationHealthDistribution, "HEALTHY")
                + distributionTotal(implementationHealthDistribution, "AT_RISK");
        double implementationHealthyRate = activeImplementationScope > 0
                ? roundRate(distributionTotal(implementationHealthDistribution, "HEALTHY"), activeImplementationScope)
                : 100D;
        return new PlmDashboardCockpitResponse(
                stuckSyncItems,
                closeBlockerItems,
                failedSystemHotspots,
                objectTypeDistribution,
                domainDistribution,
                baselineStatusDistribution,
                integrationSystemDistribution,
                integrationStatusDistribution,
                connectorStatusDistribution,
                implementationHealthDistribution,
                blockedTaskCount,
                overdueTaskCount,
                readyToCloseCount,
                pendingIntegrationCount,
                failedSyncEventCount,
                connectorTaskBacklogCount,
                pendingReceiptCount,
                acceptanceDueCount,
                roleCoverageRate,
                averageClosureHours,
                implementationHealthyRate
        );
    }

    private List<PlmStuckSyncItemResponse> queryStuckSyncItems(String userId) {
        return jdbcTemplate.query(
                """
                SELECT integration.id,
                       integration.bill_id,
                       bills.bill_no,
                       bills.business_type,
                       bills.business_title,
                       integration.system_code,
                       integration.system_name,
                       registry.connector_code,
                       CASE
                           WHEN SUM(CASE WHEN job.status = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                           WHEN SUM(CASE WHEN job.status IN ('DISPATCHED', 'ACK_PENDING') THEN 1 ELSE 0 END) > 0 THEN 'ACK_PENDING'
                           WHEN SUM(CASE WHEN job.status = 'RETRY_PENDING' THEN 1 ELSE 0 END) > 0 THEN 'RETRY_PENDING'
                           ELSE 'PENDING'
                       END AS status,
                       SUM(CASE WHEN job.status IN ('PENDING', 'RETRY_PENDING', 'DISPATCHED', 'ACK_PENDING') THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN job.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                       owner.display_name AS owner_display_name,
                       MAX(COALESCE(job.updated_at, integration.updated_at)) AS updated_at
                FROM plm_external_integration_record integration
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                LEFT JOIN wf_user owner
                  ON owner.id = bills.creator_user_id
                LEFT JOIN plm_connector_registry registry
                  ON registry.system_code = integration.system_code
                 AND registry.direction_code = integration.direction_code
                 AND registry.enabled = TRUE
                LEFT JOIN plm_connector_job job
                  ON job.integration_id = integration.id
                WHERE bills.creator_user_id = ?
                  AND (
                      integration.status IN ('PENDING', 'BLOCKED', 'FAILED')
                      OR job.status IN ('PENDING', 'RETRY_PENDING', 'DISPATCHED', 'ACK_PENDING', 'FAILED')
                  )
                GROUP BY integration.id,
                         integration.bill_id,
                         bills.bill_no,
                         bills.business_type,
                         bills.business_title,
                         integration.system_code,
                         integration.system_name,
                         registry.connector_code,
                         owner.display_name
                ORDER BY failed_count DESC, pending_count DESC, updated_at DESC
                FETCH FIRST 6 ROWS ONLY
                """,
                (rs, rowNum) -> {
                    long pendingCount = rs.getLong("pending_count");
                    long failedCount = rs.getLong("failed_count");
                    String systemName = rs.getString("system_name");
                    String summary = failedCount > 0
                            ? systemName + " 存在 " + failedCount + " 条失败同步，仍有 " + pendingCount + " 条待处理。"
                            : systemName + " 仍有 " + pendingCount + " 条同步/确认任务未完成。";
                    return new PlmStuckSyncItemResponse(
                            rs.getString("id"),
                            rs.getString("bill_id"),
                            rs.getString("bill_no"),
                            rs.getString("business_type"),
                            rs.getString("business_title"),
                            rs.getString("system_code"),
                            systemName,
                            rs.getString("connector_code"),
                            rs.getString("status"),
                            pendingCount,
                            failedCount,
                            rs.getString("owner_display_name"),
                            summary,
                            toLocalDateTime(rs, "updated_at")
                    );
                },
                userId
        );
    }

    private List<PlmCloseBlockerItemResponse> queryCloseBlockerItems(String userId) {
        List<PlmCloseBlockerItemResponse> result = new ArrayList<>();
        result.addAll(jdbcTemplate.query(
                """
                SELECT bills.bill_id,
                       bills.bill_no,
                       bills.business_type,
                       bills.business_title,
                       owner.display_name AS owner_display_name,
                       COUNT(*) AS blocker_count,
                       MIN(task.planned_end_at) AS due_at
                FROM plm_implementation_task task
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = task.business_type
                 AND bills.bill_id = task.bill_id
                LEFT JOIN wf_user owner
                  ON owner.id = bills.creator_user_id
                WHERE bills.creator_user_id = ?
                  AND task.status = 'BLOCKED'
                GROUP BY bills.bill_id, bills.bill_no, bills.business_type, bills.business_title, owner.display_name
                """,
                (rs, rowNum) -> new PlmCloseBlockerItemResponse(
                        buildId("blk"),
                        rs.getString("bill_id"),
                        rs.getString("bill_no"),
                        rs.getString("business_type"),
                        rs.getString("business_title"),
                        "BLOCKED_TASK",
                        "存在阻塞实施任务",
                        rs.getLong("blocker_count"),
                        rs.getString("owner_display_name"),
                        "实施任务仍被阻塞，需先解除阻塞后才可推进关闭。",
                        toLocalDateTime(rs, "due_at")
                ),
                userId
        ));
        result.addAll(jdbcTemplate.query(
                """
                SELECT bills.bill_id,
                       bills.bill_no,
                       bills.business_type,
                       bills.business_title,
                       owner.display_name AS owner_display_name,
                       COUNT(*) AS blocker_count
                FROM plm_acceptance_checklist checklist
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = checklist.business_type
                 AND bills.bill_id = checklist.bill_id
                LEFT JOIN wf_user owner
                  ON owner.id = bills.creator_user_id
                WHERE bills.creator_user_id = ?
                  AND checklist.status IN ('PENDING', 'FAILED')
                GROUP BY bills.bill_id, bills.bill_no, bills.business_type, bills.business_title, owner.display_name
                """,
                (rs, rowNum) -> new PlmCloseBlockerItemResponse(
                        buildId("blk"),
                        rs.getString("bill_id"),
                        rs.getString("bill_no"),
                        rs.getString("business_type"),
                        rs.getString("business_title"),
                        "ACCEPTANCE_PENDING",
                        "验收清单待完成",
                        rs.getLong("blocker_count"),
                        rs.getString("owner_display_name"),
                        "验收清单仍有待确认或失败项，暂不满足关闭条件。",
                        null
                ),
                userId
        ));
        result.addAll(jdbcTemplate.query(
                """
                SELECT bills.bill_id,
                       bills.bill_no,
                       bills.business_type,
                       bills.business_title,
                       owner.display_name AS owner_display_name,
                       COUNT(*) AS blocker_count,
                       MAX(job.last_dispatched_at) AS due_at
                FROM plm_connector_job job
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = job.business_type
                 AND bills.bill_id = job.bill_id
                LEFT JOIN wf_user owner
                  ON owner.id = bills.creator_user_id
                WHERE bills.creator_user_id = ?
                  AND job.status IN ('DISPATCHED', 'ACK_PENDING')
                GROUP BY bills.bill_id, bills.bill_no, bills.business_type, bills.business_title, owner.display_name
                """,
                (rs, rowNum) -> new PlmCloseBlockerItemResponse(
                        buildId("blk"),
                        rs.getString("bill_id"),
                        rs.getString("bill_no"),
                        rs.getString("business_type"),
                        rs.getString("business_title"),
                        "CONNECTOR_RECEIPT_PENDING",
                        "外部回执待确认",
                        rs.getLong("blocker_count"),
                        rs.getString("owner_display_name"),
                        "外部系统回执尚未确认，关闭前需要先补齐集成回执。",
                        toLocalDateTime(rs, "due_at")
                ),
                userId
        ));
        result.sort((left, right) -> {
            LocalDateTime rightDueAt = right.dueAt();
            LocalDateTime leftDueAt = left.dueAt();
            if (rightDueAt == null && leftDueAt == null) {
                return Long.compare(right.blockerCount(), left.blockerCount());
            }
            if (rightDueAt == null) {
                return -1;
            }
            if (leftDueAt == null) {
                return 1;
            }
            int compare = leftDueAt.compareTo(rightDueAt);
            return compare != 0 ? compare : Long.compare(right.blockerCount(), left.blockerCount());
        });
        return result.size() > 6 ? new ArrayList<>(result.subList(0, 6)) : result;
    }

    private List<PlmFailedSystemHotspotResponse> queryFailedSystemHotspots(String userId) {
        return jdbcTemplate.query(
                """
                SELECT integration.system_code,
                       integration.system_name,
                       SUM(CASE WHEN job.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN job.status IN ('PENDING', 'RETRY_PENDING', 'DISPATCHED', 'ACK_PENDING')
                                OR integration.status IN ('PENDING', 'BLOCKED')
                                THEN 1 ELSE 0 END) AS pending_count,
                       COUNT(DISTINCT CASE
                           WHEN job.status = 'FAILED'
                             OR integration.status IN ('FAILED', 'BLOCKED')
                           THEN integration.bill_id
                           ELSE NULL
                       END) AS blocked_bill_count
                FROM plm_external_integration_record integration
                JOIN (
                """ + BILL_SCOPE_SQL + """
                ) bills
                  ON bills.business_type = integration.business_type
                 AND bills.bill_id = integration.bill_id
                LEFT JOIN plm_connector_job job
                  ON job.integration_id = integration.id
                WHERE bills.creator_user_id = ?
                GROUP BY integration.system_code, integration.system_name
                HAVING SUM(CASE WHEN job.status = 'FAILED' THEN 1 ELSE 0 END) > 0
                    OR SUM(CASE WHEN job.status IN ('PENDING', 'RETRY_PENDING', 'DISPATCHED', 'ACK_PENDING')
                                 OR integration.status IN ('PENDING', 'BLOCKED')
                                 THEN 1 ELSE 0 END) > 0
                ORDER BY failed_count DESC, blocked_bill_count DESC, pending_count DESC, integration.system_code ASC
                FETCH FIRST 6 ROWS ONLY
                """,
                (rs, rowNum) -> {
                    long failedCount = rs.getLong("failed_count");
                    long pendingCount = rs.getLong("pending_count");
                    long blockedBillCount = rs.getLong("blocked_bill_count");
                    String systemName = rs.getString("system_name");
                    String summary = systemName + " 当前有 " + failedCount + " 条失败同步、"
                            + pendingCount + " 条待处理任务，影响 " + blockedBillCount + " 张单据。";
                    return new PlmFailedSystemHotspotResponse(
                            rs.getString("system_code"),
                            systemName,
                            failedCount,
                            pendingCount,
                            blockedBillCount,
                            summary
                    );
                },
                userId
        );
    }

    private List<PlmDashboardDistributionResponse> queryImplementationHealthDistribution(String userId) {
        List<HealthBucket> rows = jdbcTemplate.query(
                """
                SELECT
                    CASE
                        WHEN task.status = 'BLOCKED' THEN 'BLOCKED'
                        WHEN task.planned_end_at IS NOT NULL
                             AND task.planned_end_at < CURRENT_TIMESTAMP
                             AND task.status NOT IN ('COMPLETED', 'CANCELLED') THEN 'AT_RISK'
                        WHEN task.status IN ('COMPLETED', 'CANCELLED') THEN 'DONE'
                        ELSE 'HEALTHY'
                    END AS code,
                    COUNT(*) AS total_count
                FROM plm_implementation_task task
                JOIN (
                    SELECT 'PLM_ECR' AS business_type, id AS bill_id, creator_user_id FROM plm_ecr_change
                    UNION ALL
                    SELECT 'PLM_ECO' AS business_type, id AS bill_id, creator_user_id FROM plm_eco_execution
                    UNION ALL
                    SELECT 'PLM_MATERIAL' AS business_type, id AS bill_id, creator_user_id FROM plm_material_change
                ) bills
                  ON bills.business_type = task.business_type
                 AND bills.bill_id = task.bill_id
                WHERE bills.creator_user_id = ?
                GROUP BY
                    CASE
                        WHEN task.status = 'BLOCKED' THEN 'BLOCKED'
                        WHEN task.planned_end_at IS NOT NULL
                             AND task.planned_end_at < CURRENT_TIMESTAMP
                             AND task.status NOT IN ('COMPLETED', 'CANCELLED') THEN 'AT_RISK'
                        WHEN task.status IN ('COMPLETED', 'CANCELLED') THEN 'DONE'
                        ELSE 'HEALTHY'
                    END
                ORDER BY total_count DESC, code ASC
                """,
                (rs, rowNum) -> new HealthBucket(rs.getString("code"), rs.getLong("total_count")),
                userId
        );
        Map<String, String> labels = Map.of(
                "HEALTHY", "健康推进",
                "AT_RISK", "存在延期风险",
                "BLOCKED", "已阻塞",
                "DONE", "已完成"
        );
        List<PlmDashboardDistributionResponse> result = new ArrayList<>();
        for (HealthBucket row : rows) {
            result.add(new PlmDashboardDistributionResponse(row.code(), labels.get(row.code()), row.totalCount()));
        }
        return result;
    }

    private long distributionTotal(List<PlmDashboardDistributionResponse> rows, String code) {
        return rows.stream()
                .filter(item -> Objects.equals(item.code(), code))
                .mapToLong(PlmDashboardDistributionResponse::count)
                .sum();
    }

    private LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws java.sql.SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private double roundRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 10000D) / denominator) / 100D;
    }

    private void syncBomNodes(String businessType, String billId, List<LinkedObject> linkedObjects) {
        if (linkedObjects.isEmpty()) {
            return;
        }
        LinkedObject root = linkedObjects.get(0);
        jdbcTemplate.update(
                """
                INSERT INTO plm_bom_node (
                    id, business_type, bill_id, parent_node_id, object_id, node_code, node_name, node_type,
                    quantity, unit, effectivity, change_action, hierarchy_level, sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                buildId("bom"),
                businessType,
                billId,
                null,
                root.master().id(),
                root.master().objectCode(),
                root.master().objectName(),
                root.master().objectType(),
                1D,
                defaultUnit(root.master().objectType()),
                "主配置",
                root.link().changeAction(),
                0,
                1
        );
        String rootNodeId = jdbcTemplate.queryForObject(
                "SELECT id FROM plm_bom_node WHERE business_type = ? AND bill_id = ? ORDER BY created_at ASC FETCH FIRST 1 ROWS ONLY",
                String.class,
                businessType,
                billId
        );
        for (int index = 1; index < linkedObjects.size(); index++) {
            LinkedObject child = linkedObjects.get(index);
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_bom_node (
                        id, business_type, bill_id, parent_node_id, object_id, node_code, node_name, node_type,
                        quantity, unit, effectivity, change_action, hierarchy_level, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("bom"),
                    businessType,
                    billId,
                    rootNodeId,
                    child.master().id(),
                    child.master().objectCode(),
                    child.master().objectName(),
                    child.master().objectType(),
                    1D + index,
                    defaultUnit(child.master().objectType()),
                    "实施阶段",
                    child.link().changeAction(),
                    1,
                    index + 1
            );
        }
    }

    private void syncDocumentAssets(String businessType, String billId, List<LinkedObject> linkedObjects) {
        int sortOrder = 1;
        for (LinkedObject linked : linkedObjects) {
            String objectType = normalize(linked.master().objectType());
            if (!List.of("DOCUMENT", "DRAWING").contains(objectType)) {
                continue;
            }
            String fileType = "DRAWING".equals(objectType) ? "dwg" : "pdf";
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_document_asset (
                        id, business_type, bill_id, object_id, document_code, document_name, document_type,
                        version_label, vault_state, file_name, file_type, source_system, external_ref, change_action,
                        sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("doc"),
                    businessType,
                    billId,
                    linked.master().id(),
                    linked.master().objectCode(),
                    linked.master().objectName(),
                    objectType,
                    normalize(linked.link().afterRevisionCode(), linked.link().beforeRevisionCode()),
                    "ACTIVE",
                    linked.master().objectCode() + "." + fileType,
                    fileType,
                    normalize(linked.master().sourceSystem(), "WEST_FLOW"),
                    normalize(linked.master().externalRef(), linked.master().objectCode()),
                    linked.link().changeAction(),
                    sortOrder++
            );
        }
    }

    private void syncConfigurationBaselines(String businessType, String billId, List<LinkedObject> linkedObjects) {
        String baselineId = buildId("baseline");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectCount", linkedObjects.size());
        summary.put("revisionCoverage", linkedObjects.stream().filter(item -> item.link().afterRevisionCode() != null || item.link().beforeRevisionCode() != null).count());
        summary.put("scope", "变更配置基线");
        jdbcTemplate.update(
                """
                INSERT INTO plm_configuration_baseline (
                    id, business_type, bill_id, baseline_code, baseline_name, baseline_type, status, released_at,
                    summary_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                baselineId,
                businessType,
                billId,
                billId + "-BASELINE",
                "变更目标基线",
                "TARGET",
                "ACTIVE",
                Timestamp.valueOf(LocalDateTime.now()),
                toJson(summary)
        );
        int sortOrder = 1;
        for (LinkedObject linked : linkedObjects) {
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_configuration_baseline_item (
                        id, baseline_id, object_id, object_code, object_name, object_type,
                        before_revision_code, after_revision_code, effectivity, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("bitem"),
                    baselineId,
                    linked.master().id(),
                    linked.master().objectCode(),
                    linked.master().objectName(),
                    linked.master().objectType(),
                    linked.link().beforeRevisionCode(),
                    linked.link().afterRevisionCode(),
                    "立即生效",
                    sortOrder++
            );
        }
    }

    private void syncObjectAcl(
            String businessType,
            String billId,
            List<LinkedObject> linkedObjects,
            String currentUserId
    ) {
        int sortOrder = 1;
        for (LinkedObject linked : linkedObjects) {
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_object_acl (
                        id, business_type, bill_id, object_id, subject_type, subject_code, permission_code,
                        access_scope, inherited, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("acl"),
                    businessType,
                    billId,
                    linked.master().id(),
                    "USER",
                    normalize(linked.master().ownerUserId(), currentUserId),
                    "PLM_OBJECT_OWNER",
                    "OBJECT",
                    false,
                    sortOrder++
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_object_acl (
                        id, business_type, bill_id, object_id, subject_type, subject_code, permission_code,
                        access_scope, inherited, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("acl"),
                    businessType,
                    billId,
                    linked.master().id(),
                    "ROLE",
                    resolveRoleCode(linked.master().objectType()),
                    "PLM_OBJECT_CHANGE",
                    "DOMAIN",
                    true,
                    sortOrder++
            );
        }
    }

    private void syncDomainAcl(String businessType, String billId, List<LinkedObject> linkedObjects) {
        Map<String, List<String>> domainRoles = new LinkedHashMap<>();
        for (LinkedObject linked : linkedObjects) {
            String domainCode = normalize(linked.master().domainCode(), businessType);
            domainRoles.computeIfAbsent(domainCode, ignored -> new ArrayList<>())
                    .add(resolveRoleCode(linked.master().objectType()));
        }
        int sortOrder = 1;
        for (Map.Entry<String, List<String>> entry : domainRoles.entrySet()) {
            for (String roleCode : entry.getValue().stream().distinct().toList()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO plm_domain_acl (
                            id, business_type, bill_id, domain_code, role_code, permission_code,
                            access_scope, policy_source, sort_order, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                        buildId("dacl"),
                        businessType,
                        billId,
                        entry.getKey(),
                        roleCode,
                        resolveDomainPermission(roleCode),
                        "DOMAIN",
                        "ROLE_MATRIX",
                        sortOrder++
                );
            }
        }
    }

    private void syncRoleAssignments(
            String businessType,
            String billId,
            List<LinkedObject> linkedObjects,
            String currentUserId
    ) {
        List<RoleTemplate> templates = roleTemplates(businessType);
        int sortOrder = 1;
        for (RoleTemplate template : templates) {
            String assigneeUserId = resolveRoleAssignee(template.roleCode(), linkedObjects, currentUserId);
            String assigneeDisplayName = resolveUserDisplayName(assigneeUserId);
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_role_assignment (
                        id, business_type, bill_id, role_code, role_label, assignee_user_id, assignee_display_name,
                        assignment_scope, required_flag, status, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("role"),
                    businessType,
                    billId,
                    template.roleCode(),
                    template.roleLabel(),
                    assigneeUserId,
                    assigneeDisplayName,
                    template.assignmentScope(),
                    template.required(),
                    assigneeUserId == null || assigneeUserId.isBlank() ? "PENDING" : "ASSIGNED",
                    sortOrder++
            );
        }
    }

    private void syncExternalIntegrations(String businessType, String billId, List<LinkedObject> linkedObjects) {
        int sortOrder = 1;
        for (LinkedObject linked : linkedObjects) {
            List<IntegrationTarget> targets = integrationTargets(linked.master().objectType(), linked.master().sourceSystem());
            for (IntegrationTarget target : targets) {
                String integrationId = buildId("intg");
                String status = target.isSource() ? "SYNCED" : resolveIntegrationStatus(linked.link().changeAction(), target.systemCode());
                jdbcTemplate.update(
                        """
                        INSERT INTO plm_external_integration_record (
                            id, business_type, bill_id, object_id, system_code, system_name, direction_code,
                            integration_type, status, endpoint_key, external_ref, last_sync_at, message, sort_order,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                        integrationId,
                        businessType,
                        billId,
                        linked.master().id(),
                        target.systemCode(),
                        target.systemName(),
                        target.directionCode(),
                        target.integrationType(),
                        status,
                        target.endpointKey(),
                        normalize(linked.master().externalRef(), linked.master().objectCode()),
                        Timestamp.valueOf(LocalDateTime.now().minusHours(target.isSource() ? 1 : 0)),
                        resolveIntegrationMessage(status, target.systemName()),
                        sortOrder
                );
                insertSyncEvents(integrationId, linked, target, status);
                sortOrder++;
            }
        }
    }

    private void insertSyncEvents(String integrationId, LinkedObject linked, IntegrationTarget target, String status) {
        List<SyncEventTemplate> events = switch (status) {
            case "SYNCED" -> List.of(
                    new SyncEventTemplate("EXPORT", "SUCCESS", Map.of("objectCode", linked.master().objectCode(), "target", target.systemCode()), null),
                    new SyncEventTemplate("ACK", "SUCCESS", Map.of("externalRef", normalize(linked.master().externalRef(), linked.master().objectCode())), null)
            );
            case "FAILED" -> List.of(
                    new SyncEventTemplate("EXPORT", "SUCCESS", Map.of("objectCode", linked.master().objectCode(), "target", target.systemCode()), null),
                    new SyncEventTemplate("VALIDATE", "FAILED", Map.of("changeAction", linked.link().changeAction()), "目标系统校验失败，需要补充映射关系")
            );
            case "BLOCKED" -> List.of(
                    new SyncEventTemplate("PLAN", "SUCCESS", Map.of("objectCode", linked.master().objectCode(), "target", target.systemCode()), null),
                    new SyncEventTemplate("QUEUE", "PENDING", Map.of("reason", "等待上游基线发布"), "等待上游基线发布")
            );
            default -> List.of(
                    new SyncEventTemplate("PLAN", "SUCCESS", Map.of("objectCode", linked.master().objectCode(), "target", target.systemCode()), null),
                    new SyncEventTemplate("QUEUE", "PENDING", Map.of("changeAction", linked.link().changeAction()), null)
            );
        };
        int sortOrder = 1;
        for (SyncEventTemplate event : events) {
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_external_sync_event (
                        id, integration_id, event_type, status, payload_json, error_message,
                        happened_at, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("evt"),
                    integrationId,
                    event.eventType(),
                    event.status(),
                    toJson(new LinkedHashMap<>(event.payload())),
                    event.errorMessage(),
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(events.size() - sortOrder)),
                    sortOrder++
            );
        }
    }

    private String rollupIntegrationStatus(String eventStatus) {
        return switch (normalize(eventStatus, "PENDING")) {
            case "SUCCESS" -> "SYNCED";
            case "FAILED" -> "FAILED";
            default -> "PENDING";
        };
    }

    private List<PlmDashboardDistributionResponse> queryDistribution(String sql, Map<String, String> labels, String userId) {
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PlmDashboardDistributionResponse(
                        rs.getString("code"),
                        labels.getOrDefault(rs.getString("code"), rs.getString("code")),
                        rs.getLong("total_count")
                ),
                userId
        );
    }

    private List<String> safeRoleCodes(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<String> roleCodes = identityAccessMapper.selectRoleCodesByUserId(userId);
        return roleCodes == null ? List.of() : roleCodes;
    }

    private List<String> expandDomainPermission(String permissionCode, String roleCode) {
        String normalizedPermission = normalize(permissionCode);
        String normalizedRole = normalize(roleCode);
        return switch (normalizedPermission) {
            case "PLM_DOMAIN_CHANGE" -> {
                List<String> permissions = new ArrayList<>(List.of(
                        PERMISSION_BILL_READ,
                        PERMISSION_BILL_MANAGE,
                        PERMISSION_OBJECT_READ,
                        PERMISSION_OBJECT_CHANGE,
                        PERMISSION_BASELINE_READ,
                        PERMISSION_TASK_OPERATE
                ));
                if ("PLM_CHANGE_MANAGER".equals(normalizedRole)) {
                    permissions.add(PERMISSION_ACL_ADMIN);
                }
                yield permissions;
            }
            case "PLM_DOMAIN_RELEASE" -> List.of(
                    PERMISSION_BILL_READ,
                    PERMISSION_OBJECT_READ,
                    PERMISSION_OBJECT_CHANGE,
                    PERMISSION_BASELINE_READ
            );
            case "PLM_DOMAIN_VALIDATE" -> List.of(
                    PERMISSION_BILL_READ,
                    PERMISSION_BILL_MANAGE,
                    PERMISSION_BASELINE_READ,
                    PERMISSION_TASK_OPERATE
            );
            case "PLM_DOMAIN_SYNC" -> List.of(
                    PERMISSION_BILL_READ,
                    PERMISSION_OBJECT_READ,
                    PERMISSION_TASK_OPERATE
            );
            default -> List.of(PERMISSION_BILL_READ);
        };
    }

    private List<String> expandObjectPermission(String permissionCode) {
        return switch (normalize(permissionCode)) {
            case "PLM_OBJECT_OWNER" -> List.of(
                    PERMISSION_BILL_READ,
                    PERMISSION_OBJECT_READ,
                    PERMISSION_OBJECT_CHANGE,
                    PERMISSION_BASELINE_READ
            );
            case "PLM_OBJECT_CHANGE" -> List.of(
                    PERMISSION_BILL_READ,
                    PERMISSION_OBJECT_READ,
                    PERMISSION_OBJECT_CHANGE
            );
            default -> List.of(permissionCode);
        };
    }

    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT display_name FROM wf_user WHERE id = ?",
                userId
        );
        if (rows.isEmpty()) {
            return userId;
        }
        Object displayName = rows.get(0).get("display_name");
        return displayName == null ? userId : String.valueOf(displayName);
    }

    private ContractException forbidden(String billId, String message) {
        return new ContractException(
                "AUTH.FORBIDDEN",
                HttpStatus.FORBIDDEN,
                message,
                Map.of("billId", billId)
        );
    }

    private double queryRoleCoverageRate(String userId) {
        Long assigned = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM plm_role_assignment assignment
                JOIN (
                    SELECT 'PLM_ECR' AS business_type, id AS bill_id, creator_user_id FROM plm_ecr_change
                    UNION ALL
                    SELECT 'PLM_ECO' AS business_type, id AS bill_id, creator_user_id FROM plm_eco_execution
                    UNION ALL
                    SELECT 'PLM_MATERIAL' AS business_type, id AS bill_id, creator_user_id FROM plm_material_change
                ) bills
                  ON bills.business_type = assignment.business_type
                 AND bills.bill_id = assignment.bill_id
                WHERE bills.creator_user_id = ?
                  AND assignment.required_flag = TRUE
                  AND assignment.assignee_user_id IS NOT NULL
                  AND assignment.assignee_user_id <> ''
                """,
                Long.class,
                userId
        );
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM plm_role_assignment assignment
                JOIN (
                    SELECT 'PLM_ECR' AS business_type, id AS bill_id, creator_user_id FROM plm_ecr_change
                    UNION ALL
                    SELECT 'PLM_ECO' AS business_type, id AS bill_id, creator_user_id FROM plm_eco_execution
                    UNION ALL
                    SELECT 'PLM_MATERIAL' AS business_type, id AS bill_id, creator_user_id FROM plm_material_change
                ) bills
                  ON bills.business_type = assignment.business_type
                 AND bills.bill_id = assignment.bill_id
                WHERE bills.creator_user_id = ?
                  AND assignment.required_flag = TRUE
                """,
                Long.class,
                userId
        );
        if (total == null || total == 0L) {
            return 0D;
        }
        return Math.round(((assigned == null ? 0D : assigned.doubleValue()) / total.doubleValue()) * 10000D) / 100D;
    }

    private long queryLong(String sql, String userId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return value == null ? 0L : value;
    }

    private double queryAverageClosureHours(String userId) {
        List<Duration> durations = jdbcTemplate.query(
                """
                SELECT created_at, closed_at
                FROM (
                    SELECT creator_user_id, created_at, closed_at FROM plm_ecr_change
                    UNION ALL
                    SELECT creator_user_id, created_at, closed_at FROM plm_eco_execution
                    UNION ALL
                    SELECT creator_user_id, created_at, closed_at FROM plm_material_change
                ) bills
                WHERE creator_user_id = ?
                  AND closed_at IS NOT NULL
                """,
                (rs, rowNum) -> Duration.between(
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("closed_at").toLocalDateTime()
                ),
                userId
        );
        if (durations.isEmpty()) {
            return 0D;
        }
        double totalHours = durations.stream().mapToDouble(duration -> duration.toMinutes() / 60D).sum();
        return Math.round((totalHours / durations.size()) * 100D) / 100D;
    }

    private void deleteByBill(String tableName, String businessType, String billId) {
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE business_type = ? AND bill_id = ?", businessType, billId);
    }

    private void deleteByBill(String tableName, String businessType, String billId, String predicate) {
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + predicate, businessType, billId);
    }

    private RowMapper<PlmBomNodeResponse> bomNodeMapper() {
        return (rs, rowNum) -> new PlmBomNodeResponse(
                rs.getString("id"),
                rs.getString("business_type"),
                rs.getString("bill_id"),
                rs.getString("parent_node_id"),
                rs.getString("object_id"),
                rs.getString("node_code"),
                rs.getString("node_name"),
                rs.getString("node_type"),
                rs.getObject("quantity") == null ? null : rs.getDouble("quantity"),
                rs.getString("unit"),
                rs.getString("effectivity"),
                rs.getString("change_action"),
                rs.getInt("hierarchy_level"),
                rs.getInt("sort_order")
        );
    }

    private RowMapper<PlmDocumentAssetResponse> documentAssetMapper() {
        return (rs, rowNum) -> new PlmDocumentAssetResponse(
                rs.getString("id"),
                rs.getString("business_type"),
                rs.getString("bill_id"),
                rs.getString("object_id"),
                rs.getString("document_code"),
                rs.getString("document_name"),
                rs.getString("document_type"),
                rs.getString("version_label"),
                rs.getString("vault_state"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getString("source_system"),
                rs.getString("external_ref"),
                rs.getString("change_action"),
                rs.getInt("sort_order")
        );
    }

    private String resolveRoleCode(String objectType) {
        return switch (normalize(objectType)) {
            case "DRAWING", "DOCUMENT" -> "PLM_DOC_CONTROLLER";
            case "BOM", "PROCESS" -> "PLM_CHANGE_MANAGER";
            default -> "PLM_DOMAIN_OWNER";
        };
    }

    private String resolveDomainPermission(String roleCode) {
        return switch (normalize(roleCode)) {
            case "PLM_DOC_CONTROLLER" -> "PLM_DOMAIN_RELEASE";
            case "PLM_QUALITY_OWNER" -> "PLM_DOMAIN_VALIDATE";
            case "PLM_MANUFACTURING_OWNER", "PLM_ERP_OWNER" -> "PLM_DOMAIN_SYNC";
            default -> "PLM_DOMAIN_CHANGE";
        };
    }

    private List<RoleTemplate> roleTemplates(String businessType) {
        return switch (normalize(businessType)) {
            case "PLM_ECO" -> List.of(
                    new RoleTemplate("PLM_CHANGE_MANAGER", "变更经理", "PROGRAM", true),
                    new RoleTemplate("PLM_MANUFACTURING_OWNER", "制造负责人", "SITE", true),
                    new RoleTemplate("PLM_QUALITY_OWNER", "质量负责人", "DOMAIN", true)
            );
            case "PLM_MATERIAL" -> List.of(
                    new RoleTemplate("PLM_DATA_STEWARD", "主数据管理员", "DOMAIN", true),
                    new RoleTemplate("PLM_ERP_OWNER", "ERP 接口负责人", "SYSTEM", true),
                    new RoleTemplate("PLM_CHANGE_MANAGER", "变更经理", "PROGRAM", true)
            );
            default -> List.of(
                    new RoleTemplate("PLM_CHANGE_MANAGER", "变更经理", "PROGRAM", true),
                    new RoleTemplate("PLM_DOC_CONTROLLER", "文控负责人", "DOMAIN", true),
                    new RoleTemplate("PLM_QUALITY_OWNER", "质量负责人", "DOMAIN", true)
            );
        };
    }

    private String resolveRoleAssignee(String roleCode, List<LinkedObject> linkedObjects, String currentUserId) {
        if ("PLM_CHANGE_MANAGER".equalsIgnoreCase(roleCode) || "PLM_DATA_STEWARD".equalsIgnoreCase(roleCode)) {
            return currentUserId;
        }
        return linkedObjects.stream()
                .map(linked -> linked.master().ownerUserId())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(currentUserId);
    }

    private List<IntegrationTarget> integrationTargets(String objectType, String sourceSystem) {
        String normalizedSource = normalize(sourceSystem, "WEST_FLOW").toUpperCase();
        List<IntegrationTarget> targets = new ArrayList<>();
        targets.add(new IntegrationTarget(normalizedSource, systemName(normalizedSource), "UPSTREAM", "MASTER_DATA", "plm/" + normalizedSource.toLowerCase(), true));
        switch (normalize(objectType)) {
            case "DRAWING" -> {
                targets.add(new IntegrationTarget("PDM", "PDM 文档库", "DOWNSTREAM", "DOCUMENT_RELEASE", "plm/pdm/release", false));
                targets.add(new IntegrationTarget("CAD", "CAD 图档中心", "DOWNSTREAM", "DRAWING_PUBLISH", "plm/cad/publish", false));
            }
            case "DOCUMENT" -> targets.add(new IntegrationTarget("PDM", "PDM 文档库", "DOWNSTREAM", "DOCUMENT_RELEASE", "plm/pdm/release", false));
            case "BOM", "PART", "MATERIAL" -> {
                targets.add(new IntegrationTarget("ERP", "ERP 主数据", "DOWNSTREAM", "MASTER_SYNC", "plm/erp/sync", false));
                targets.add(new IntegrationTarget("MES", "MES 制造执行", "DOWNSTREAM", "BOM_SYNC", "plm/mes/sync", false));
            }
            default -> targets.add(new IntegrationTarget("ERP", "ERP 主数据", "DOWNSTREAM", "MASTER_SYNC", "plm/erp/sync", false));
        }
        return targets;
    }

    private String resolveIntegrationStatus(String changeAction, String systemCode) {
        String normalizedAction = normalize(changeAction);
        if ("CAD".equalsIgnoreCase(systemCode) && "REMOVE".equalsIgnoreCase(normalizedAction)) {
            return "FAILED";
        }
        if ("MES".equalsIgnoreCase(systemCode) && "REPLACE".equalsIgnoreCase(normalizedAction)) {
            return "BLOCKED";
        }
        return "PENDING";
    }

    private String resolveIntegrationMessage(String status, String systemName) {
        return switch (normalize(status)) {
            case "SYNCED" -> systemName + " 已完成最近一次回写。";
            case "FAILED" -> systemName + " 回写失败，等待人工处理。";
            case "BLOCKED" -> systemName + " 正等待上游条件满足。";
            default -> systemName + " 已排入本次变更同步队列。";
        };
    }

    private String systemName(String systemCode) {
        return switch (normalize(systemCode)) {
            case "ERP" -> "ERP 主数据";
            case "MES" -> "MES 制造执行";
            case "PDM" -> "PDM 文档库";
            case "CAD" -> "CAD 图档中心";
            default -> "平台主数据";
        };
    }

    private String defaultUnit(String objectType) {
        return switch (normalize(objectType)) {
            case "BOM" -> "SET";
            case "MATERIAL", "PART" -> "EA";
            default -> "DOC";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 PLM 企业对象摘要", ex);
        }
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record LinkedObject(
            PlmBillObjectLinkRecord link,
            PlmObjectMasterRecord master,
            PlmAffectedItemRequest item
    ) {
    }

    private record BaselineRecord(
            String id,
            String businessType,
            String billId,
            String baselineCode,
            String baselineName,
            String baselineType,
            String status,
            LocalDateTime releasedAt,
            String summaryJson
    ) {
    }

    private record ExternalIntegrationRecord(
            String id,
            String businessType,
            String billId,
            String objectId,
            String systemCode,
            String systemName,
            String directionCode,
            String integrationType,
            String status,
            String endpointKey,
            String externalRef,
            LocalDateTime lastSyncAt,
            String message,
            Integer sortOrder
    ) {
    }

    private record RoleTemplate(
            String roleCode,
            String roleLabel,
            String assignmentScope,
            boolean required
    ) {
    }

    private record IntegrationTarget(
            String systemCode,
            String systemName,
            String directionCode,
            String integrationType,
            String endpointKey,
            boolean isSource
    ) {
    }

    private record SyncEventTemplate(
            String eventType,
            String status,
            Map<String, Object> payload,
            String errorMessage
    ) {
    }

    private record HealthBucket(
            String code,
            long totalCount
    ) {
    }
}
