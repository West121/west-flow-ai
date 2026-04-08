package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.plm.api.PlmConnectorDispatchLogResponse;
import com.westflow.plm.api.PlmConnectorExternalAckRequest;
import com.westflow.plm.api.PlmConnectorExternalAckResponse;
import com.westflow.plm.api.PlmConnectorHealthSummaryResponse;
import com.westflow.plm.api.PlmConnectorJobResponse;
import com.westflow.plm.api.PlmConnectorSystemHealthResponse;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM v8 连接器任务、派发日志与外部 ACK 底座。
 */
@Service
@RequiredArgsConstructor
public class PlmConnectorJobService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlmConnectorPayloadBuilderService plmConnectorPayloadBuilderService;

    @Transactional
    public void enqueueLifecycleJobs(
            String businessType,
            String billId,
            String jobType,
            String operatorUserId,
            String summaryMessage
    ) {
        List<JobSeedRecord> seeds = queryJobSeeds(businessType, billId);
        if (seeds.isEmpty()) {
            seedBillLevelIntegrations(businessType, billId);
            seeds = queryJobSeeds(businessType, billId);
        }
        int sortOrder = 1;
        for (JobSeedRecord seed : seeds) {
            if (hasExistingJob(seed.integrationId(), jobType)) {
                continue;
            }
            String jobId = buildId("job");
            String payloadJson = plmConnectorPayloadBuilderService.buildPayload(
                    businessType,
                    billId,
                    jobType,
                    seed.connectorCode(),
                    seed.systemCode(),
                    seed.systemName(),
                    seed.endpointKey(),
                    operatorUserId,
                    summaryMessage
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_connector_job (
                        id, business_type, bill_id, integration_id, connector_registry_id,
                        job_type, status, request_payload_json, external_ref, retry_count,
                        next_run_at, last_dispatched_at, last_ack_at, last_error, created_by,
                        sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL, 0,
                              CURRENT_TIMESTAMP, NULL, NULL, NULL, ?, ?,
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    jobId,
                    businessType,
                    billId,
                    seed.integrationId(),
                    seed.connectorRegistryId(),
                    jobType,
                    payloadJson,
                    operatorUserId,
                    sortOrder
            );
            insertDispatchLog(
                    buildId("dlog"),
                    jobId,
                    "ENQUEUED",
                    "PENDING",
                    payloadJson,
                    null,
                    summaryMessage,
                    sortOrder++
            );
        }
    }

    private boolean hasExistingJob(String integrationId, String jobType) {
        Integer existing = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM plm_connector_job
                WHERE integration_id = ?
                  AND job_type = ?
                  AND status <> 'CANCELLED'
                """,
                Integer.class,
                integrationId,
                jobType
        );
        return existing != null && existing > 0;
    }

    private List<JobSeedRecord> queryJobSeeds(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT integration.id AS integration_id,
                       integration.system_code,
                       integration.system_name,
                       integration.direction_code,
                       integration.endpoint_key,
                       registry.id AS connector_registry_id,
                       registry.connector_code
                FROM plm_external_integration_record integration
                JOIN plm_connector_registry registry
                  ON registry.system_code = integration.system_code
                 AND registry.direction_code = integration.direction_code
                 AND registry.enabled = TRUE
                WHERE integration.business_type = ?
                  AND integration.bill_id = ?
                  AND integration.direction_code = 'DOWNSTREAM'
                ORDER BY integration.sort_order ASC, integration.created_at ASC
                """,
                (rs, rowNum) -> new JobSeedRecord(
                        rs.getString("integration_id"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("endpoint_key"),
                        rs.getString("connector_registry_id"),
                        rs.getString("connector_code")
                ),
                businessType,
                billId
        );
    }

    private void seedBillLevelIntegrations(String businessType, String billId) {
        List<RegistrySeedRecord> registries = jdbcTemplate.query(
                """
                SELECT id,
                       connector_code,
                       system_code,
                       system_name,
                       direction_code
                FROM plm_connector_registry
                WHERE enabled = TRUE
                  AND direction_code = 'DOWNSTREAM'
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new RegistrySeedRecord(
                        rs.getString("id"),
                        rs.getString("connector_code"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code")
                )
        );
        int sortOrder = 1;
        for (RegistrySeedRecord registry : registries) {
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM plm_external_integration_record
                    WHERE business_type = ?
                      AND bill_id = ?
                      AND system_code = ?
                      AND direction_code = ?
                    """,
                    Integer.class,
                    businessType,
                    billId,
                    registry.systemCode(),
                    registry.directionCode()
            );
            if (existing != null && existing > 0) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_external_integration_record (
                        id, business_type, bill_id, object_id, system_code, system_name, direction_code,
                        integration_type, status, endpoint_key, external_ref, last_sync_at, message, sort_order,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, 'PENDING', ?, ?, NULL, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("intg"),
                    businessType,
                    billId,
                    registry.systemCode(),
                    registry.systemName(),
                    registry.directionCode(),
                    "BILL_SYNC",
                    endpointKeyFor(registry.systemCode()),
                    billId,
                    registry.systemName() + " 已排入本次变更同步队列。",
                    sortOrder++
            );
        }
    }

    private String endpointKeyFor(String systemCode) {
        return switch (normalize(systemCode, "")) {
            case "ERP" -> "plm/erp/sync";
            case "MES" -> "plm/mes/sync";
            case "PDM" -> "plm/pdm/release";
            case "CAD" -> "plm/cad/publish";
            default -> "plm/" + normalize(systemCode, "external").toLowerCase() + "/sync";
        };
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    public List<PlmConnectorJobResponse> listBillJobs(String businessType, String billId) {
        List<JobRecord> jobs = jdbcTemplate.query(
                """
                SELECT job.id,
                       job.business_type,
                       job.bill_id,
                       job.integration_id,
                       job.connector_registry_id,
                       registry.connector_code,
                       registry.system_code,
                       registry.system_name,
                       registry.direction_code,
                       job.job_type,
                       job.status,
                       job.request_payload_json,
                       job.external_ref,
                       job.retry_count,
                       job.next_run_at,
                       job.last_dispatched_at,
                       job.last_ack_at,
                       job.last_error,
                       job.created_by,
                       job.sort_order
                FROM plm_connector_job job
                JOIN plm_connector_registry registry
                  ON registry.id = job.connector_registry_id
                WHERE job.business_type = ?
                  AND job.bill_id = ?
                ORDER BY job.sort_order ASC, job.created_at ASC
                """,
                (rs, rowNum) -> new JobRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("integration_id"),
                        rs.getString("connector_registry_id"),
                        rs.getString("connector_code"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("job_type"),
                        rs.getString("status"),
                        rs.getString("request_payload_json"),
                        rs.getString("external_ref"),
                        rs.getInt("retry_count"),
                        toLocalDateTime(rs, "next_run_at"),
                        toLocalDateTime(rs, "last_dispatched_at"),
                        toLocalDateTime(rs, "last_ack_at"),
                        rs.getString("last_error"),
                        rs.getString("created_by"),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
        List<PlmConnectorJobResponse> responses = new ArrayList<>();
        for (JobRecord job : jobs) {
            responses.add(new PlmConnectorJobResponse(
                    job.id(),
                    job.businessType(),
                    job.billId(),
                    job.integrationId(),
                    job.connectorRegistryId(),
                    job.connectorCode(),
                    job.systemCode(),
                    job.systemName(),
                    job.directionCode(),
                    job.jobType(),
                    job.status(),
                    job.requestPayloadJson(),
                    job.externalRef(),
                    job.retryCount(),
                    job.nextRunAt(),
                    job.lastDispatchedAt(),
                    job.lastAckAt(),
                    job.lastError(),
                    job.createdBy(),
                    job.sortOrder(),
                    listDispatchLogsInternal(job.id()),
                    listAcks(job.id())
            ));
        }
        return responses;
    }

    public PlmConnectorHealthSummaryResponse summarizeBillHealth(String businessType, String billId) {
        List<PlmConnectorJobResponse> jobs = listBillJobs(businessType, billId);
        int pendingCount = countJobs(jobs, "PENDING");
        int retryPendingCount = countJobs(jobs, "RETRY_PENDING");
        int dispatchedCount = countJobs(jobs, "DISPATCHED");
        int ackPendingCount = countJobs(jobs, "ACK_PENDING");
        int ackedCount = countJobs(jobs, "ACKED");
        int failedCount = countJobs(jobs, "FAILED");
        LocalDateTime latestDispatchedAt = jobs.stream()
                .map(PlmConnectorJobResponse::lastDispatchedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime latestAckAt = jobs.stream()
                .map(PlmConnectorJobResponse::lastAckAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        List<PlmConnectorSystemHealthResponse> systems = jobs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        job -> systemKey(job.systemCode(), job.systemName()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> {
                    List<PlmConnectorJobResponse> group = entry.getValue();
                    String[] parts = entry.getKey().split("\\|", 2);
                    int groupPending = countJobs(group, "PENDING");
                    int groupRetryPending = countJobs(group, "RETRY_PENDING");
                    int groupDispatched = countJobs(group, "DISPATCHED");
                    int groupAckPending = countJobs(group, "ACK_PENDING");
                    int groupAcked = countJobs(group, "ACKED");
                    int groupFailed = countJobs(group, "FAILED");
                    return new PlmConnectorSystemHealthResponse(
                            parts[0],
                            parts.length > 1 ? parts[1] : parts[0],
                            group.size(),
                            groupPending,
                            groupRetryPending,
                            groupDispatched,
                            groupAckPending,
                            groupAcked,
                            groupFailed,
                            groupRetryPending + groupAckPending + groupFailed
                    );
                })
                .toList();
        return new PlmConnectorHealthSummaryResponse(
                businessType,
                billId,
                jobs.size(),
                pendingCount,
                retryPendingCount,
                dispatchedCount,
                ackPendingCount,
                ackedCount,
                failedCount,
                retryPendingCount + ackPendingCount + failedCount,
                latestDispatchedAt,
                latestAckAt,
                systems
        );
    }

    public List<PlmConnectorDispatchLogResponse> listDispatchLogs(String jobId) {
        return listDispatchLogsInternal(jobId);
    }

    public JobLocator requireJobLocator(String jobId) {
        List<JobLocator> rows = jdbcTemplate.query(
                """
                SELECT id, business_type, bill_id, integration_id
                FROM plm_connector_job
                WHERE id = ?
                """,
                (rs, rowNum) -> new JobLocator(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("integration_id")
                ),
                jobId
        );
        if (rows.isEmpty()) {
            throw new ContractException(
                    "PLM.CONNECTOR_JOB_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "连接器任务不存在",
                    Map.of("jobId", jobId)
            );
        }
        return rows.get(0);
    }

    @Transactional
    public PlmConnectorJobResponse retryJob(String jobId, String operatorUserId) {
        JobLocator locator = requireJobLocator(jobId);
        JobStatusRecord statusRecord = jdbcTemplate.queryForObject(
                """
                SELECT status, retry_count, request_payload_json
                FROM plm_connector_job
                WHERE id = ?
                """,
                (rs, rowNum) -> new JobStatusRecord(
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("request_payload_json")
                ),
                jobId
        );
        if (!isRetryableStatus(statusRecord.status())) {
            throw new ContractException(
                    "PLM.CONNECTOR_JOB_NOT_RETRYABLE",
                    HttpStatus.BAD_REQUEST,
                    "当前连接器任务不允许重试",
                    Map.of("jobId", jobId, "status", statusRecord.status())
            );
        }
        jdbcTemplate.update(
                """
                UPDATE plm_connector_job
                SET status = 'RETRY_PENDING',
                    retry_count = ?,
                    next_run_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                statusRecord.retryCount() + 1,
                jobId
        );
        insertDispatchLog(
                buildId("dlog"),
                jobId,
                "RETRY_REQUESTED",
                "RETRY_PENDING",
                statusRecord.requestPayloadJson(),
                null,
                "人工触发重试",
                statusRecord.retryCount() + 100
        );
        jdbcTemplate.update(
                """
                INSERT INTO plm_external_sync_event (
                    id, integration_id, event_type, status, payload_json, error_message,
                    happened_at, sort_order, created_at, updated_at
                ) VALUES (?, ?, 'CONNECTOR_RETRY_REQUESTED', 'PENDING', ?, NULL, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                buildId("evt"),
                locator.integrationId(),
                toJson(Map.of("jobId", jobId, "operatorUserId", operatorUserId)),
                nextSyncEventSortOrder(locator.integrationId())
        );
        return listBillJobs(locator.businessType(), locator.billId()).stream()
                .filter(job -> job.id().equals(jobId))
                .findFirst()
                .orElseThrow();
    }

    public PlmConnectorHandler.DispatchCommand prepareDispatch(String jobId) {
        DispatchableJobRecord record = jdbcTemplate.queryForObject(
                """
                SELECT job.status,
                       job.id,
                       job.business_type,
                       job.bill_id,
                       job.integration_id,
                       job.connector_registry_id,
                       job.request_payload_json,
                       job.external_ref,
                       job.job_type,
                       registry.connector_code,
                       registry.system_code,
                       registry.system_name,
                       registry.direction_code,
                       registry.handler_key
                FROM plm_connector_job job
                JOIN plm_connector_registry registry
                  ON registry.id = job.connector_registry_id
                WHERE job.id = ?
                """,
                (rs, rowNum) -> new DispatchableJobRecord(
                        rs.getString("status"),
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("integration_id"),
                        rs.getString("connector_registry_id"),
                        rs.getString("request_payload_json"),
                        rs.getString("external_ref"),
                        rs.getString("job_type"),
                        rs.getString("connector_code"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("direction_code"),
                        rs.getString("handler_key")
                ),
                jobId
        );
        if (!isDispatchableStatus(record.status())) {
            throw new ContractException(
                    "PLM.CONNECTOR_JOB_NOT_DISPATCHABLE",
                    HttpStatus.BAD_REQUEST,
                    "当前连接器任务不允许派发",
                    Map.of("jobId", jobId, "status", record.status())
            );
        }
        return new PlmConnectorHandler.DispatchCommand(
                record.id(),
                record.businessType(),
                record.billId(),
                record.integrationId(),
                record.connectorRegistryId(),
                record.handlerKey(),
                record.connectorCode(),
                record.systemCode(),
                record.systemName(),
                record.directionCode(),
                record.jobType(),
                record.requestPayloadJson(),
                record.externalRef()
        );
    }

    @Transactional
    public PlmConnectorJobResponse completeDispatch(
            PlmConnectorHandler.DispatchCommand command,
            PlmConnectorHandler.DispatchResult result
    ) {
        String externalRef = blankToNull(result.externalRef()) != null
                ? result.externalRef()
                : "EXT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        jdbcTemplate.update(
                """
                UPDATE plm_connector_job
                SET status = 'DISPATCHED',
                    external_ref = ?,
                    last_dispatched_at = CURRENT_TIMESTAMP,
                    next_run_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                externalRef,
                command.jobId()
        );
        jdbcTemplate.update(
                """
                UPDATE plm_external_integration_record
                SET status = 'SYNCING',
                    external_ref = COALESCE(?, external_ref),
                    last_sync_at = CURRENT_TIMESTAMP,
                    message = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                externalRef,
                blankToNull(result.message()) == null ? command.systemName() + " 已接受本次变更同步任务。" : result.message(),
                command.integrationId()
        );
        insertDispatchLog(
                buildId("dlog"),
                command.jobId(),
                "DISPATCHED",
                "DISPATCHED",
                command.requestPayloadJson(),
                blankToNull(result.responsePayloadJson()),
                null,
                nextDispatchSortOrder(command.jobId())
        );
        jdbcTemplate.update(
                """
                INSERT INTO plm_external_sync_event (
                    id, integration_id, event_type, status, payload_json, error_message,
                    happened_at, sort_order, created_at, updated_at
                ) VALUES (?, ?, 'CONNECTOR_DISPATCHED', 'SYNCING', ?, NULL, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                buildId("evt"),
                command.integrationId(),
                blankToNull(result.responsePayloadJson()),
                nextSyncEventSortOrder(command.integrationId())
        );
        return listBillJobs(command.businessType(), command.billId()).stream()
                .filter(job -> job.id().equals(command.jobId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PlmConnectorExternalAckResponse writeAck(String jobId, PlmConnectorExternalAckRequest request) {
        JobLocator locator = requireJobLocator(jobId);
        JobRegistryRecord registryRecord = jdbcTemplate.queryForObject(
                """
                SELECT job.integration_id,
                       registry.connector_code,
                       registry.system_code,
                       registry.system_name,
                       registry.config_json
                FROM plm_connector_job job
                JOIN plm_connector_registry registry
                  ON registry.id = job.connector_registry_id
                WHERE job.id = ?
                """,
                (rs, rowNum) -> new JobRegistryRecord(
                        rs.getString("integration_id"),
                        rs.getString("connector_code"),
                        rs.getString("system_code"),
                        rs.getString("system_name"),
                        rs.getString("config_json")
                ),
                jobId
        );
        String requestedSourceSystem = blankToNull(request == null ? null : request.sourceSystem());
        if (requestedSourceSystem != null && !registryRecord.systemCode().equalsIgnoreCase(requestedSourceSystem)) {
            throw new ContractException(
                    "PLM.CONNECTOR_ACK_SOURCE_MISMATCH",
                    HttpStatus.BAD_REQUEST,
                    "连接器回执来源与任务所属系统不一致",
                    Map.of(
                            "jobId", jobId,
                            "expectedSourceSystem", registryRecord.systemCode(),
                            "actualSourceSystem", requestedSourceSystem
                    )
            );
        }
        String idempotencyKey = blankToNull(request == null ? null : request.idempotencyKey());
        if (idempotencyKey != null) {
            List<PlmConnectorExternalAckResponse> existing = jdbcTemplate.query(
                    """
                    SELECT id,
                           job_id,
                           ack_status,
                           ack_code,
                           idempotency_key,
                           external_ref,
                           message,
                           payload_json,
                           source_system,
                           happened_at,
                           sort_order
                    FROM plm_external_ack
                    WHERE job_id = ?
                      AND idempotency_key = ?
                    ORDER BY happened_at ASC
                    """,
                    (rs, rowNum) -> new PlmConnectorExternalAckResponse(
                            rs.getString("id"),
                            rs.getString("job_id"),
                            rs.getString("ack_status"),
                            rs.getString("ack_code"),
                            rs.getString("idempotency_key"),
                            rs.getString("external_ref"),
                            rs.getString("message"),
                            rs.getString("payload_json"),
                            rs.getString("source_system"),
                            toLocalDateTime(rs, "happened_at"),
                            rs.getInt("sort_order")
                    ),
                    jobId,
                    idempotencyKey
            );
            if (!existing.isEmpty()) {
                return existing.getFirst();
            }
        }
        String currentJobStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM plm_connector_job WHERE id = ?",
                String.class,
                jobId
        );
        if (!isAckableStatus(currentJobStatus)) {
            throw new ContractException(
                    "PLM.CONNECTOR_ACK_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "当前连接器任务状态不允许写入回执",
                    Map.of("jobId", jobId, "status", currentJobStatus)
            );
        }
        String requiredAckToken = registryRecord.ackToken(objectMapper);
        if (requiredAckToken != null) {
            String actualAckToken = blankToNull(request == null ? null : request.ackToken());
            if (!requiredAckToken.equals(actualAckToken)) {
                throw new ContractException(
                        "PLM.CONNECTOR_ACK_TOKEN_INVALID",
                        HttpStatus.FORBIDDEN,
                        "连接器回执鉴权失败",
                        Map.of(
                                "jobId", jobId,
                                "connectorCode", registryRecord.connectorCode(),
                                "sourceSystem", registryRecord.systemCode()
                        )
                );
            }
        }
        String ackStatus = normalizeAckStatus(request == null ? null : request.ackStatus());
        String ackId = buildId("ack");
        Integer sortOrder = nextAckSortOrder(jobId);
        jdbcTemplate.update(
                """
                INSERT INTO plm_external_ack (
                    id, job_id, ack_status, ack_code, idempotency_key, external_ref, message,
                    payload_json, source_system, happened_at, sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                ackId,
                jobId,
                ackStatus,
                blankToNull(request == null ? null : request.ackCode()),
                idempotencyKey,
                blankToNull(request == null ? null : request.externalRef()),
                blankToNull(request == null ? null : request.message()),
                blankToNull(request == null ? null : request.payloadJson()),
                requestedSourceSystem == null ? registryRecord.systemCode() : requestedSourceSystem.toUpperCase(),
                sortOrder
        );
        String jobStatus = mapJobStatusFromAck(ackStatus);
        jdbcTemplate.update(
                """
                UPDATE plm_connector_job
                SET status = ?,
                    external_ref = COALESCE(?, external_ref),
                    last_ack_at = CURRENT_TIMESTAMP,
                    last_error = CASE WHEN ? = 'FAILED' THEN ? ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                jobStatus,
                blankToNull(request == null ? null : request.externalRef()),
                jobStatus,
                blankToNull(request == null ? null : request.message()),
                jobId
        );
        jdbcTemplate.update(
                """
                UPDATE plm_external_integration_record
                SET status = ?,
                    external_ref = COALESCE(?, external_ref),
                    last_sync_at = CURRENT_TIMESTAMP,
                    message = COALESCE(?, message),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                mapIntegrationStatusFromAck(ackStatus),
                blankToNull(request == null ? null : request.externalRef()),
                blankToNull(request == null ? null : request.message()),
                locator.integrationId()
        );
        insertDispatchLog(
                buildId("dlog"),
                jobId,
                "ACK_RECEIVED",
                jobStatus,
                null,
                blankToNull(request == null ? null : request.payloadJson()),
                blankToNull(request == null ? null : request.message()),
                nextDispatchSortOrder(jobId)
        );
        jdbcTemplate.update(
                """
                INSERT INTO plm_external_sync_event (
                    id, integration_id, event_type, status, payload_json, error_message,
                    happened_at, sort_order, created_at, updated_at
                ) VALUES (?, ?, 'EXTERNAL_ACK_RECEIVED', ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                buildId("evt"),
                locator.integrationId(),
                mapIntegrationStatusFromAck(ackStatus),
                toJson(Map.of(
                        "jobId", jobId,
                        "ackStatus", ackStatus,
                        "externalRef", blankToNull(request == null ? null : request.externalRef()),
                        "systemCode", registryRecord.systemCode()
                )),
                blankToNull(request == null ? null : request.message()),
                nextSyncEventSortOrder(locator.integrationId())
        );
        return new PlmConnectorExternalAckResponse(
                ackId,
                jobId,
                ackStatus,
                blankToNull(request == null ? null : request.ackCode()),
                idempotencyKey,
                blankToNull(request == null ? null : request.externalRef()),
                blankToNull(request == null ? null : request.message()),
                blankToNull(request == null ? null : request.payloadJson()),
                requestedSourceSystem == null ? registryRecord.systemCode() : requestedSourceSystem.toUpperCase(),
                LocalDateTime.now(),
                sortOrder
        );
    }

    private List<PlmConnectorDispatchLogResponse> listDispatchLogsInternal(String jobId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       job_id,
                       action_type,
                       status,
                       request_payload_json,
                       response_payload_json,
                       error_message,
                       happened_at,
                       sort_order
                FROM plm_connector_dispatch_log
                WHERE job_id = ?
                ORDER BY sort_order ASC, happened_at ASC
                """,
                (rs, rowNum) -> new PlmConnectorDispatchLogResponse(
                        rs.getString("id"),
                        rs.getString("job_id"),
                        rs.getString("action_type"),
                        rs.getString("status"),
                        rs.getString("request_payload_json"),
                        rs.getString("response_payload_json"),
                        rs.getString("error_message"),
                        toLocalDateTime(rs, "happened_at"),
                        rs.getInt("sort_order")
                ),
                jobId
        );
    }

    private List<PlmConnectorExternalAckResponse> listAcks(String jobId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       job_id,
                       ack_status,
                       ack_code,
                       idempotency_key,
                       external_ref,
                       message,
                       payload_json,
                       source_system,
                       happened_at,
                       sort_order
                FROM plm_external_ack
                WHERE job_id = ?
                ORDER BY sort_order ASC, happened_at ASC
                """,
                (rs, rowNum) -> new PlmConnectorExternalAckResponse(
                        rs.getString("id"),
                        rs.getString("job_id"),
                        rs.getString("ack_status"),
                        rs.getString("ack_code"),
                        rs.getString("idempotency_key"),
                        rs.getString("external_ref"),
                        rs.getString("message"),
                        rs.getString("payload_json"),
                        rs.getString("source_system"),
                        toLocalDateTime(rs, "happened_at"),
                        rs.getInt("sort_order")
                ),
                jobId
        );
    }

    private void insertDispatchLog(
            String logId,
            String jobId,
            String actionType,
            String status,
            String requestPayloadJson,
            String responsePayloadJson,
            String errorMessage,
            Integer sortOrder
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO plm_connector_dispatch_log (
                    id, job_id, action_type, status, request_payload_json,
                    response_payload_json, error_message, happened_at, sort_order,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                logId,
                jobId,
                actionType,
                status,
                requestPayloadJson,
                responsePayloadJson,
                errorMessage,
                sortOrder
        );
    }

    private Integer nextAckSortOrder(String jobId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM plm_external_ack WHERE job_id = ?",
                Integer.class,
                jobId
        );
        return value == null ? 1 : value;
    }

    private Integer nextDispatchSortOrder(String jobId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM plm_connector_dispatch_log WHERE job_id = ?",
                Integer.class,
                jobId
        );
        return value == null ? 1 : value;
    }

    private Integer nextSyncEventSortOrder(String integrationId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM plm_external_sync_event WHERE integration_id = ?",
                Integer.class,
                integrationId
        );
        return value == null ? 1 : value;
    }

    private int countJobs(List<PlmConnectorJobResponse> jobs, String status) {
        return (int) jobs.stream()
                .filter(job -> status.equalsIgnoreCase(job.status()))
                .count();
    }

    private String systemKey(String systemCode, String systemName) {
        return (systemCode == null ? "" : systemCode) + "|" + (systemName == null ? "" : systemName);
    }

    private boolean isRetryableStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING", "FAILED", "DISPATCHED", "ACK_PENDING", "RETRY_PENDING" -> true;
            default -> false;
        };
    }

    private boolean isDispatchableStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING", "RETRY_PENDING" -> true;
            default -> false;
        };
    }

    private boolean isAckableStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase()) {
            case "DISPATCHED", "ACK_PENDING" -> true;
            default -> false;
        };
    }

    private String mapJobStatusFromAck(String ackStatus) {
        return switch (ackStatus) {
            case "SUCCESS", "ACKED" -> "ACKED";
            case "FAILED", "REJECTED" -> "FAILED";
            default -> "ACK_PENDING";
        };
    }

    private String mapIntegrationStatusFromAck(String ackStatus) {
        return switch (ackStatus) {
            case "SUCCESS", "ACKED" -> "SYNCED";
            case "FAILED", "REJECTED" -> "FAILED";
            default -> "PENDING";
        };
    }

    private String normalizeAckStatus(String ackStatus) {
        String normalized = blankToNull(ackStatus);
        return normalized == null ? "ACKED" : normalized.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化连接器任务请求", ex);
        }
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public record JobLocator(
            String jobId,
            String businessType,
            String billId,
            String integrationId
    ) {
    }

    private record JobSeedRecord(
            String integrationId,
            String systemCode,
            String systemName,
            String directionCode,
            String endpointKey,
            String connectorRegistryId,
            String connectorCode
    ) {
    }

    private record RegistrySeedRecord(
            String id,
            String connectorCode,
            String systemCode,
            String systemName,
            String directionCode
    ) {
    }

    private record JobRecord(
            String id,
            String businessType,
            String billId,
            String integrationId,
            String connectorRegistryId,
            String connectorCode,
            String systemCode,
            String systemName,
            String directionCode,
            String jobType,
            String status,
            String requestPayloadJson,
            String externalRef,
            Integer retryCount,
            LocalDateTime nextRunAt,
            LocalDateTime lastDispatchedAt,
            LocalDateTime lastAckAt,
            String lastError,
            String createdBy,
            Integer sortOrder
    ) {
    }

    private record JobStatusRecord(
            String status,
            Integer retryCount,
            String requestPayloadJson
    ) {
    }

    private record JobRegistryRecord(
            String integrationId,
            String connectorCode,
            String systemCode,
            String systemName,
            String configJson
    ) {
        private String ackToken(ObjectMapper objectMapper) {
            if (configJson == null || configJson.isBlank()) {
                return null;
            }
            try {
                return blankToNullStatic(objectMapper.readTree(configJson).path("ackToken").asText(null));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("无法解析连接器配置", ex);
            }
        }
    }

    private record DispatchableJobRecord(
            String status,
            String id,
            String businessType,
            String billId,
            String integrationId,
            String connectorRegistryId,
            String requestPayloadJson,
            String externalRef,
            String jobType,
            String connectorCode,
            String systemCode,
            String systemName,
            String directionCode,
            String handlerKey
    ) {
    }

    private static String blankToNullStatic(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
