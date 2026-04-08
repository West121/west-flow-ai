package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuntimeProcessPredictionRefreshService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeProcessPredictionService runtimeProcessPredictionService;
    private final RuntimeProcessPredictionActionExecutorService runtimeProcessPredictionActionExecutorService;
    private final RuntimeProcessPredictionSnapshotService runtimeProcessPredictionSnapshotService;

    public void refreshForProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            PublishedProcessDefinition definition = runtimeProcessMetadataService.resolvePublishedDefinitionByInstance(processInstanceId)
                    .orElse(null);
            if (definition == null) {
                return;
            }
            Map<String, Object> variables = runtimeProcessMetadataService.runtimeOrHistoricVariables(processInstanceId);
            String businessType = stringValue(variables.get("westflowBusinessType"));
            String initiatorUserId = stringValue(variables.get("westflowInitiatorUserId"));
            String organizationProfile = resolveOrganizationProfile(
                    stringValue(variables.get("westflowInitiatorDepartmentName")),
                    stringValue(variables.get("westflowInitiatorPostName"))
            );
            List<Task> activeTasks = flowableEngineFacade.taskService()
                    .createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .orderByTaskCreateTime()
                    .asc()
                    .list();
            for (Task task : activeTasks) {
                if (!runtimeTaskVisibilityService.isVisibleTask(
                        task,
                        RuntimeTaskQueryContext.create(),
                        runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
                )) {
                    continue;
                }
                String taskKind = runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create());
                if ("CC".equals(taskKind)) {
                    continue;
                }
                ProcessPredictionResponse prediction = runtimeProcessPredictionService.predictForActiveTaskListItem(
                        definition.processKey(),
                        task.getTaskDefinitionKey(),
                        task.getName(),
                        taskKind,
                        runtimeTaskSupportService.resolveTaskSemanticMode(task),
                        resolveCurrentAction(variables),
                        runtimeTaskSupportService.resolveActingMode(task, null),
                        stringValue(flowableEngineFacade.taskService().getVariableLocal(task.getId(), "westflowActingForUserId")),
                        stringValue(flowableEngineFacade.taskService().getVariableLocal(task.getId(), "westflowDelegatedByUserId")),
                        stringValue(flowableEngineFacade.taskService().getVariableLocal(task.getId(), "westflowHandoverFromUserId")),
                        task.getAssignee(),
                        businessType,
                        organizationProfile,
                        OffsetDateTime.ofInstant(task.getCreateTime().toInstant(), TIME_ZONE),
                        definition.dsl().nodes(),
                        definition.dsl().edges()
                );
                prediction = runtimeProcessPredictionActionExecutorService.execute(
                        processInstanceId,
                        task.getId(),
                        definition.processName(),
                        task.getTaskDefinitionKey(),
                        task.getName(),
                        task.getAssignee(),
                        initiatorUserId,
                        prediction
                );
                runtimeProcessPredictionSnapshotService.recordSnapshot(processInstanceId, task.getId(), prediction);
            }
        } catch (RuntimeException exception) {
            log.warn("process prediction refresh skipped for instance {}: {}", processInstanceId, exception.getMessage());
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private String resolveCurrentAction(Map<String, Object> variables) {
        String currentAction = stringValue(variables.get("westflowAction"));
        if (currentAction != null) {
            return currentAction;
        }
        return stringValue(variables.get("westflowLastAction"));
    }

    private String resolveOrganizationProfile(String departmentName, String postName) {
        if (departmentName == null || departmentName.isBlank()) {
            return postName == null || postName.isBlank() ? null : postName;
        }
        if (postName == null || postName.isBlank()) {
            return departmentName;
        }
        return departmentName + " / " + postName;
    }
}
