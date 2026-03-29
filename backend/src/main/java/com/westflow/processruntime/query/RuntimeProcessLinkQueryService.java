package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessLinkQueryService {

    private final ProcessLinkService processLinkService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final RuntimeProcessLinkSyncService runtimeProcessLinkSyncService;
    private final RuntimeProcessLinkProjectionService runtimeProcessLinkProjectionService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final FlowableEngineFacade flowableEngineFacade;

    public List<ProcessInstanceLinkResponse> subprocessLinks(String instanceId, String operatorUserId) {
        String rootInstanceId = processLinkService.resolveRootInstanceId(instanceId);
        runtimeProcessLinkSyncService.synchronizeProcessLinks(rootInstanceId, operatorUserId);
        List<ProcessLinkRecord> rootLinks = processLinkService.listByRootInstanceId(rootInstanceId);
        if (rootLinks.isEmpty()) {
            return List.of();
        }
        List<ProcessLinkRecord> visibleLinks = runtimeProcessLinkSyncService.collectVisibleSubprocessLinks(rootInstanceId, rootLinks);
        Map<String, Integer> descendantCounts = new LinkedHashMap<>();
        Map<String, Integer> runningDescendantCounts = new LinkedHashMap<>();
        for (ProcessLinkRecord link : visibleLinks) {
            List<ProcessLinkRecord> descendants = runtimeProcessLinkSyncService.descendantProcessLinks(rootInstanceId, link.childInstanceId());
            descendantCounts.put(link.id(), descendants.size());
            runningDescendantCounts.put(
                    link.id(),
                    (int) descendants.stream().filter(descendant -> "RUNNING".equals(descendant.status())).count()
            );
        }
        return visibleLinks.stream()
                .map(link -> runtimeProcessLinkProjectionService.toProcessInstanceLinkResponse(
                        link,
                        descendantCounts.getOrDefault(link.id(), 0),
                        runningDescendantCounts.getOrDefault(link.id(), 0)
                ))
                .toList();
    }

    public ProcessInstanceLinkResponse requireLinkResponse(String linkId) {
        return runtimeProcessLinkProjectionService.requireLinkResponse(linkId);
    }

    public List<RuntimeAppendLinkResponse> appendLinks(String instanceId, String operatorUserId) {
        String rootInstanceId = runtimeProcessMetadataService.resolveRuntimeTreeRootInstanceId(instanceId);
        runtimeProcessLinkSyncService.synchronizeAppendLinks(rootInstanceId, operatorUserId);
        return runtimeAppendLinkService.listByRootInstanceId(rootInstanceId).stream()
                .map(runtimeProcessLinkProjectionService::toRuntimeAppendLinkResponse)
                .toList();
    }

    public List<ProcessTaskSnapshot> activeAppendTasks(String processInstanceId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(task -> "APPEND".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create())))
                .map(runtimeProcessLinkProjectionService::toAppendTaskView)
                .toList();
    }

    public List<ProcessLinkRecord> descendantProcessLinks(String rootInstanceId, String parentInstanceId) {
        return runtimeProcessLinkSyncService.descendantProcessLinks(rootInstanceId, parentInstanceId);
    }

    public void synchronizeProcessLinks(String rootInstanceId, String operatorUserId) {
        runtimeProcessLinkSyncService.synchronizeProcessLinks(rootInstanceId, operatorUserId);
    }

    public void synchronizeAppendLinks(String rootInstanceId, String operatorUserId) {
        runtimeProcessLinkSyncService.synchronizeAppendLinks(rootInstanceId, operatorUserId);
    }
}
