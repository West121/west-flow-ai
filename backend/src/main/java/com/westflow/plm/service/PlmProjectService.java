package com.westflow.plm.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.plm.api.CreatePlmProjectRequest;
import com.westflow.plm.api.PlmProjectDashboardResponse;
import com.westflow.plm.api.PlmProjectDetailResponse;
import com.westflow.plm.api.PlmProjectLinkRequest;
import com.westflow.plm.api.PlmProjectLinkResponse;
import com.westflow.plm.api.PlmProjectListItemResponse;
import com.westflow.plm.api.PlmProjectMemberRequest;
import com.westflow.plm.api.PlmProjectMilestoneRequest;
import com.westflow.plm.api.PlmProjectPhaseTransitionRequest;
import com.westflow.plm.api.UpdatePlmProjectRequest;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.action.FlowableRuntimeStartService;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import com.westflow.plm.mapper.PlmProjectLinkMapper;
import com.westflow.plm.mapper.PlmProjectMapper;
import com.westflow.plm.mapper.PlmProjectMemberMapper;
import com.westflow.plm.mapper.PlmProjectMilestoneMapper;
import com.westflow.plm.mapper.PlmProjectStageEventMapper;
import com.westflow.plm.model.PlmProjectLinkRecord;
import com.westflow.plm.model.PlmProjectMemberRecord;
import com.westflow.plm.model.PlmProjectMilestoneRecord;
import com.westflow.plm.model.PlmProjectRecord;
import com.westflow.plm.model.PlmProjectStageEventRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM 项目管理服务。
 */
