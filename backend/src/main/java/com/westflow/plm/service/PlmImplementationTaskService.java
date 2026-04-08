package com.westflow.plm.service;

import com.westflow.common.error.ContractException;
import com.westflow.plm.api.PlmAcceptanceChecklistResponse;
import com.westflow.plm.api.PlmAcceptanceChecklistUpdateRequest;
import com.westflow.plm.api.PlmImplementationDependencyResponse;
import com.westflow.plm.api.PlmImplementationEvidenceResponse;
import com.westflow.plm.api.PlmImplementationEvidenceUpdateRequest;
import com.westflow.plm.api.PlmImplementationEvidenceUpsertRequest;
import com.westflow.plm.api.PlmImplementationTaskActionRequest;
import com.westflow.plm.api.PlmImplementationTaskResponse;
import com.westflow.plm.api.PlmImplementationTaskUpsertRequest;
import com.westflow.plm.api.PlmImplementationTemplateResponse;
import com.westflow.plm.api.PlmImplementationWorkspaceResponse;
import com.westflow.plm.api.PlmRoleAssignmentResponse;
import com.westflow.plm.mapper.PlmImplementationTaskMapper;
import com.westflow.plm.model.PlmImplementationTaskRecord;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM 实施任务服务。
 */
@Service
@RequiredArgsConstructor
public class PlmImplementationTaskService {

    private final PlmImplementationTaskMapper plmImplementationTaskMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PlmEnterpriseDepthService plmEnterpriseDepthService;

