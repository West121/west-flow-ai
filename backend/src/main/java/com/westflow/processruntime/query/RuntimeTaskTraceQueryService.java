package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskTraceQueryService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;

    public List<ProcessTaskTraceItemResponse> buildTaskTrace(List<HistoricTaskInstance> historicTasks, List<Task> activeTasks) {
        List<ProcessTaskTraceItemResponse> items = new ArrayList<>();
        Set<String> knownTaskIds = new LinkedHashSet<>();
        Map<String, Task> activeTaskById = activeTasks.stream()
                .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left, LinkedHashMap::new));
        for (HistoricTaskInstance task : historicTasks) {
            if (task.getEndTime() == null
                    && activeTaskById.containsKey(task.getId())
                    && !isHistoricTaskRevoked(task)) {
                continue;
            }
            knownTaskIds.add(task.getId());
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            OffsetDateTime endedAt = toOffsetDateTime(task.getEndTime());
            String taskKind = resolveHistoricTaskKind(task);
            String taskSemanticMode = runtimeTaskSupportService.resolveHistoricTaskSemanticMode(task);
            Map<String, Object> localVariables = historicTaskLocalVariables(task.getId());
            String historicStatus = runtimeTaskSupportService.resolveHistoricTaskStatus(task, null);
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    taskKind,
                    taskSemanticMode,
                    historicStatus,
                    task.getAssignee(),
                    List.of(),
                    List.of(),
                    stringValue(localVariables.get("westflowAction")),
                    null,
                    null,
                    createdAt,
                    runtimeTaskSupportService.readTimeValue(localVariables),
                    createdAt,
                    endedAt,
                    durationSeconds(createdAt, endedAt),
                    stringValue(localVariables.get("westflowSourceTaskId")),
                    null,
                    stringValue(localVariables.get("westflowTargetUserId")),
                    "CC".equals(taskKind),
                    "ADD_SIGN".equals(taskKind),
                    isHistoricTaskRevoked(task),
                    false,
                    false,
                    "TAKEN_BACK".equals(historicStatus),
                    null,
                    null,
                    null,
                    runtimeTaskSupportService.resolveActingMode(null, task),
                    resolveActingForUserId(null, task),
                    resolveDelegatedByUserId(null, task),
                    stringValue(localVariables.get("westflowHandoverFromUserId"))
            ));
        }
        for (Task task : activeTasks) {
            if (knownTaskIds.contains(task.getId())) {
                continue;
            }
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            String taskKind = resolveTaskKind(task);
            String taskSemanticMode = runtimeTaskSupportService.resolveTaskSemanticMode(task);
            Map<String, Object> localVariables = taskLocalVariables(task.getId());
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    taskKind,
                    taskSemanticMode,
                    resolveTraceTaskStatus(task, localVariables),
                    task.getAssignee(),
                    runtimeTaskSupportService.candidateUsers(task.getId()),
                    runtimeTaskSupportService.candidateGroups(task.getId()),
                    null,
                    null,
                    null,
                    createdAt,
                    runtimeTaskSupportService.readTimeValue(localVariables),
                    createdAt,
                    null,
                    null,
                    stringValue(localVariables.get("westflowSourceTaskId")),
                    null,
                    stringValue(localVariables.get("westflowTargetUserId")),
                    "CC".equals(taskKind),
                    "ADD_SIGN".equals(taskKind),
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    runtimeTaskSupportService.resolveActingMode(task, null),
                    resolveActingForUserId(task, null),
                    resolveDelegatedByUserId(task, null),
                    stringValue(localVariables.get("westflowHandoverFromUserId"))
            ));
        }
        return items;
    }

    public List<ProcessInstanceEventResponse> buildSyntheticEvents(
            HistoricProcessInstance processInstance,
            List<ProcessTaskTraceItemResponse> taskTrace
    ) {
        List<ProcessInstanceEventResponse> events = new ArrayList<>();
        events.add(new ProcessInstanceEventResponse(
                processInstance.getId() + "::start",
                processInstance.getId(),
                null,
                null,
                "INSTANCE_STARTED",
                "流程实例已发起",
                "INSTANCE",
                null,
                null,
                null,
                processInstance.getStartUserId(),
                toOffsetDateTime(processInstance.getStartTime()),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        if (processInstance.getEndTime() != null || processInstance.getDeleteReason() != null) {
            String eventType = "INSTANCE_COMPLETED";
            String eventName = "流程结束";
            if ("WESTFLOW_REVOKED".equals(processInstance.getDeleteReason())) {
                eventType = "INSTANCE_REVOKED";
                eventName = "流程已撤销";
            } else if (processInstance.getDeleteReason() != null) {
                eventType = "INSTANCE_TERMINATED";
                eventName = "流程已终止";
            }
            events.add(new ProcessInstanceEventResponse(
                    processInstance.getId() + "::end",
                    processInstance.getId(),
                    null,
                    null,
                    eventType,
                    eventName,
                    "INSTANCE",
                    null,
                    null,
                    null,
                    processInstance.getStartUserId(),
                    toOffsetDateTime(processInstance.getEndTime()),
                    Map.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        for (ProcessTaskTraceItemResponse item : taskTrace) {
            OffsetDateTime occurredAt = item.handleEndTime() != null ? item.handleEndTime() : item.receiveTime();
            events.add(new ProcessInstanceEventResponse(
                    processInstance.getId() + "::" + item.taskId(),
                    processInstance.getId(),
                    item.taskId(),
                    item.nodeId(),
                    item.handleEndTime() == null ? "TASK_CREATED" : "TASK_COMPLETED",
                    item.handleEndTime() == null ? "任务已创建" : "任务已完成",
                    "TASK",
                    null,
                    null,
                    null,
                    item.assigneeUserId(),
                    occurredAt,
                    Map.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return events;
    }

    public List<ProcessInstanceEventResponse> mergeInstanceEvents(
            List<ProcessInstanceEventResponse> instanceEvents,
            List<ProcessInstanceEventResponse> derivedEvents
    ) {
        if (derivedEvents.isEmpty()) {
            return instanceEvents;
        }
        Map<String, ProcessInstanceEventResponse> deduped = new LinkedHashMap<>();
        for (ProcessInstanceEventResponse event : instanceEvents) {
            deduped.put(event.eventId(), event);
        }
        for (ProcessInstanceEventResponse event : derivedEvents) {
            deduped.put(event.eventId(), event);
        }
        return deduped.values().stream()
                .sorted(Comparator.comparing(ProcessInstanceEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProcessInstanceEventResponse::eventId))
                .toList();
    }

    public Map<String, Object> ensureReadTimeAndReturnLocalVariables(Task task) {
        Map<String, Object> localVariables = taskLocalVariables(task.getId());
        if (localVariables.get("westflowReadTime") != null || !"CC".equals(resolveTaskKind(task))) {
            return localVariables;
        }
        OffsetDateTime now = OffsetDateTime.now(TIME_ZONE);
        flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowReadTime", java.util.Date.from(now.toInstant()));
        return taskLocalVariables(task.getId());
    }

    public Map<String, Object> historicTaskLocalVariables(String taskId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .taskId(taskId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    public String resolveInstanceStatus(HistoricProcessInstance processInstance, List<Task> activeTasks) {
        if (processInstance.getDeleteReason() != null) {
            if ("WESTFLOW_REVOKED".equals(processInstance.getDeleteReason())) {
                return "REVOKED";
            }
            return isTerminatedProcess(processInstance) ? "TERMINATED" : "COMPLETED";
        }
        return activeTasks.stream().anyMatch(this::isBlockingTask) ? "RUNNING" : "COMPLETED";
    }

    private Map<String, Object> taskLocalVariables(String taskId) {
        return runtimeTaskVisibilityService.taskLocalVariables(taskId, RuntimeTaskQueryContext.create());
    }

    private String resolveTaskKind(Task task) {
        return runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create());
    }

    private String resolveHistoricTaskKind(HistoricTaskInstance task) {
        String historicTaskKind = stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (historicTaskKind != null) {
            return historicTaskKind;
        }
        return runtimeTaskVisibilityService.resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), RuntimeTaskQueryContext.create());
    }

    private String resolveTraceTaskStatus(Task task, Map<String, Object> localVariables) {
        String action = stringValue(localVariables.get("westflowAction"));
        if ("HANDOVER".equals(action)) {
            return "HANDOVERED";
        }
        return runtimeTaskSupportService.resolveTaskStatus(task);
    }

    private boolean isBlockingTask(Task task) {
        return isVisibleTask(task) && !"CC".equals(resolveTaskKind(task));
    }

    private boolean isVisibleTask(Task task) {
        return runtimeTaskVisibilityService.isVisibleTask(
                task,
                RuntimeTaskQueryContext.create(),
                runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
        );
    }

    private boolean isHistoricTaskRevoked(HistoricTaskInstance task) {
        return task.getDeleteReason() != null && !task.getDeleteReason().isBlank();
    }

    private String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowActingForUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        String ownerUserId = activeTask != null ? activeTask.getOwner() : historicTask == null ? null : historicTask.getOwner();
        String assigneeUserId = activeTask != null ? activeTask.getAssignee() : historicTask == null ? null : historicTask.getAssignee();
        if (ownerUserId != null && assigneeUserId != null && !ownerUserId.equals(assigneeUserId)) {
            return ownerUserId;
        }
        return null;
    }

    private String resolveDelegatedByUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowDelegatedByUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        return resolveActingForUserId(activeTask, historicTask);
    }

    private boolean isTerminatedProcess(HistoricProcessInstance historicProcessInstance) {
        String deleteReason = historicProcessInstance.getDeleteReason();
        return deleteReason != null && (
                deleteReason.startsWith("WESTFLOW_TERMINATE:")
                        || deleteReason.startsWith("WESTFLOW_SUBPROCESS_FINISH_POLICY:")
        );
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    private Long durationSeconds(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