@Service
@RequiredArgsConstructor
public class PlmProjectService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final PlmProjectMapper plmProjectMapper;
    private final PlmProjectMemberMapper plmProjectMemberMapper;
    private final PlmProjectMilestoneMapper plmProjectMilestoneMapper;
    private final PlmProjectLinkMapper plmProjectLinkMapper;
    private final PlmProjectStageEventMapper plmProjectStageEventMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BusinessProcessBindingService businessProcessBindingService;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final FlowableEngineFacade flowableEngineFacade;

    @Transactional
    public PlmProjectDetailResponse createProject(CreatePlmProjectRequest request) {
        String projectId = UUID.randomUUID().toString().replace("-", "");
        String creatorUserId = currentUserId();
        plmProjectMapper.insert(new PlmProjectRecord(
                projectId,
                generateProjectNo(),
                request.projectCode().trim(),
                request.projectName().trim(),
                request.projectType().trim(),
                trimToNull(request.projectLevel()),
                "PLANNING",
                "INITIATION",
                trimToNull(request.ownerUserId()),
                trimToNull(request.sponsorUserId()),
                trimToNull(request.domainCode()),
                trimToNull(request.priorityLevel()),
                trimToNull(request.targetRelease()),
                request.startDate(),
                request.targetEndDate(),
                null,
                trimToNull(request.summary()),
                trimToNull(request.businessGoal()),
                trimToNull(request.riskSummary()),
                creatorUserId,
                "DRAFT",
                "default",
                null,
                null,
                null
        ));
        replaceProjectChildren(projectId, request.members(), request.milestones(), request.links());
        appendStageEvent(projectId, null, "INITIATION", "CREATE", "项目已创建", creatorUserId);
        return detail(projectId);
    }

    @Transactional
    public PlmProjectDetailResponse updateProject(String projectId, UpdatePlmProjectRequest request) {
        PlmProjectRecord existing = requireWritableProject(projectId);
        plmProjectMapper.update(new PlmProjectRecord(
                existing.id(),
                existing.projectNo(),
                existing.projectCode(),
                request.projectName().trim(),
                request.projectType().trim(),
                trimToNull(request.projectLevel()),
                normalizeStatus(request.status(), request.phaseCode()),
                normalizePhaseCode(request.phaseCode(), existing.phaseCode()),
                trimToNull(request.ownerUserId()),
                trimToNull(request.sponsorUserId()),
                trimToNull(request.domainCode()),
                trimToNull(request.priorityLevel()),
                trimToNull(request.targetRelease()),
                request.startDate(),
                request.targetEndDate(),
                request.actualEndDate(),
                trimToNull(request.summary()),
                trimToNull(request.businessGoal()),
                trimToNull(request.riskSummary()),
                existing.creatorUserId(),
                existing.initiationStatus(),
                existing.initiationSceneCode(),
                existing.initiationProcessInstanceId(),
                existing.initiationSubmittedAt(),
                existing.initiationDecidedAt()
        ));
        replaceProjectChildren(projectId, request.members(), request.milestones(), request.links());
        return detail(projectId);
    }

    @Transactional
    public PlmProjectDetailResponse submitInitiation(String projectId) {
        PlmProjectRecord project = requireWritableProject(projectId);
        String initiationStatus = normalizeInitiationStatus(project.initiationStatus());
        if (!List.of("DRAFT", "REJECTED", "CANCELLED").contains(initiationStatus)) {
            throw new ContractException(
                    "PLM.PROJECT_INITIATION_NOT_SUBMITTABLE",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前项目状态不支持提交立项审批",
                    Map.of("projectId", projectId, "initiationStatus", initiationStatus)
            );
        }
        String sceneCode = trimToNull(project.initiationSceneCode()) == null ? "default" : trimToNull(project.initiationSceneCode());
        StartProcessResponse startResponse = startInitiationProcess(project, sceneCode);
        plmProjectMapper.updateInitiation(
                projectId,
                "PENDING_APPROVAL",
                sceneCode,
                startResponse.instanceId(),
                LocalDateTime.now(),
                null
        );
        runtimeBusinessLinkService.insertLink(
                "PLM_PROJECT",
                projectId,
                startResponse.instanceId(),
                startResponse.processDefinitionId(),
                project.creatorUserId(),
                startResponse.status()
        );
        return detail(projectId);
    }

    @Transactional
    public PlmProjectDetailResponse cancelInitiation(String projectId) {
        PlmProjectRecord project = requireWritableProject(projectId);
        String initiationStatus = normalizeInitiationStatus(project.initiationStatus());
        if (!List.of("DRAFT", "PENDING_APPROVAL", "REJECTED").contains(initiationStatus)) {
            throw new ContractException(
                    "PLM.PROJECT_INITIATION_NOT_CANCELLABLE",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前项目状态不支持撤回立项",
                    Map.of("projectId", projectId, "initiationStatus", initiationStatus)
            );
        }
        String processInstanceId = trimToNull(project.initiationProcessInstanceId());
        if ("PENDING_APPROVAL".equals(initiationStatus) && processInstanceId != null) {
            try {
                flowableEngineFacade.runtimeService().deleteProcessInstance(processInstanceId, "WESTFLOW_REVOKED");
            } catch (FlowableObjectNotFoundException ignored) {
                // 流程实例已经结束时，交给状态同步逻辑兜底。
            }
            runtimeBusinessLinkService.updateStatus(processInstanceId, "REVOKED");
        }
        plmProjectMapper.updateInitiation(
                projectId,
                "CANCELLED",
                project.initiationSceneCode(),
                processInstanceId,
                project.initiationSubmittedAt(),
                LocalDateTime.now()
        );
        return detail(projectId);
    }

    public PageResponse<PlmProjectListItemResponse> page(PageRequest request) {
        String keyword = trimToNull(request.keyword());
        String status = stringFilter(request.filters(), "status");
        String phaseCode = stringFilter(request.filters(), "phaseCode");
        String ownerUserId = stringFilter(request.filters(), "ownerUserId");
        String domainCode = stringFilter(request.filters(), "domainCode");
        LocalDate targetEndDateFrom = dateFilter(request.filters(), "targetEndDate", "gte");
        LocalDate targetEndDateTo = dateFilter(request.filters(), "targetEndDate", "lte");
        long total = plmProjectMapper.countPage(
                keyword,
                status,
                phaseCode,
                ownerUserId,
                domainCode,
                targetEndDateFrom,
                targetEndDateTo
        );
        List<PlmProjectListItemResponse> records = total == 0
                ? List.of()
                : plmProjectMapper.selectPage(
                        keyword,
                        status,
                        phaseCode,
                        ownerUserId,
                        domainCode,
                        targetEndDateFrom,
                        targetEndDateTo,
                        resolveOrderBy(request),
                        resolveOrderDirection(request),
                        request.pageSize(),
                        (long) (request.page() - 1) * request.pageSize()
                );
        if (!records.isEmpty()) {
            boolean changed = false;
            for (PlmProjectListItemResponse item : records) {
                changed |= synchronizeInitiationState(item.projectId());
            }
            if (changed) {
                records = plmProjectMapper.selectPage(
                        keyword,
                        status,
                        phaseCode,
                        ownerUserId,
                        domainCode,
                        targetEndDateFrom,
                        targetEndDateTo,
                        resolveOrderBy(request),
                        resolveOrderDirection(request),
                        request.pageSize(),
                        (long) (request.page() - 1) * request.pageSize()
                );
            }
        }
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / request.pageSize());
        return new PageResponse<>(
                request.page(),
                request.pageSize(),
                total,
                pages,
                records,
                List.of()
        );
    }

    public PlmProjectDetailResponse detail(String projectId) {
        synchronizeInitiationState(projectId);
        PlmProjectDetailResponse base = requireReadableProject(projectId);
        return new PlmProjectDetailResponse(
                base.projectId(),
                base.projectNo(),
                base.projectCode(),
                base.projectName(),
                base.projectType(),
                base.projectLevel(),
                base.status(),
                base.phaseCode(),
                base.ownerUserId(),
                base.ownerDisplayName(),
                base.sponsorUserId(),
                base.sponsorDisplayName(),
                base.domainCode(),
                base.priorityLevel(),
                base.targetRelease(),
                base.startDate(),
                base.targetEndDate(),
                base.actualEndDate(),
                base.summary(),
                base.businessGoal(),
                base.riskSummary(),
                base.creatorUserId(),
                base.creatorDisplayName(),
                base.initiationStatus(),
                base.initiationSceneCode(),
                base.initiationProcessInstanceId(),
                base.initiationSubmittedAt(),
                base.initiationDecidedAt(),
                base.createdAt(),
                base.updatedAt(),
                plmProjectMemberMapper.selectByProjectId(projectId),
                plmProjectMilestoneMapper.selectByProjectId(projectId),
                plmProjectLinkMapper.selectByProjectId(projectId),
                plmProjectStageEventMapper.selectByProjectId(projectId),
                dashboard(projectId)
        );
    }

    public PlmProjectDashboardResponse dashboard(String projectId) {
        synchronizeInitiationState(projectId);
        requireReadableProject(projectId);
        long memberCount = count("SELECT COUNT(1) FROM plm_project_member WHERE project_id = ?", projectId);
        long milestoneCount = count("SELECT COUNT(1) FROM plm_project_milestone WHERE project_id = ?", projectId);
        long openMilestoneCount = count(
                "SELECT COUNT(1) FROM plm_project_milestone WHERE project_id = ? AND status NOT IN ('COMPLETED','CANCELLED')",
                projectId
        );
        long overdueMilestoneCount = count(
                "SELECT COUNT(1) FROM plm_project_milestone WHERE project_id = ? AND status NOT IN ('COMPLETED','CANCELLED') AND planned_at IS NOT NULL AND planned_at < CURRENT_TIMESTAMP",
                projectId
        );
        long billLinkCount = count(
                "SELECT COUNT(1) FROM plm_project_link WHERE project_id = ? AND link_type = 'PLM_BILL'",
                projectId
        );
        long objectLinkCount = count(
                "SELECT COUNT(1) FROM plm_project_link WHERE project_id = ? AND link_type IN ('OBJECT','BOM','DOCUMENT','BASELINE')",
                projectId
        );
        long taskLinkCount = count(
                "SELECT COUNT(1) FROM plm_project_link WHERE project_id = ? AND link_type = 'IMPLEMENTATION_TASK'",
                projectId
        );
        List<PlmProjectDashboardResponse.DistributionItem> linkTypeDistribution = jdbcTemplate.query(
                """
                SELECT link_type AS code, link_type AS label, COUNT(1) AS total_count
                FROM plm_project_link
                WHERE project_id = ?
                GROUP BY link_type
                ORDER BY total_count DESC, code ASC
                """,
                (rs, rowNum) -> new PlmProjectDashboardResponse.DistributionItem(
                        rs.getString("code"),
                        rs.getString("label"),
                        rs.getLong("total_count")
                ),
                projectId
        );
        List<PlmProjectDashboardResponse.DistributionItem> milestoneStatusDistribution = jdbcTemplate.query(
                """
                SELECT status AS code, status AS label, COUNT(1) AS total_count
                FROM plm_project_milestone
                WHERE project_id = ?
                GROUP BY status
                ORDER BY total_count DESC, code ASC
                """,
                (rs, rowNum) -> new PlmProjectDashboardResponse.DistributionItem(
                        rs.getString("code"),
                        rs.getString("label"),
                        rs.getLong("total_count")
                ),
                projectId
        );
        List<PlmProjectDashboardResponse.HighlightItem> recentRisks = new ArrayList<>();
        recentRisks.addAll(jdbcTemplate.query(
                """
                SELECT id, milestone_name, status, COALESCE(summary, '里程碑已临近或超出计划时间') AS hint
                FROM plm_project_milestone
                WHERE project_id = ?
                  AND status NOT IN ('COMPLETED','CANCELLED')
                  AND planned_at IS NOT NULL
                  AND planned_at < CURRENT_TIMESTAMP
                ORDER BY planned_at ASC
                LIMIT 3
                """,
                (rs, rowNum) -> new PlmProjectDashboardResponse.HighlightItem(
                        rs.getString("id"),
                        rs.getString("milestone_name"),
                        rs.getString("status"),
                        rs.getString("hint")
                ),
                projectId
        ));
        recentRisks.addAll(jdbcTemplate.query(
                """
                SELECT id, COALESCE(target_title, target_no, target_id) AS title, COALESCE(target_status, 'UNKNOWN') AS status,
                       COALESCE(summary, '关联对象需要项目负责人确认') AS hint
                FROM plm_project_link
                WHERE project_id = ?
                  AND target_status IN ('RUNNING','BLOCKED','REJECTED')
                ORDER BY sort_order ASC, created_at ASC
                LIMIT 3
                """,
                (rs, rowNum) -> new PlmProjectDashboardResponse.HighlightItem(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("hint")
                ),
                projectId
        ));
        return new PlmProjectDashboardResponse(
                memberCount,
                milestoneCount,
                openMilestoneCount,
                overdueMilestoneCount,
                billLinkCount,
                objectLinkCount,
                taskLinkCount,
                linkTypeDistribution,
                milestoneStatusDistribution,
                recentRisks.stream().limit(6).toList()
        );
    }

    @Transactional
    public PlmProjectDetailResponse transitionPhase(String projectId, PlmProjectPhaseTransitionRequest request) {
        PlmProjectRecord project = requireWritableProject(projectId);
        String nextPhaseCode = normalizePhaseCode(request.toPhaseCode(), project.phaseCode());
        ensureInitiationApprovedForTransition(project, nextPhaseCode);
        String nextStatus = normalizeStatus(request.status(), nextPhaseCode);
        plmProjectMapper.updatePhase(projectId, nextPhaseCode, nextStatus, request.actualEndDate());
        appendStageEvent(projectId, project.phaseCode(), nextPhaseCode, request.actionCode(), trimToNull(request.comment()), currentUserId());
        return detail(projectId);
    }

    public List<com.westflow.plm.api.PlmProjectMemberResponse> members(String projectId) {
        requireReadableProject(projectId);
        return plmProjectMemberMapper.selectByProjectId(projectId);
    }

    public List<com.westflow.plm.api.PlmProjectMilestoneResponse> milestones(String projectId) {
        requireReadableProject(projectId);
        return plmProjectMilestoneMapper.selectByProjectId(projectId);
    }

    public List<PlmProjectLinkResponse> links(String projectId) {
        requireReadableProject(projectId);
        return plmProjectLinkMapper.selectByProjectId(projectId);
    }

    public List<com.westflow.plm.api.PlmProjectStageEventResponse> stageEvents(String projectId) {
        requireReadableProject(projectId);
        return plmProjectStageEventMapper.selectByProjectId(projectId);
    }

    private void replaceProjectChildren(
            String projectId,
            List<PlmProjectMemberRequest> members,
            List<PlmProjectMilestoneRequest> milestones,
            List<PlmProjectLinkRequest> links
    ) {
        plmProjectMemberMapper.deleteByProjectId(projectId);
        plmProjectMilestoneMapper.deleteByProjectId(projectId);
        plmProjectLinkMapper.deleteByProjectId(projectId);
        for (int index = 0; index < members.size(); index++) {
            PlmProjectMemberRequest member = members.get(index);
            plmProjectMemberMapper.insert(new PlmProjectMemberRecord(
                    UUID.randomUUID().toString().replace("-", ""),
                    projectId,
                    member.userId().trim(),
                    member.roleCode().trim(),
                    member.roleLabel().trim(),
                    trimToNull(member.responsibilitySummary()),
                    index
            ));
        }
        for (int index = 0; index < milestones.size(); index++) {
            PlmProjectMilestoneRequest milestone = milestones.get(index);
            plmProjectMilestoneMapper.insert(new PlmProjectMilestoneRecord(
                    UUID.randomUUID().toString().replace("-", ""),
                    projectId,
                    milestone.milestoneCode().trim(),
                    milestone.milestoneName().trim(),
                    milestone.status().trim(),
                    trimToNull(milestone.ownerUserId()),
                    milestone.plannedAt(),
                    milestone.actualAt(),
                    trimToNull(milestone.summary()),
                    index
            ));
        }
        for (int index = 0; index < links.size(); index++) {
            PlmProjectLinkRequest link = links.get(index);
            plmProjectLinkMapper.insert(new PlmProjectLinkRecord(
                    UUID.randomUUID().toString().replace("-", ""),
                    projectId,
                    link.linkType().trim(),
                    trimToNull(link.targetBusinessType()),
                    link.targetId().trim(),
                    trimToNull(link.targetNo()),
                    trimToNull(link.targetTitle()),
                    trimToNull(link.targetStatus()),
                    trimToNull(link.targetHref()),
                    trimToNull(link.summary()),
                    index
            ));
        }
    }

    private void appendStageEvent(
            String projectId,
            String fromPhaseCode,
            String toPhaseCode,
            String actionCode,
            String comment,
            String changedBy
    ) {
        plmProjectStageEventMapper.insert(new PlmProjectStageEventRecord(
                UUID.randomUUID().toString().replace("-", ""),
                projectId,
                fromPhaseCode,
                toPhaseCode,
                actionCode,
                comment,
                changedBy,
                LocalDateTime.now()
        ));
    }

    private PlmProjectDetailResponse requireReadableProject(String projectId) {
        PlmProjectDetailResponse detail = plmProjectMapper.selectDetail(projectId);
        if (detail == null) {
            throw new ContractException(
                    "PLM.PROJECT_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "PLM 项目不存在",
                    Map.of("projectId", projectId)
            );
        }
        return detail;
    }

    private PlmProjectRecord requireWritableProject(String projectId) {
        PlmProjectRecord record = plmProjectMapper.selectRecord(projectId);
        if (record == null) {
            throw new ContractException(
                    "PLM.PROJECT_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "PLM 项目不存在",
                    Map.of("projectId", projectId)
            );
        }
        if (synchronizeInitiationState(projectId)) {
            record = plmProjectMapper.selectRecord(projectId);
        }
        String currentUserId = currentUserId();
        if (!Objects.equals(currentUserId, record.creatorUserId()) && !Objects.equals(currentUserId, record.ownerUserId())) {
            throw new ContractException(
                    "PLM.PROJECT_FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "只有项目创建人或负责人可以修改项目",
                    Map.of("projectId", projectId, "currentUserId", currentUserId)
            );
        }
        return record;
    }

    private void ensureInitiationApprovedForTransition(PlmProjectRecord project, String nextPhaseCode) {
        if ("INITIATION".equalsIgnoreCase(nextPhaseCode)) {
            return;
        }
        if (!"APPROVED".equalsIgnoreCase(normalizeInitiationStatus(project.initiationStatus()))) {
            throw new ContractException(
                    "PLM.PROJECT_INITIATION_APPROVAL_REQUIRED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "项目立项审批通过后才能推进阶段",
                    Map.of(
                            "projectId", project.id(),
                            "phaseCode", nextPhaseCode,
                            "initiationStatus", project.initiationStatus()
                    )
            );
        }
    }

    private StartProcessResponse startInitiationProcess(PlmProjectRecord project, String sceneCode) {
        String processKey = businessProcessBindingService.resolveProcessKey("PLM_PROJECT", sceneCode);
        Map<String, Object> formData = new java.util.LinkedHashMap<>();
        formData.put("projectNo", project.projectNo());
        formData.put("projectCode", project.projectCode());
        formData.put("projectName", project.projectName());
        formData.put("projectType", project.projectType());
        formData.put("projectLevel", project.projectLevel());
        formData.put("ownerUserId", project.ownerUserId());
        formData.put("sponsorUserId", project.sponsorUserId());
        formData.put("domainCode", project.domainCode());
        formData.put("priorityLevel", project.priorityLevel());
        formData.put("targetRelease", project.targetRelease());
        formData.put("summary", project.summary());
        formData.put("businessGoal", project.businessGoal());
        formData.put("riskSummary", project.riskSummary());
        return flowableRuntimeStartService.start(new StartProcessRequest(
                processKey,
                project.id(),
                "PLM_PROJECT",
                formData
        ));
    }

    private boolean synchronizeInitiationState(String projectId) {
        PlmProjectRecord record = plmProjectMapper.selectRecord(projectId);
        if (record == null) {
            return false;
        }
        String normalizedStatus = normalizeInitiationStatus(record.initiationStatus());
        String sceneCode = trimToNull(record.initiationSceneCode()) == null ? "default" : trimToNull(record.initiationSceneCode());
        String processInstanceId = trimToNull(record.initiationProcessInstanceId());
        var link = runtimeBusinessLinkService.findByBusiness("PLM_PROJECT", projectId).orElse(null);
        if (processInstanceId == null && link != null) {
            processInstanceId = trimToNull(link.processInstanceId());
        }
        if (processInstanceId == null || List.of("DRAFT", "CANCELLED").contains(normalizedStatus)) {
            return false;
        }
        HistoricProcessInstance historicProcessInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        String derivedStatus = deriveInitiationStatus(processInstanceId, normalizedStatus, historicProcessInstance);
        LocalDateTime decidedAt = "PENDING_APPROVAL".equals(derivedStatus)
                ? null
                : resolveDecidedAt(record.initiationDecidedAt(), historicProcessInstance);
        if (Objects.equals(normalizedStatus, derivedStatus)
                && Objects.equals(processInstanceId, trimToNull(record.initiationProcessInstanceId()))
                && Objects.equals(record.initiationDecidedAt(), decidedAt)) {
            return false;
        }
        plmProjectMapper.updateInitiation(
                projectId,
                derivedStatus,
                sceneCode,
                processInstanceId,
                record.initiationSubmittedAt(),
                decidedAt
        );
        return true;
    }

    private String deriveInitiationStatus(
            String processInstanceId,
            String fallbackStatus,
            HistoricProcessInstance historicProcessInstance
    ) {
        try {
            long activeCount = flowableEngineFacade.taskService()
                    .createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .count();
            if (activeCount > 0) {
                return "PENDING_APPROVAL";
            }
        } catch (FlowableObjectNotFoundException ignored) {
            // 运行态实例不存在时继续走历史实例判断。
        }
        if (historicProcessInstance == null) {
            return fallbackStatus;
        }
        if (historicProcessInstance.getDeleteReason() != null) {
            return "CANCELLED";
        }
        Map<String, Object> variables = historicVariables(processInstanceId);
        String latestAction = trimToNull(stringValue(variables.get("westflowLastAction")));
        if (latestAction != null && List.of("REJECT", "RETURN").contains(latestAction.toUpperCase(Locale.ROOT))) {
            return "REJECTED";
        }
        return "APPROVED";
    }

    private Map<String, Object> historicVariables(String processInstanceId) {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private LocalDateTime resolveDecidedAt(LocalDateTime existing, HistoricProcessInstance historicProcessInstance) {
        if (existing != null) {
            return existing;
        }
        if (historicProcessInstance == null || historicProcessInstance.getEndTime() == null) {
            return LocalDateTime.now(TIME_ZONE);
        }
        return LocalDateTime.ofInstant(historicProcessInstance.getEndTime().toInstant(), TIME_ZONE);
    }

    private String normalizeInitiationStatus(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "DRAFT" : normalized.toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0L : result;
    }

    private String generateProjectNo() {
        return "PRJ-" + System.currentTimeMillis();
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhaseCode(String requested, String fallback) {
        String value = trimToNull(requested);
        return value == null ? fallback : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String requested, String phaseCode) {
        String value = trimToNull(requested);
        if (value != null) {
            return value.toUpperCase(Locale.ROOT);
        }
        return switch (normalizePhaseCode(phaseCode, "INITIATION")) {
            case "CLOSED" -> "COMPLETED";
            case "ON_HOLD" -> "ON_HOLD";
            default -> "ACTIVE";
        };
    }

    private String stringFilter(List<FilterItem> filters, String field) {
        for (FilterItem filter : filters) {
            if (field.equals(filter.field()) && "eq".equalsIgnoreCase(filter.operator())) {
                JsonNode value = filter.value();
                if (value != null && value.isTextual()) {
                    return trimToNull(value.asText());
                }
            }
        }
        return null;
    }

    private LocalDate dateFilter(List<FilterItem> filters, String field, String operator) {
        for (FilterItem filter : filters) {
            if (field.equals(filter.field()) && operator.equalsIgnoreCase(filter.operator())) {
                JsonNode value = filter.value();
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return LocalDate.parse(value.asText());
                }
            }
        }
        return null;
    }

    private String resolveOrderBy(PageRequest request) {
        if (request.sorts().isEmpty()) {
            return "p.updated_at";
        }
        String field = request.sorts().get(0).field();
        return switch (field) {
            case "projectNo" -> "p.project_no";
            case "projectCode" -> "p.project_code";
            case "projectName" -> "p.project_name";
            case "status" -> "p.status";
            case "phaseCode" -> "p.phase_code";
            case "targetEndDate" -> "p.target_end_date";
            case "updatedAt" -> "p.updated_at";
            default -> "p.updated_at";
        };
    }

    private String resolveOrderDirection(PageRequest request) {
        if (request.sorts().isEmpty()) {
            return "DESC";
        }
        return "ASC".equalsIgnoreCase(request.sorts().get(0).direction()) ? "ASC" : "DESC";
    }
}