    @Transactional
    public void seedDefaultTaskIfMissing(
            String businessType,
            String billId,
            String sceneCode,
            String taskTitle,
            String ownerUserId
    ) {
        if (!plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).isEmpty()) {
            ensureDefaultChecklist(businessType, billId);
            return;
        }
        List<TemplateRecord> templates = selectTemplates(businessType, sceneCode);
        if (templates.isEmpty()) {
            plmImplementationTaskMapper.insert(new PlmImplementationTaskRecord(
                    buildId("task"),
                    businessType,
                    billId,
                    null,
                    null,
                    "TASK-001",
                    taskTitle == null || taskTitle.isBlank() ? "实施任务" : taskTitle.trim(),
                    "IMPLEMENTATION",
                    ownerUserId,
                    "PENDING",
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    true,
                    1,
                    null,
                    null
            ));
            ensureDefaultChecklist(businessType, billId);
            return;
        }
        Map<String, String> roleOwners = resolveRoleOwners(businessType, billId);
        Map<String, PlmImplementationTaskRecord> generatedTasks = new LinkedHashMap<>();
        int sortOrder = 1;
        for (TemplateRecord template : templates) {
            PlmImplementationTaskRecord record = new PlmImplementationTaskRecord(
                    buildId("task"),
                    businessType,
                    billId,
                    template.id(),
                    template.templateCode(),
                    String.format("TASK-%03d", sortOrder),
                    defaultTaskTitle(template, taskTitle),
                    template.taskType(),
                    roleOwners.getOrDefault(template.defaultOwnerRoleCode(), ownerUserId),
                    "PENDING",
                    null,
                    null,
                    null,
                    null,
                    null,
                    safeInt(template.requiredEvidenceCount()),
                    template.verificationRequired(),
                    sortOrder,
                    null,
                    null
            );
            plmImplementationTaskMapper.insert(record);
            generatedTasks.put(template.templateCode(), record);
            sortOrder++;
        }
        buildDependenciesFromTemplates(businessType, billId, templates, generatedTasks);
        ensureDefaultChecklist(businessType, billId);
    }

    public void ensureReadyForValidation(String businessType, String billId) {
        List<PlmImplementationTaskRecord> tasks = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId);
        if (tasks.isEmpty()) {
            throw illegalTaskState("至少需要一条实施任务才能进入验证阶段", businessType, billId);
        }
        boolean hasIncompleteRequiredTask = tasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.verificationRequired()))
                .anyMatch(task -> !"COMPLETED".equalsIgnoreCase(task.status()));
        if (hasIncompleteRequiredTask) {
            throw illegalTaskState("存在未完成的必做实施任务", businessType, billId);
        }
    }

    public void ensureReadyForClose(String businessType, String billId) {
        List<PlmImplementationTaskRecord> tasks = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId);
        boolean hasOpenTask = tasks.stream()
                .anyMatch(task -> !"COMPLETED".equalsIgnoreCase(task.status()) && !"CANCELLED".equalsIgnoreCase(task.status()));
        if (hasOpenTask) {
            throw illegalTaskState("仍有未完成的实施任务，不能关闭", businessType, billId);
        }
        boolean hasMissingEvidence = tasks.stream()
                .filter(task -> !"CANCELLED".equalsIgnoreCase(task.status()))
                .anyMatch(task -> evidenceCount(task.id()) < safeInt(task.requiredEvidenceCount()));
        if (hasMissingEvidence) {
            throw illegalTaskState("仍有实施任务缺少必要证据，不能关闭", businessType, billId);
        }
        boolean hasIncompleteChecklist = listAcceptanceChecklist(businessType, billId).stream()
                .filter(item -> Boolean.TRUE.equals(item.requiredFlag()))
                .anyMatch(item -> !"ACCEPTED".equalsIgnoreCase(item.status()));
        if (hasIncompleteChecklist) {
            throw illegalTaskState("仍有未完成的必做验收清单，不能关闭", businessType, billId);
        }
    }

    public List<PlmImplementationTaskResponse> listBillTasks(String businessType, String billId) {
        List<PlmImplementationTaskRecord> tasks = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId);
        Map<String, Integer> evidenceCounts = queryEvidenceCounts(businessType, billId);
        List<DependencyRecord> dependencies = selectDependencies(businessType, billId);
        Map<String, PlmImplementationTaskRecord> taskIndex = tasks.stream()
                .collect(Collectors.toMap(PlmImplementationTaskRecord::id, task -> task));
        return tasks.stream()
                .sorted(Comparator.comparing(PlmImplementationTaskRecord::sortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(task -> toResponse(
                        task,
                        evidenceCounts.getOrDefault(task.id(), 0),
                        dependencyReady(task, dependencies, taskIndex),
                        blockedByTaskIds(task, dependencies, taskIndex)
                ))
                .toList();
    }

    public List<PlmImplementationTemplateResponse> listTemplates(String businessType, String sceneCode) {
        return selectTemplates(businessType, sceneCode).stream()
                .map(template -> new PlmImplementationTemplateResponse(
                        template.id(),
                        template.businessType(),
                        template.sceneCode(),
                        template.templateCode(),
                        template.templateName(),
                        template.taskType(),
                        template.defaultTaskTitle(),
                        template.defaultOwnerRoleCode(),
                        template.requiredEvidenceCount(),
                        template.verificationRequired(),
                        template.sortOrder(),
                        template.enabled()
                ))
                .toList();
    }

    public List<PlmImplementationDependencyResponse> listDependencies(String businessType, String billId) {
        Map<String, PlmImplementationTaskRecord> taskIndex = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .collect(Collectors.toMap(PlmImplementationTaskRecord::id, task -> task));
        return selectDependencies(businessType, billId).stream()
                .map(record -> new PlmImplementationDependencyResponse(
                        record.id(),
                        record.businessType(),
                        record.billId(),
                        record.predecessorTaskId(),
                        labelTaskNo(record.predecessorTaskId(), taskIndex),
                        labelTaskTitle(record.predecessorTaskId(), taskIndex),
                        record.successorTaskId(),
                        labelTaskNo(record.successorTaskId(), taskIndex),
                        labelTaskTitle(record.successorTaskId(), taskIndex),
                        record.dependencyType(),
                        record.requiredFlag()
                ))
                .toList();
    }

    public List<PlmImplementationEvidenceResponse> listEvidence(String businessType, String billId) {
        return selectEvidence(businessType, billId).stream()
                .map(record -> new PlmImplementationEvidenceResponse(
                        record.id(),
                        record.businessType(),
                        record.billId(),
                        record.taskId(),
                        record.evidenceType(),
                        record.evidenceName(),
                        record.evidenceRef(),
                        record.evidenceSummary(),
                        record.uploadedBy(),
                        record.createdAt()
                ))
                .toList();
    }

    public List<PlmAcceptanceChecklistResponse> listAcceptanceChecklist(String businessType, String billId) {
        ensureDefaultChecklist(businessType, billId);
        return selectChecklist(businessType, billId).stream()
                .map(record -> new PlmAcceptanceChecklistResponse(
                        record.id(),
                        record.businessType(),
                        record.billId(),
                        record.checkCode(),
                        record.checkName(),
                        record.requiredFlag(),
                        record.status(),
                        record.resultSummary(),
                        record.checkedBy(),
                        record.checkedAt(),
                        record.sortOrder()
                ))
                .toList();
    }

    public PlmImplementationWorkspaceResponse workspace(String businessType, String billId) {
        String sceneCode = resolveSceneCode(businessType, billId);
        return new PlmImplementationWorkspaceResponse(
                listBillTasks(businessType, billId),
                listTemplates(businessType, sceneCode),
                listDependencies(businessType, billId),
                listEvidence(businessType, billId),
                listAcceptanceChecklist(businessType, billId)
        );
    }

    @Transactional
    public PlmImplementationEvidenceResponse addEvidence(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationEvidenceUpsertRequest request,
            String currentUserId
    ) {
        requireTask(businessType, billId, taskId);
        String evidenceId = buildId("evi");
        jdbcTemplate.update(
                """
                INSERT INTO plm_implementation_task_evidence (
                    id, business_type, bill_id, task_id, evidence_type, evidence_name, evidence_ref, evidence_summary, uploaded_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                evidenceId,
                businessType,
                billId,
                taskId,
                normalizeValue(request.evidenceType(), "ATTACHMENT"),
                normalizeValue(request.evidenceName(), "实施证据"),
                normalizeValue(request.evidenceRef(), null),
                normalizeValue(request.evidenceSummary(), null),
                currentUserId
        );
        return listEvidence(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), evidenceId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PlmImplementationEvidenceResponse updateEvidence(
            String businessType,
            String billId,
            String evidenceId,
            PlmImplementationEvidenceUpdateRequest request
    ) {
        EvidenceRecord existing = selectEvidence(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), evidenceId))
                .findFirst()
                .orElseThrow(() -> illegalTaskState("实施证据不存在", businessType, billId));
        jdbcTemplate.update(
                """
                UPDATE plm_implementation_task_evidence
                SET evidence_type = ?,
                    evidence_name = ?,
                    evidence_ref = ?,
                    evidence_summary = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                normalizeValue(request.evidenceType(), existing.evidenceType()),
                normalizeValue(request.evidenceName(), existing.evidenceName()),
                normalizeValue(request.evidenceRef(), existing.evidenceRef()),
                normalizeValue(request.evidenceSummary(), existing.evidenceSummary()),
                evidenceId
        );
        return listEvidence(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), evidenceId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteEvidence(String businessType, String billId, String evidenceId) {
        EvidenceRecord existing = selectEvidence(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), evidenceId))
                .findFirst()
                .orElseThrow(() -> illegalTaskState("实施证据不存在", businessType, billId));
        jdbcTemplate.update("DELETE FROM plm_implementation_task_evidence WHERE id = ?", existing.id());
    }

    @Transactional
    public PlmAcceptanceChecklistResponse updateAcceptanceChecklist(
            String businessType,
            String billId,
            String checklistId,
            PlmAcceptanceChecklistUpdateRequest request,
            String currentUserId
    ) {
        ChecklistRecord existing = selectChecklist(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), checklistId))
                .findFirst()
                .orElseThrow(() -> illegalTaskState("验收清单不存在", businessType, billId));
        String nextStatus = normalizeValue(request.status(), existing.status());
        String nextSummary = normalizeValue(request.resultSummary(), existing.resultSummary());
        LocalDateTime checkedAt = "ACCEPTED".equalsIgnoreCase(nextStatus) || "REJECTED".equalsIgnoreCase(nextStatus)
                ? LocalDateTime.now()
                : existing.checkedAt();
        jdbcTemplate.update(
                """
                UPDATE plm_acceptance_checklist
                SET status = ?,
                    result_summary = ?,
                    checked_by = ?,
                    checked_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                nextStatus,
                nextSummary,
                currentUserId,
                checkedAt,
                checklistId
        );
        return listAcceptanceChecklist(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), checklistId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PlmImplementationTaskResponse upsertTask(
            String businessType,
            String billId,
            PlmImplementationTaskUpsertRequest request,
            String currentUserId
    ) {
        PlmImplementationTaskRecord existing = request.taskId() == null || request.taskId().isBlank()
                ? null
                : plmImplementationTaskMapper.selectById(request.taskId());

        PlmImplementationTaskRecord record = new PlmImplementationTaskRecord(
                existing == null ? buildId("task") : existing.id(),
                businessType,
                billId,
                normalizeValue(request.templateId(), existing == null ? null : existing.templateId()),
                normalizeValue(request.templateCode(), existing == null ? null : existing.templateCode()),
                normalizeValue(request.taskNo(), existing == null ? nextTaskNo(businessType, billId) : existing.taskNo()),
                normalizeValue(request.taskTitle(), existing == null ? "实施任务" : existing.taskTitle()),
                normalizeValue(request.taskType(), existing == null ? "IMPLEMENTATION" : existing.taskType()),
                normalizeValue(request.ownerUserId(), existing == null ? currentUserId : existing.ownerUserId()),
                normalizeValue(request.status(), existing == null ? "PENDING" : existing.status()),
                request.plannedStartAt(),
                request.plannedEndAt(),
                existing == null ? null : existing.startedAt(),
                existing == null ? null : existing.completedAt(),
                normalizeValue(request.resultSummary(), existing == null ? null : existing.resultSummary()),
                request.requiredEvidenceCount() == null ? (existing == null ? 0 : safeInt(existing.requiredEvidenceCount())) : request.requiredEvidenceCount(),
                request.verificationRequired() == null ? (existing == null || Boolean.TRUE.equals(existing.verificationRequired())) : request.verificationRequired(),
                request.sortOrder() == null ? (existing == null ? 1 : existing.sortOrder()) : request.sortOrder(),
                existing == null ? null : existing.createdAt(),
                null
        );
        if (existing == null) {
            plmImplementationTaskMapper.insert(record);
        } else {
            plmImplementationTaskMapper.update(record);
        }
        return listBillTasks(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), record.id()))
                .findFirst()
                .orElseThrow();
    }

    public PlmImplementationTaskResponse startTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "RUNNING", true, false, request);
    }

    public PlmImplementationTaskResponse completeTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "COMPLETED", false, true, request);
    }

    public PlmImplementationTaskResponse blockTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "BLOCKED", false, false, request);
    }

    public PlmImplementationTaskResponse cancelTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "CANCELLED", false, true, request);
    }

    private PlmImplementationTaskResponse changeStatus(
            String businessType,
            String billId,
            String taskId,
            String status,
            boolean setStartedAt,
            boolean setCompletedAt,
            PlmImplementationTaskActionRequest request
    ) {
        PlmImplementationTaskRecord record = requireTask(businessType, billId, taskId);
        List<DependencyRecord> dependencies = selectDependencies(businessType, billId);
        Map<String, PlmImplementationTaskRecord> taskIndex = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .collect(Collectors.toMap(PlmImplementationTaskRecord::id, task -> task));
        if ("RUNNING".equalsIgnoreCase(status) && !dependencyReady(record, dependencies, taskIndex)) {
            throw illegalTaskState("存在未完成的前置任务，当前任务不能开始", businessType, billId);
        }
        LocalDateTime now = LocalDateTime.now();
        PlmImplementationTaskRecord updated = new PlmImplementationTaskRecord(
                record.id(),
                record.businessType(),
                record.billId(),
                record.templateId(),
                record.templateCode(),
                record.taskNo(),
                record.taskTitle(),
                record.taskType(),
                normalizeValue(request == null ? null : request.ownerUserId(), record.ownerUserId()),
                status,
                record.plannedStartAt(),
                record.plannedEndAt(),
                setStartedAt ? now : record.startedAt(),
                setCompletedAt ? now : record.completedAt(),
                normalizeValue(request == null ? null : request.resultSummary(), record.resultSummary()),
                safeInt(record.requiredEvidenceCount()),
                record.verificationRequired(),
                record.sortOrder(),
                record.createdAt(),
                null
        );
        plmImplementationTaskMapper.update(updated);
        return listBillTasks(businessType, billId).stream()
                .filter(item -> Objects.equals(item.id(), taskId))
                .findFirst()
                .orElseThrow();
    }

    private PlmImplementationTaskRecord requireTask(String businessType, String billId, String taskId) {
        PlmImplementationTaskRecord task = plmImplementationTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.businessType(), businessType) || !Objects.equals(task.billId(), billId)) {
            throw illegalTaskState("实施任务不存在", businessType, billId);
        }
        return task;
    }

    private String nextTaskNo(String businessType, String billId) {
        int count = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).size();
        return String.format("TASK-%03d", count + 1);
    }

    private PlmImplementationTaskResponse toResponse(
            PlmImplementationTaskRecord record,
            int evidenceCount,
            boolean dependencyReady,
            List<String> blockedByTaskIds
    ) {
        return new PlmImplementationTaskResponse(
                record.id(),
                record.businessType(),
                record.billId(),
                record.templateId(),
                record.templateCode(),
                record.taskNo(),
                record.taskTitle(),
                record.taskType(),
                record.ownerUserId(),
                record.status(),
                record.plannedStartAt(),
                record.plannedEndAt(),
                record.startedAt(),
                record.completedAt(),
                record.resultSummary(),
                safeInt(record.requiredEvidenceCount()),
                evidenceCount,
                dependencyReady,
                blockedByTaskIds,
                record.verificationRequired(),
                record.sortOrder(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private Map<String, String> resolveRoleOwners(String businessType, String billId) {
        return plmEnterpriseDepthService.listBillRoleAssignments(businessType, billId).stream()
                .filter(item -> item.assigneeUserId() != null && !item.assigneeUserId().isBlank())
                .collect(Collectors.toMap(
                        PlmRoleAssignmentResponse::roleCode,
                        PlmRoleAssignmentResponse::assigneeUserId,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<TemplateRecord> selectTemplates(String businessType, String sceneCode) {
        String normalizedSceneCode = sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
        List<TemplateRecord> scoped = queryTemplates(businessType, normalizedSceneCode);
        if (!scoped.isEmpty()) {
            return scoped;
        }
        List<TemplateRecord> fallback = queryTemplates(businessType, "default");
        return fallback.isEmpty() ? builtinTemplates(businessType) : fallback;
    }

    private List<TemplateRecord> builtinTemplates(String businessType) {
        String normalizedType = normalizeValue(businessType, "");
        if ("PLM_ECO".equalsIgnoreCase(normalizedType)) {
            return List.of(
                    builtinTemplate("PLM_ECO", "default", "ECO_MANUFACTURING_ROLLOUT", "ECO 生产切换", "ROLLOUT", "生产切换与现场执行", "PLM_MANUFACTURING_OWNER", 1, true, 1),
                    builtinTemplate("PLM_ECO", "default", "ECO_QUALITY_VERIFY", "ECO 质量确认", "VALIDATION", "质量确认与放行", "PLM_QUALITY_OWNER", 1, true, 2),
                    builtinTemplate("PLM_ECO", "default", "ECO_ERP_SYNC", "ECO ERP 同步", "SYNC", "ERP / MES 同步确认", "PLM_ERP_OWNER", 1, true, 3)
            );
        }
        if ("PLM_MATERIAL".equalsIgnoreCase(normalizedType)) {
            return List.of(
                    builtinTemplate("PLM_MATERIAL", "default", "MAT_MASTER_UPDATE", "物料主数据更新", "DATA_CHANGE", "主数据更新执行", "PLM_DATA_STEWARD", 1, true, 1),
                    builtinTemplate("PLM_MATERIAL", "default", "MAT_ERP_SYNC", "物料 ERP 同步", "SYNC", "ERP 编码与主数据同步", "PLM_ERP_OWNER", 1, true, 2),
                    builtinTemplate("PLM_MATERIAL", "default", "MAT_CHANGE_CONFIRM", "物料变更确认", "CONFIRM", "变更确认与关闭准备", "PLM_CHANGE_MANAGER", 1, true, 3)
            );
        }
        return List.of(
                builtinTemplate("PLM_ECR", "default", "ECR_IMPL_PLAN", "ECR 实施计划", "IMPLEMENTATION", "工程变更实施", "PLM_CHANGE_MANAGER", 1, true, 1),
                builtinTemplate("PLM_ECR", "default", "ECR_DOC_RELEASE", "ECR 文档发布", "DOCUMENT", "图纸与文档发布", "PLM_DOC_CONTROLLER", 1, true, 2),
                builtinTemplate("PLM_ECR", "default", "ECR_QUALITY_VERIFY", "ECR 质量验证", "VALIDATION", "质量验证与归档", "PLM_QUALITY_OWNER", 1, true, 3)
        );
    }

    private TemplateRecord builtinTemplate(
            String businessType,
            String sceneCode,
            String templateCode,
            String templateName,
            String taskType,
            String defaultTaskTitle,
            String defaultOwnerRoleCode,
            int requiredEvidenceCount,
            boolean verificationRequired,
            int sortOrder
    ) {
        return new TemplateRecord(
                "builtin_" + templateCode.toLowerCase(),
                businessType,
                sceneCode,
                templateCode,
                templateName,
                taskType,
                defaultTaskTitle,
                defaultOwnerRoleCode,
                requiredEvidenceCount,
                verificationRequired,
                sortOrder,
                true
        );
    }

    private List<TemplateRecord> queryTemplates(String businessType, String sceneCode) {
        return jdbcTemplate.query(
                """
                SELECT id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
                       default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled
                FROM plm_implementation_template
                WHERE business_type = ?
                  AND scene_code = ?
                  AND enabled = TRUE
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new TemplateRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("scene_code"),
                        rs.getString("template_code"),
                        rs.getString("template_name"),
                        rs.getString("task_type"),
                        rs.getString("default_task_title"),
                        rs.getString("default_owner_role_code"),
                        rs.getInt("required_evidence_count"),
                        rs.getBoolean("verification_required"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("enabled")
                ),
                businessType,
                sceneCode
        );
    }

    private void buildDependenciesFromTemplates(
            String businessType,
            String billId,
            List<TemplateRecord> templates,
            Map<String, PlmImplementationTaskRecord> generatedTasks
    ) {
        String sceneCode = resolveSceneCode(businessType, billId);
        List<TemplateDependencyRecord> templateDependencies = selectTemplateDependencies(businessType, sceneCode);
        if (!templateDependencies.isEmpty()) {
            for (TemplateDependencyRecord dependency : templateDependencies) {
                PlmImplementationTaskRecord predecessor = generatedTasks.get(dependency.predecessorTemplateCode());
                PlmImplementationTaskRecord successor = generatedTasks.get(dependency.successorTemplateCode());
                if (predecessor == null || successor == null) {
                    continue;
                }
                jdbcTemplate.update(
                        """
                        INSERT INTO plm_implementation_task_dependency (
                            id, business_type, bill_id, predecessor_task_id, successor_task_id, dependency_type, required_flag, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                        buildId("dep"),
                        businessType,
                        billId,
                        predecessor.id(),
                        successor.id(),
                        dependency.dependencyType(),
                        dependency.requiredFlag()
                );
            }
            return;
        }
        TemplateRecord previous = null;
        for (TemplateRecord template : templates) {
            if (previous != null) {
                PlmImplementationTaskRecord predecessor = generatedTasks.get(previous.templateCode());
                PlmImplementationTaskRecord successor = generatedTasks.get(template.templateCode());
                if (predecessor != null && successor != null) {
                    jdbcTemplate.update(
                            """
                            INSERT INTO plm_implementation_task_dependency (
                                id, business_type, bill_id, predecessor_task_id, successor_task_id, dependency_type, required_flag, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, 'FINISH_TO_START', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """,
                            buildId("dep"),
                            businessType,
                            billId,
                            predecessor.id(),
                            successor.id()
                    );
                }
            }
            previous = template;
        }
    }

    private List<DependencyRecord> selectDependencies(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id, business_type, bill_id, predecessor_task_id, successor_task_id, dependency_type, required_flag
                FROM plm_implementation_task_dependency
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new DependencyRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("predecessor_task_id"),
                        rs.getString("successor_task_id"),
                        rs.getString("dependency_type"),
                        rs.getBoolean("required_flag")
                ),
                businessType,
                billId
        );
    }

    private boolean dependencyReady(
            PlmImplementationTaskRecord task,
            List<DependencyRecord> dependencies,
            Map<String, PlmImplementationTaskRecord> taskIndex
    ) {
        return dependencies.stream()
                .filter(dep -> Objects.equals(dep.successorTaskId(), task.id()) && Boolean.TRUE.equals(dep.requiredFlag()))
                .allMatch(dep -> {
                    PlmImplementationTaskRecord predecessor = taskIndex.get(dep.predecessorTaskId());
                    return predecessor != null && "COMPLETED".equalsIgnoreCase(predecessor.status());
                });
    }

    private List<String> blockedByTaskIds(
            PlmImplementationTaskRecord task,
            List<DependencyRecord> dependencies,
            Map<String, PlmImplementationTaskRecord> taskIndex
    ) {
        return dependencies.stream()
                .filter(dep -> Objects.equals(dep.successorTaskId(), task.id()) && Boolean.TRUE.equals(dep.requiredFlag()))
                .filter(dep -> {
                    PlmImplementationTaskRecord predecessor = taskIndex.get(dep.predecessorTaskId());
                    return predecessor == null || !"COMPLETED".equalsIgnoreCase(predecessor.status());
                })
                .map(DependencyRecord::predecessorTaskId)
                .toList();
    }

    private List<EvidenceRecord> selectEvidence(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id, business_type, bill_id, task_id, evidence_type, evidence_name, evidence_ref, evidence_summary, uploaded_by, created_at
                FROM plm_implementation_task_evidence
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new EvidenceRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("task_id"),
                        rs.getString("evidence_type"),
                        rs.getString("evidence_name"),
                        rs.getString("evidence_ref"),
                        rs.getString("evidence_summary"),
                        rs.getString("uploaded_by"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                businessType,
                billId
        );
    }

    private int evidenceCount(String taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM plm_implementation_task_evidence WHERE task_id = ?",
                Integer.class,
                taskId
        );
        return count == null ? 0 : count;
    }

    private Map<String, Integer> queryEvidenceCounts(String businessType, String billId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT task_id, COUNT(1) AS total_count
                FROM plm_implementation_task_evidence
                WHERE business_type = ?
                  AND bill_id = ?
                GROUP BY task_id
                """,
                (rs, rowNum) -> {
                    counts.put(rs.getString("task_id"), rs.getInt("total_count"));
                    return null;
                },
                businessType,
                billId
        );
        return counts;
    }

    private void ensureDefaultChecklist(String businessType, String billId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM plm_acceptance_checklist WHERE business_type = ? AND bill_id = ?",
                Integer.class,
                businessType,
                billId
        );
        if (count != null && count > 0) {
            return;
        }
        String sceneCode = resolveSceneCode(businessType, billId);
        List<ChecklistSeed> seeds = selectChecklistTemplates(businessType, sceneCode);
        if (seeds.isEmpty()) {
            seeds = List.of(
                    new ChecklistSeed("SCOPE_CONFIRMED", "实施范围已确认", true, 1),
                    new ChecklistSeed("EVIDENCE_ARCHIVED", "实施证据已归档", true, 2),
                    new ChecklistSeed("EXTERNAL_SYNC_VERIFIED", "外部系统同步结果已核对", true, 3)
            );
        }
        for (ChecklistSeed seed : seeds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_acceptance_checklist (
                        id, business_type, bill_id, check_code, check_name, required_flag, status, result_summary, checked_by, checked_at, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, NULL, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    buildId("chk"),
                    businessType,
                    billId,
                    seed.checkCode(),
                    seed.checkName(),
                    seed.requiredFlag(),
                    seed.sortOrder()
            );
        }
    }

    private List<ChecklistSeed> selectChecklistTemplates(String businessType, String sceneCode) {
        String normalizedSceneCode = sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
        List<ChecklistSeed> scoped = queryChecklistTemplates(businessType, normalizedSceneCode);
        if (!scoped.isEmpty()) {
            return scoped;
        }
        return queryChecklistTemplates(businessType, "default");
    }

    private List<ChecklistSeed> queryChecklistTemplates(String businessType, String sceneCode) {
        return jdbcTemplate.query(
                """
                SELECT check_code, check_name, required_flag, sort_order
                FROM plm_acceptance_checklist_template
                WHERE business_type = ?
                  AND scene_code = ?
                  AND enabled = TRUE
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new ChecklistSeed(
                        rs.getString("check_code"),
                        rs.getString("check_name"),
                        rs.getBoolean("required_flag"),
                        rs.getInt("sort_order")
                ),
                businessType,
                sceneCode
        );
    }

    private List<TemplateDependencyRecord> selectTemplateDependencies(String businessType, String sceneCode) {
        String normalizedSceneCode = sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
        List<TemplateDependencyRecord> scoped = queryTemplateDependencies(businessType, normalizedSceneCode);
        if (!scoped.isEmpty()) {
            return scoped;
        }
        return queryTemplateDependencies(businessType, "default");
    }

    private List<TemplateDependencyRecord> queryTemplateDependencies(String businessType, String sceneCode) {
        return jdbcTemplate.query(
                """
                SELECT predecessor_template_code, successor_template_code, dependency_type, required_flag
                FROM plm_implementation_template_dependency
                WHERE business_type = ?
                  AND scene_code = ?
                  AND enabled = TRUE
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new TemplateDependencyRecord(
                        rs.getString("predecessor_template_code"),
                        rs.getString("successor_template_code"),
                        rs.getString("dependency_type"),
                        rs.getBoolean("required_flag")
                ),
                businessType,
                sceneCode
        );
    }

    private String resolveSceneCode(String businessType, String billId) {
        return switch (normalizeValue(businessType, "")) {
            case "PLM_ECR" -> jdbcTemplate.queryForObject("SELECT scene_code FROM plm_ecr_change WHERE id = ?", String.class, billId);
            case "PLM_ECO" -> jdbcTemplate.queryForObject("SELECT scene_code FROM plm_eco_execution WHERE id = ?", String.class, billId);
            case "PLM_MATERIAL" -> jdbcTemplate.queryForObject("SELECT scene_code FROM plm_material_change WHERE id = ?", String.class, billId);
            default -> "default";
        };
    }

    private List<ChecklistRecord> selectChecklist(String businessType, String billId) {
        return jdbcTemplate.query(
                """
                SELECT id, business_type, bill_id, check_code, check_name, required_flag, status, result_summary, checked_by, checked_at, sort_order
                FROM plm_acceptance_checklist
                WHERE business_type = ?
                  AND bill_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """,
                (rs, rowNum) -> new ChecklistRecord(
                        rs.getString("id"),
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("check_code"),
                        rs.getString("check_name"),
                        rs.getBoolean("required_flag"),
                        rs.getString("status"),
                        rs.getString("result_summary"),
                        rs.getString("checked_by"),
                        rs.getTimestamp("checked_at") == null ? null : rs.getTimestamp("checked_at").toLocalDateTime(),
                        rs.getInt("sort_order")
                ),
                businessType,
                billId
        );
    }

    private String labelTaskNo(String taskId, Map<String, PlmImplementationTaskRecord> taskIndex) {
        return taskIndex.containsKey(taskId) ? taskIndex.get(taskId).taskNo() : null;
    }

    private String labelTaskTitle(String taskId, Map<String, PlmImplementationTaskRecord> taskIndex) {
        return taskIndex.containsKey(taskId) ? taskIndex.get(taskId).taskTitle() : null;
    }

    private String defaultTaskTitle(TemplateRecord template, String fallback) {
        if (template.defaultTaskTitle() != null && !template.defaultTaskTitle().isBlank()) {
            return template.defaultTaskTitle();
        }
        return fallback == null || fallback.isBlank() ? "实施任务" : fallback.trim();
    }

    private String normalizeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private ContractException illegalTaskState(String message, String businessType, String billId) {
        return new ContractException(
                "PLM.TASK_STATE",
                HttpStatus.CONFLICT,
                message,
                java.util.Map.of(
                        "businessType", businessType,
                        "billId", billId
                )
        );
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record TemplateRecord(
            String id,
            String businessType,
            String sceneCode,
            String templateCode,
            String templateName,
            String taskType,
            String defaultTaskTitle,
            String defaultOwnerRoleCode,
            Integer requiredEvidenceCount,
            Boolean verificationRequired,
            Integer sortOrder,
            Boolean enabled
    ) {
    }

    private record DependencyRecord(
            String id,
            String businessType,
            String billId,
            String predecessorTaskId,
            String successorTaskId,
            String dependencyType,
            Boolean requiredFlag
    ) {
    }

    private record EvidenceRecord(
            String id,
            String businessType,
            String billId,
            String taskId,
            String evidenceType,
            String evidenceName,
            String evidenceRef,
            String evidenceSummary,
            String uploadedBy,
            LocalDateTime createdAt
    ) {
    }

    private record ChecklistRecord(
            String id,
            String businessType,
            String billId,
            String checkCode,
            String checkName,
            Boolean requiredFlag,
            String status,
            String resultSummary,
            String checkedBy,
            LocalDateTime checkedAt,
            Integer sortOrder
    ) {
    }

    private record ChecklistSeed(
            String checkCode,
            String checkName,
            Boolean requiredFlag,
            Integer sortOrder
    ) {
    }

    private record TemplateDependencyRecord(
            String predecessorTemplateCode,
            String successorTemplateCode,
            String dependencyType,
            Boolean requiredFlag
    ) {
    }
}
