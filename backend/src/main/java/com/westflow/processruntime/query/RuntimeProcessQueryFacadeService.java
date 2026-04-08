package com.westflow.processruntime.query;

import com.westflow.common.api.RequestContext;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.mapper.IdentityAccessMapper;
import com.westflow.processruntime.action.FlowableCountersignService;
import com.westflow.processruntime.action.RuntimeActionSupportService;
import com.westflow.processruntime.action.RuntimeProcessActionSupportService;
import com.westflow.processruntime.action.RuntimeTaskActionSupportService;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.response.InclusiveGatewayHitResponse;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.response.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.api.response.WorkbenchDashboardSummaryResponse;
import com.westflow.processruntime.link.BusinessLinkSnapshot;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import com.westflow.processruntime.trace.RuntimeInstanceEventRecorder;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import com.westflow.workflowadmin.service.WorkflowOperationLogService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeProcessQueryFacadeService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final IdentityAccessMapper identityAccessMapper;
    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableCountersignService flowableCountersignService;
    private final RuntimeTaskQueryService runtimeTaskQueryService;
    private final RuntimeApprovalSheetQueryService runtimeApprovalSheetQueryService;
    private final RuntimeTaskDetailQueryService runtimeTaskDetailQueryService;
    private final RuntimeProcessPredictionAnalyticsService runtimeProcessPredictionAnalyticsService;
    private final RuntimeProcessPredictionGovernanceService runtimeProcessPredictionGovernanceService;
    private final RuntimeProcessLinkQueryService runtimeProcessLinkQueryService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final ProcessLinkService processLinkService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeInstanceEventRecorder runtimeInstanceEventRecorder;
    private final WorkflowOperationLogService workflowOperationLogService;

    public PageResponse<ProcessTaskListItemResponse> page(PageRequest request) {
        return runtimeTaskQueryService.page(request, actionSupportService.currentUserId());
    }

    public PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request) {
        return runtimeApprovalSheetQueryService.pageApprovalSheets(request, actionSupportService.currentUserId());
    }

    public PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request, String userId) {
        String effectiveUserId = userId == null || userId.isBlank() ? actionSupportService.currentUserId() : userId;
        return runtimeApprovalSheetQueryService.pageApprovalSheets(request, effectiveUserId);
    }

    public WorkbenchDashboardSummaryResponse dashboardSummary() {
        String currentUserId = actionSupportService.currentUserId();
        java.time.LocalDate today = OffsetDateTime.now(TIME_ZONE).toLocalDate();
        List<ProcessTaskListItemResponse> todoRecords = page(new PageRequest(1, Integer.MAX_VALUE, null, List.of(), List.of(), List.of())).records();
        long todoTodayCount = todoRecords.stream()
                .filter(task -> task.createdAt() != null && task.createdAt().toLocalDate().equals(today))
                .count();
        long highRiskTodoCount = todoRecords.stream()
                .filter(task -> task.prediction() != null && "HIGH".equalsIgnoreCase(task.prediction().overdueRiskLevel()))
                .count();
        long overdueTodayCount = todoRecords.stream()
                .filter(task -> task.prediction() != null && task.prediction().predictedRiskThresholdTime() != null)
                .filter(task -> task.prediction().predictedRiskThresholdTime().toLocalDate().equals(today))
                .count();
        long doneApprovalCount = runtimeApprovalSheetQueryService.buildDoneApprovalSheets(currentUserId, List.of()).size();
        var analytics = runtimeProcessPredictionAnalyticsService.summarize(todoRecords);
        return new WorkbenchDashboardSummaryResponse(
                todoTodayCount,
                doneApprovalCount,
                highRiskTodoCount,
                overdueTodayCount,
                analytics.riskDistribution(),
                analytics.overdueTrend(),
                analytics.bottleneckNodes(),
                analytics.topRiskProcesses(),
                runtimeProcessPredictionGovernanceService.governanceSnapshot(),
                runtimeProcessPredictionGovernanceService.metricsLastDays(7)
        );
    }

    public ProcessTaskDetailResponse detail(String taskId) {
        return detail(taskId, null);
    }

    public ProcessTaskDetailResponse detail(String taskId, String userId) {
        String effectiveUserId = userId == null || userId.isBlank() ? actionSupportService.currentUserId() : userId;
        Task activeTask = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
        if (activeTask != null) {
            String processInstanceId = activeTask.getProcessInstanceId();
            return runtimeTaskDetailQueryService.buildDetailResponse(
                    processInstanceId,
                    activeTask,
                    null,
                    null,
                    true,
                    subprocessLinks(processInstanceId, effectiveUserId),
                    appendLinks(processInstanceId, effectiveUserId)
            );
        }
        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        if (historicTask == null) {
            throw actionSupportService.taskNotFound(taskId);
        }
        String processInstanceId = historicTask.getProcessInstanceId();
        return runtimeTaskDetailQueryService.buildDetailResponse(
                processInstanceId,
                null,
                historicTask,
                null,
                false,
                subprocessLinks(processInstanceId, effectiveUserId),
                appendLinks(processInstanceId, effectiveUserId)
        );
    }

    public ProcessTaskDetailResponse detailByBusiness(String businessType, String businessId) {
        Optional<BusinessLinkSnapshot> link = runtimeBusinessLinkService.findByBusiness(businessType, businessId);
        String processInstanceId = link.map(BusinessLinkSnapshot::processInstanceId)
                .orElseGet(() -> processActionSupportService.resolveProcessInstanceIdByBusinessKey(businessId));
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        Task activeTask = activeTasks.stream()
                .filter(this::isBlockingTask)
                .filter(task -> "ADD_SIGN".equals(taskActionSupportService.resolveTaskKind(task)))
                .findFirst()
                .orElse(activeTasks.stream()
                        .filter(this::isBlockingTask)
                        .filter(task -> !"APPEND".equals(taskActionSupportService.resolveTaskKind(task)))
                        .findFirst()
                        .orElse(activeTasks.stream().filter(this::isBlockingTask).findFirst().orElse(null)));
        HistoricTaskInstance latestHistoricTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        return runtimeTaskDetailQueryService.buildDetailResponse(
                processInstanceId,
                activeTask,
                latestHistoricTask,
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(null),
                activeTask != null,
                subprocessLinks(processInstanceId),
                appendLinks(processInstanceId)
        );
    }

    public List<CountersignTaskGroupResponse> taskGroups(String instanceId) {
        processActionSupportService.requireHistoricProcessInstance(instanceId);
        return flowableCountersignService.queryTaskGroups(instanceId);
    }

    public List<InclusiveGatewayHitResponse> inclusiveGatewayHits(String instanceId) {
        return runtimeTaskDetailQueryService.inclusiveGatewayHits(instanceId);
    }

    public List<ProcessInstanceLinkResponse> links(String instanceId) {
        runtimeProcessMetadataService.requireHistoricProcessInstance(instanceId);
        return subprocessLinks(instanceId);
    }

    @Transactional
    public ProcessInstanceLinkResponse confirmParentResume(String instanceId, String linkId) {
        String rootInstanceId = runtimeProcessMetadataService.resolveRuntimeTreeRootInstanceId(instanceId);
        runtimeProcessLinkQueryService.synchronizeProcessLinks(rootInstanceId, actionSupportService.currentUserId());
        var link = processLinkService.getById(linkId);
        if (link == null) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "主子流程关联不存在",
                    Map.of("linkId", linkId)
            );
        }
        if (!Objects.equals(link.rootInstanceId(), rootInstanceId) && !Objects.equals(link.parentInstanceId(), instanceId)) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_FORBIDDEN",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前实例无权确认该子流程关联",
                    Map.of("instanceId", instanceId, "linkId", linkId)
            );
        }
        if (!"WAIT_PARENT_CONFIRM".equals(link.status())) {
            throw actionSupportService.actionNotAllowed(
                    "当前子流程不处于等待父流程确认状态",
                    Map.of("linkId", linkId, "status", link.status())
            );
        }
        Instant confirmedAt = link.finishedAt() != null ? link.finishedAt() : Instant.now();
        processLinkService.updateStatus(link.childInstanceId(), "FINISHED", confirmedAt);
        processActionSupportService.appendInstanceEvent(
                link.parentInstanceId(),
                null,
                link.parentNodeId(),
                "SUBPROCESS_PARENT_CONFIRMED",
                "父流程已确认子流程完成",
                "INSTANCE",
                null,
                link.childInstanceId(),
                actionSupportService.currentUserId(),
                processActionSupportService.eventDetails(
                        "linkId", link.id(),
                        "childInstanceId", link.childInstanceId(),
                        "parentNodeId", link.parentNodeId(),
                        "joinMode", link.joinMode(),
                        "parentResumeStrategy", link.parentResumeStrategy()
                ),
                null,
                link.parentNodeId(),
                null,
                null,
                null,
                null,
                null
        );
        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                link.parentInstanceId(),
                null,
                null,
                null,
                null,
                null,
                link.parentNodeId(),
                "CONFIRM_SUBPROCESS_RESUME",
                "父流程确认子流程完成",
                "INSTANCE",
                actionSupportService.currentUserId(),
                null,
                null,
                null,
                null,
                Map.of(
                        "linkId", link.id(),
                        "childInstanceId", link.childInstanceId(),
                        "rootInstanceId", link.rootInstanceId()
                ),
                Instant.now()
        ));
        runtimeProcessLinkQueryService.synchronizeProcessLinks(rootInstanceId, actionSupportService.currentUserId());
        return runtimeProcessLinkQueryService.requireLinkResponse(linkId);
    }

    public List<RuntimeAppendLinkResponse> appendLinks(String instanceId) {
        runtimeProcessMetadataService.requireHistoricProcessInstance(instanceId);
        return appendLinks(instanceId, actionSupportService.currentUserId());
    }

    private List<ProcessInstanceLinkResponse> subprocessLinks(String instanceId) {
        return subprocessLinks(instanceId, actionSupportService.currentUserId());
    }

    private List<RuntimeAppendLinkResponse> appendLinks(String instanceId, String userId) {
        return runtimeProcessLinkQueryService.appendLinks(instanceId, userId);
    }

    private List<ProcessInstanceLinkResponse> subprocessLinks(String instanceId, String userId) {
        return runtimeProcessLinkQueryService.subprocessLinks(instanceId, userId);
    }

    private boolean isBlockingTask(Task task) {
        return taskActionSupportService.isVisibleTask(task) && !"CC".equals(taskActionSupportService.resolveTaskKind(task));
    }
}
