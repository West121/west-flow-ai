package com.westflow.processruntime.query;

import com.westflow.common.api.RequestContext;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.ApprovalSheetListView;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeApprovalSheetQueryService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeApprovalSheetQueryService.class);

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskAssembler runtimeTaskAssembler;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final RuntimeApprovalSheetProjectionService runtimeApprovalSheetProjectionService;

    public PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request, String currentUserId) {
        long startedAt = System.nanoTime();
        if (request.view() == ApprovalSheetListView.TODO && isDefaultApprovalSheetRequest(request)) {
            PageResponse<ApprovalSheetListItemResponse> response = pageTodoApprovalSheetsFast(currentUserId, request.page(), request.pageSize());
            log.info(
                    "approval-perf requestId={} path={} stage=approval-sheets.page mode=todo-fast totalMs={} totalRecords={} page={} pageSize={} keywordBlank={}",
                    RequestContext.getOrCreateRequestId(),
                    RequestContext.currentPath(),
                    elapsedMs(startedAt),
                    response.total(),
                    request.page(),
                    request.pageSize(),
                    request.keyword() == null || request.keyword().isBlank()
            );
            return response;
        }
        long buildStartedAt = System.nanoTime();
        List<ApprovalSheetListItemResponse> records = switch (request.view()) {
            case TODO -> buildTodoApprovalSheets(currentUserId);
            case INITIATED -> buildInitiatedApprovalSheets(currentUserId, request.businessTypes());
            case DONE -> buildDoneApprovalSheets(currentUserId, request.businessTypes());
            case CC -> buildCopiedApprovalSheets(currentUserId, request.businessTypes());
        };
        long buildElapsedMs = elapsedMs(buildStartedAt);
        long filterSortStartedAt = System.nanoTime();
        List<ApprovalSheetListItemResponse> filtered = records.stream()
                .filter(item -> runtimeApprovalSheetProjectionService.matchesKeyword(item, request.keyword()))
                .sorted(Comparator.comparing(ApprovalSheetListItemResponse::updatedAt).reversed())
                .toList();
        long filterSortElapsedMs = elapsedMs(filterSortStartedAt);
        PageResponse<ApprovalSheetListItemResponse> response = page(filtered, request.page(), request.pageSize());
        log.info(
                "approval-perf requestId={} path={} stage=approval-sheets.page mode={} buildMs={} filterSortMs={} totalMs={} sourceRecords={} filteredRecords={} page={} pageSize={} keywordBlank={} businessTypes={}",
                RequestContext.getOrCreateRequestId(),
                RequestContext.currentPath(),
                request.view(),
                buildElapsedMs,
                filterSortElapsedMs,
                elapsedMs(startedAt),
                records.size(),
                filtered.size(),
                request.page(),
                request.pageSize(),
                request.keyword() == null || request.keyword().isBlank(),
                request.businessTypes() == null ? 0 : request.businessTypes().size()
        );
        return response;
    }

    public List<ApprovalSheetListItemResponse> buildInitiatedApprovalSheets(String currentUserId, List<String> businessTypes) {
        return runtimeBusinessLinkService.listByStartUser(currentUserId).stream()
                .filter(link -> businessTypes.isEmpty() || businessTypes.contains(link.businessType()))
                .map(runtimeApprovalSheetProjectionService::fromLink)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ApprovalSheetListItemResponse> buildDoneApprovalSheets(String currentUserId, List<String> businessTypes) {
        Set<String> instanceIds = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskAssignee(currentUserId)
                .finished()
                .list()
                .stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ApprovalSheetListItemResponse> items = new ArrayList<>();
        for (String instanceId : instanceIds) {
            runtimeBusinessLinkService.findByInstanceId(instanceId)
                    .filter(link -> businessTypes.isEmpty() || businessTypes.contains(link.businessType()))
                    .map(runtimeApprovalSheetProjectionService::fromLink)
                    .ifPresent(items::add);
        }
        return items;
    }

    public List<ApprovalSheetListItemResponse> buildCopiedApprovalSheets(String currentUserId, List<String> businessTypes) {
        Map<String, ApprovalSheetListItemResponse> items = new LinkedHashMap<>();
        flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskCandidateOrAssigned(currentUserId)
                .active()
                .list()
                .stream()
                .filter(task -> "CC".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create())))
                .filter(task -> matchesBusinessTypes(runtimeVariables(task.getProcessInstanceId()), businessTypes))
                .forEach(task -> items.putIfAbsent(task.getProcessInstanceId(), runtimeApprovalSheetProjectionService.fromCopiedTask(task)));
        flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskAssignee(currentUserId)
                .finished()
                .list()
                .stream()
                .filter(task -> "CC".equals(runtimeApprovalSheetProjectionService.resolveHistoricTaskKind(task)))
                .filter(task -> matchesBusinessTypes(historicVariables(task.getProcessInstanceId()), businessTypes))
                .forEach(task -> items.putIfAbsent(task.getProcessInstanceId(), runtimeApprovalSheetProjectionService.fromCopiedHistoricTask(task)));
        return List.copyOf(items.values());
    }

    public List<ApprovalSheetListItemResponse> buildTodoApprovalSheets(String currentUserId) {
        RuntimeTaskQueryContext queryContext = RuntimeTaskQueryContext.create();
        RuntimeTaskProjectionContext projectionContext = RuntimeTaskProjectionContext.create();
        RuntimeApprovalSheetProjectionService.ApprovalSheetProjectionContext approvalContext =
                runtimeApprovalSheetProjectionService.createProjectionContext();
        Map<String, ApprovalSheetListItemResponse> projections = new LinkedHashMap<>();
        for (Task task : runtimeTaskVisibilityService.visibleActiveTasks(
                currentUserId,
                queryContext,
                runtimeTaskAssembler::resolvePublishedDefinitionByInstance
        )) {
            ProcessTaskListItemResponse taskItem = runtimeTaskAssembler.toTaskListItem(task, projectionContext, queryContext);
            if (taskItem != null) {
                projections.putIfAbsent(taskItem.instanceId(), runtimeApprovalSheetProjectionService.fromTask(taskItem, approvalContext));
            }
        }
        return List.copyOf(projections.values());
    }

    private PageResponse<ApprovalSheetListItemResponse> pageTodoApprovalSheetsFast(String currentUserId, int page, int pageSize) {
        long visibleTasksStartedAt = System.nanoTime();
        RuntimeTaskQueryContext queryContext = RuntimeTaskQueryContext.create();
        List<Task> visibleTasks = runtimeTaskVisibilityService.visibleActiveTasks(
                currentUserId,
                queryContext,
                runtimeTaskAssembler::resolvePublishedDefinitionByInstance
        );
        long visibleTasksElapsedMs = elapsedMs(visibleTasksStartedAt);
        long projectionStartedAt = System.nanoTime();
        LinkedHashMap<String, Task> taskByInstanceId = new LinkedHashMap<>();
        for (Task task : visibleTasks) {
            taskByInstanceId.putIfAbsent(task.getProcessInstanceId(), task);
        }
        List<Task> pagedTasks = pageSlice(new ArrayList<>(taskByInstanceId.values()), page, pageSize);
        long projectionElapsedMs = elapsedMs(projectionStartedAt);
        RuntimeTaskProjectionContext projectionContext = RuntimeTaskProjectionContext.create();
        RuntimeApprovalSheetProjectionService.ApprovalSheetProjectionContext approvalContext =
                runtimeApprovalSheetProjectionService.createProjectionContext();
        long enrichStartedAt = System.nanoTime();
        List<ApprovalSheetListItemResponse> records = pagedTasks.stream()
                .map(task -> runtimeTaskAssembler.toTaskListItem(task, projectionContext, queryContext))
                .filter(Objects::nonNull)
                .map(task -> runtimeApprovalSheetProjectionService.fromTask(task, approvalContext))
                .toList();
        long enrichElapsedMs = elapsedMs(enrichStartedAt);
        log.info(
                "approval-perf requestId={} path={} stage=approval-sheets.todo-fast visibleMs={} projectionMs={} enrichMs={} totalVisibleTasks={} uniqueInstances={} pageRecords={} page={} pageSize={}",
                RequestContext.getOrCreateRequestId(),
                RequestContext.currentPath(),
                visibleTasksElapsedMs,
                projectionElapsedMs,
                enrichElapsedMs,
                visibleTasks.size(),
                taskByInstanceId.size(),
                records.size(),
                page,
                pageSize
        );
        return page(records, page, pageSize, taskByInstanceId.size());
    }

    private boolean isDefaultApprovalSheetRequest(ApprovalSheetPageRequest request) {
        return (request.keyword() == null || request.keyword().isBlank())
                && request.filters().isEmpty()
                && request.sorts().isEmpty()
                && request.groups().isEmpty()
                && (request.businessTypes() == null || request.businessTypes().isEmpty());
    }

    private boolean matchesBusinessTypes(Map<String, Object> variables, List<String> businessTypes) {
        if (businessTypes == null || businessTypes.isEmpty()) {
            return true;
        }
        Object value = variables.get("westflowBusinessType");
        String businessType = value == null ? null : String.valueOf(value).trim();
        return businessType != null && businessTypes.contains(businessType);
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : variables;
    }

    private Map<String, Object> historicVariables(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private <T> PageResponse<T> page(List<T> records, int page, int pageSize) {
        return page(records, page, pageSize, records.size());
    }

    private <T> PageResponse<T> page(List<T> records, int page, int pageSize, long total) {
        long pages = total == 0 ? 0 : (total + pageSize - 1L) / pageSize;
        List<T> currentPage = total == records.size() ? pageSlice(records, page, pageSize) : records;
        return new PageResponse<>(page, pageSize, total, pages, currentPage, List.of());
    }

    private <T> List<T> pageSlice(List<T> records, int page, int pageSize) {
        long total = records.size();
        long offset = Math.max(0L, (long) (page - 1) * pageSize);
        return total == 0 ? List.of() : records.stream().skip(offset).limit(pageSize).toList();
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
