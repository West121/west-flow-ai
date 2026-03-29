package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import com.westflow.processruntime.trace.RuntimeInstanceEventRecorder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessLinkSyncService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessLinkService processLinkService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeInstanceEventRecorder runtimeInstanceEventRecorder;
    private final com.westflow.processruntime.action.FlowableTaskActionService flowableTaskActionService;

    public void synchronizeProcessLinks(String rootInstanceId, String operatorUserId) {
        List<ProcessLinkRecord> rootLinks = processLinkService.listByRootInstanceId(rootInstanceId);
        rootLinks.stream()
                .filter(record -> "RUNNING".equals(record.status()))
                .forEach(record -> {
                    ProcessInstance runtimeChild = flowableEngineFacade.runtimeService()
                            .createProcessInstanceQuery()
                            .processInstanceId(record.childInstanceId())
                            .singleResult();
                    if (runtimeChild != null) {
                        return;
                    }
                    HistoricProcessInstance historicChild = flowableEngineFacade.historyService()
                            .createHistoricProcessInstanceQuery()
                            .processInstanceId(record.childInstanceId())
                            .singleResult();
                    if (historicChild == null || historicChild.getEndTime() == null) {
                        return;
                    }
                    RuntimeProcessMetadataService.SubprocessStructureMetadata structureMetadata =
                            runtimeProcessMetadataService.resolveSubprocessStructureMetadata(record);
                    boolean terminateParentAfterFinish = "TERMINATE_PARENT".equals(record.childFinishPolicy())
                            && !isTerminatedProcess(historicChild);
                    String resolvedStatus = isTerminatedProcess(historicChild)
                            ? "TERMINATED"
                            : terminateParentAfterFinish
                            ? "FINISHED"
                            : runtimeProcessMetadataService.requiresParentConfirmation(structureMetadata)
                            ? "WAIT_PARENT_CONFIRM"
                            : "FINISHED";
                    processLinkService.updateStatus(record.childInstanceId(), resolvedStatus, historicChild.getEndTime().toInstant());
                    runtimeInstanceEventRecorder.appendInstanceEvent(
                            record.parentInstanceId(),
                            null,
                            record.parentNodeId(),
                            switch (resolvedStatus) {
                                case "TERMINATED" -> "SUBPROCESS_TERMINATED";
                                case "WAIT_PARENT_CONFIRM" -> "SUBPROCESS_WAIT_PARENT_CONFIRM";
                                default -> "SUBPROCESS_FINISHED";
                            },
                            switch (resolvedStatus) {
                                case "TERMINATED" -> "子流程已终止";
                                case "WAIT_PARENT_CONFIRM" -> "子流程已完成，等待父流程确认";
                                default -> "子流程已完成";
                            },
                            "INSTANCE",
                            null,
                            record.childInstanceId(),
                            null,
                            runtimeInstanceEventRecorder.eventDetails(
                                    "childInstanceId", record.childInstanceId(),
                                    "parentNodeId", record.parentNodeId(),
                                    "calledProcessKey", record.calledProcessKey(),
                                    "resolvedStatus", resolvedStatus
                            ),
                            null,
                            record.parentNodeId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            operatorUserId
                    );
                    if (terminateParentAfterFinish) {
                        terminateParentProcessOnChildFinish(record, historicChild.getEndTime().toInstant(), rootLinks, operatorUserId);
                    }
                });
    }

    public void synchronizeAppendLinks(String rootInstanceId, String operatorUserId) {
        runtimeAppendLinkService.listByRootInstanceId(rootInstanceId).stream()
                .filter(record -> "RUNNING".equals(record.status()))
                .forEach(record -> {
                    if (record.targetTaskId() != null && !record.targetTaskId().isBlank()) {
                        synchronizeTaskAppendLink(record, operatorUserId);
                        return;
                    }
                    if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
                        synchronizeInstanceAppendLink(record, operatorUserId);
                    }
                });
    }

    public List<ProcessLinkRecord> collectVisibleSubprocessLinks(String parentInstanceId, List<ProcessLinkRecord> rootLinks) {
        Map<String, List<ProcessLinkRecord>> linksByParentInstanceId = rootLinks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProcessLinkRecord::parentInstanceId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<ProcessLinkRecord> visibleLinks = new ArrayList<>();
        collectVisibleSubprocessLinks(parentInstanceId, linksByParentInstanceId, visibleLinks);
        return visibleLinks;
    }

    public List<ProcessLinkRecord> descendantProcessLinks(String rootInstanceId, String parentInstanceId) {
        Map<String, List<ProcessLinkRecord>> linksByParentInstanceId = processLinkService
                .listByRootInstanceId(rootInstanceId)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProcessLinkRecord::parentInstanceId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<ProcessLinkRecord> descendants = new ArrayList<>();
        collectDescendantProcessLinks(parentInstanceId, linksByParentInstanceId, descendants);
        return descendants;
    }

    private void synchronizeTaskAppendLink(com.westflow.processruntime.model.RuntimeAppendLinkRecord record, String operatorUserId) {
        Task runtimeTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskId(record.targetTaskId())
                .singleResult();
        if (runtimeTask != null) {
            return;
        }
        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(record.targetTaskId())
                .singleResult();
        if (historicTask == null || historicTask.getEndTime() == null) {
            return;
        }
        String resolvedStatus = historicTask.getDeleteReason() != null && !historicTask.getDeleteReason().isBlank()
                ? "TERMINATED"
                : "COMPLETED";
        runtimeAppendLinkService.updateStatusByTargetTaskId(
                record.targetTaskId(),
                resolvedStatus,
                historicTask.getEndTime().toInstant()
        );
        runtimeInstanceEventRecorder.appendInstanceEvent(
                record.parentInstanceId(),
                record.targetTaskId(),
                record.sourceNodeId(),
                "TERMINATED".equals(resolvedStatus) ? "APPEND_TERMINATED" : "TASK_APPEND_COMPLETED",
                "TERMINATED".equals(resolvedStatus) ? "追加任务已终止" : "追加任务已完成",
                "TASK",
                record.sourceTaskId(),
                record.targetTaskId(),
                record.targetUserId(),
                runtimeInstanceEventRecorder.eventDetails(
                        "appendLinkId", record.id(),
                        "appendType", record.appendType(),
                        "runtimeLinkType", record.runtimeLinkType(),
                        "sourceTaskId", record.sourceTaskId(),
                        "sourceNodeId", record.sourceNodeId(),
                        "targetTaskId", record.targetTaskId(),
                        "targetUserId", record.targetUserId(),
                        "resolvedStatus", resolvedStatus
                ),
                null,
                record.sourceNodeId(),
                null,
                null,
                null,
                null,
                null,
                operatorUserId
        );
    }

    private void synchronizeInstanceAppendLink(com.westflow.processruntime.model.RuntimeAppendLinkRecord record, String operatorUserId) {
        ProcessInstance runtimeChild = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(record.targetInstanceId())
                .singleResult();
        if (runtimeChild != null) {
            return;
        }
        HistoricProcessInstance historicChild = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(record.targetInstanceId())
                .singleResult();
        if (historicChild == null || historicChild.getEndTime() == null) {
            return;
        }
        String resolvedStatus = isTerminatedProcess(historicChild) ? "TERMINATED" : "COMPLETED";
        runtimeAppendLinkService.updateStatusByTargetInstanceId(
                record.targetInstanceId(),
                resolvedStatus,
                historicChild.getEndTime().toInstant()
        );
        runtimeInstanceEventRecorder.appendInstanceEvent(
                record.parentInstanceId(),
                null,
                record.sourceNodeId(),
                "TERMINATED".equals(resolvedStatus) ? "APPEND_TERMINATED" : "SUBPROCESS_APPENDED_FINISHED",
                "TERMINATED".equals(resolvedStatus) ? "追加子流程已终止" : "追加子流程已完成",
                "INSTANCE",
                record.sourceTaskId(),
                record.targetInstanceId(),
                null,
                runtimeInstanceEventRecorder.eventDetails(
                        "appendLinkId", record.id(),
                        "appendType", record.appendType(),
                        "runtimeLinkType", record.runtimeLinkType(),
                        "sourceTaskId", record.sourceTaskId(),
                        "sourceNodeId", record.sourceNodeId(),
                        "childInstanceId", record.targetInstanceId(),
                        "calledProcessKey", record.calledProcessKey(),
                        "resolvedStatus", resolvedStatus
                ),
                null,
                record.sourceNodeId(),
                null,
                null,
                null,
                null,
                null,
                operatorUserId
        );
    }

    private void collectVisibleSubprocessLinks(
            String parentInstanceId,
            Map<String, List<ProcessLinkRecord>> linksByParentInstanceId,
            List<ProcessLinkRecord> visibleLinks
    ) {
        for (ProcessLinkRecord link : linksByParentInstanceId.getOrDefault(parentInstanceId, List.of())) {
            visibleLinks.add(link);
            if ("CHILD_AND_DESCENDANTS".equals(runtimeProcessMetadataService.resolveSubprocessStructureMetadata(link).callScope())) {
                collectVisibleSubprocessLinks(link.childInstanceId(), linksByParentInstanceId, visibleLinks);
            }
        }
    }

    private void collectDescendantProcessLinks(
            String parentInstanceId,
            Map<String, List<ProcessLinkRecord>> linksByParentInstanceId,
            List<ProcessLinkRecord> descendants
    ) {
        for (ProcessLinkRecord link : linksByParentInstanceId.getOrDefault(parentInstanceId, List.of())) {
            descendants.add(link);
            collectDescendantProcessLinks(link.childInstanceId(), linksByParentInstanceId, descendants);
        }
    }

    private void terminateParentProcessOnChildFinish(
            ProcessLinkRecord record,
            Instant finishedAt,
            List<ProcessLinkRecord> rootLinks,
            String operatorUserId
    ) {
        ProcessInstance runtimeParent = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(record.parentInstanceId())
                .singleResult();
        if (runtimeParent == null) {
            return;
        }
        flowableTaskActionService.revokeProcessInstance(
                record.parentInstanceId(),
                "WESTFLOW_SUBPROCESS_FINISH_POLICY:" + record.parentNodeId()
        );
        List<ProcessLinkRecord> subtreeLinks = collectVisibleSubprocessLinks(record.parentInstanceId(), rootLinks);
        for (ProcessLinkRecord subtreeLink : subtreeLinks) {
            if (record.childInstanceId().equals(subtreeLink.childInstanceId())) {
                continue;
            }
            if (!"TERMINATED".equals(subtreeLink.status())) {
                processLinkService.updateStatus(subtreeLink.childInstanceId(), "TERMINATED", finishedAt);
            }
        }
        LinkedHashSet<String> subtreeInstanceIds = new LinkedHashSet<>();
        subtreeInstanceIds.add(record.parentInstanceId());
        subtreeLinks.stream()
                .map(ProcessLinkRecord::childInstanceId)
                .filter(childInstanceId -> !record.childInstanceId().equals(childInstanceId))
                .forEach(subtreeInstanceIds::add);
        subtreeInstanceIds.forEach(instanceId -> runtimeAppendLinkService.markTerminatedByParentInstanceId(instanceId, finishedAt));
        runtimeInstanceEventRecorder.appendInstanceEvent(
                record.parentInstanceId(),
                null,
                record.parentNodeId(),
                "SUBPROCESS_FINISH_TERMINATE_PARENT",
                "子流程完成后终止父流程",
                "INSTANCE",
                null,
                record.childInstanceId(),
                null,
                runtimeInstanceEventRecorder.eventDetails(
                        "childInstanceId", record.childInstanceId(),
                        "parentNodeId", record.parentNodeId(),
                        "childFinishPolicy", record.childFinishPolicy()
                ),
                null,
                record.parentNodeId(),
                null,
                null,
                null,
                null,
                null,
                operatorUserId
        );
    }

    private boolean isTerminatedProcess(HistoricProcessInstance historicProcessInstance) {
        String deleteReason = historicProcessInstance.getDeleteReason();
        return deleteReason != null && (
                deleteReason.startsWith("WESTFLOW_TERMINATE:")
                        || deleteReason.startsWith("WESTFLOW_SUBPROCESS_FINISH_POLICY:")
        );
    }
}
