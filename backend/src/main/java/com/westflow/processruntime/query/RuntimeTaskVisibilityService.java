package com.westflow.processruntime.query;

import com.westflow.common.api.RequestContext;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.mapper.IdentityAccessMapper;
import com.westflow.identity.response.CurrentUserResponse;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTaskVisibilityService.class);

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final IdentityAuthService identityAuthService;
    private final IdentityAccessMapper identityAccessMapper;

    public List<Task> visibleActiveTasks(
            String currentUserId,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        long startedAt = System.nanoTime();
        List<Task> candidateOrAssignedTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskCandidateOrAssigned(currentUserId)
                .active()
                .list();
        long candidateQueryMs = elapsedMs(startedAt);
        List<String> currentCandidateGroupIds = currentCandidateGroupIds();
        long groupQueryStartedAt = System.nanoTime();
        List<Task> departmentCandidateTasks = currentCandidateGroupIds.isEmpty()
                ? List.of()
                : flowableEngineFacade.taskService()
                        .createTaskQuery()
                        .taskCandidateGroupIn(currentCandidateGroupIds)
                        .active()
                        .list();
        long groupQueryMs = elapsedMs(groupQueryStartedAt);
        long mergeFilterStartedAt = System.nanoTime();
        Collection<Task> mergedTasks = java.util.stream.Stream.concat(candidateOrAssignedTasks.stream(), departmentCandidateTasks.stream())
                .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left, LinkedHashMap::new))
                .values();
        prefetchRunningAppendLinks(mergedTasks, context);
        long taskKindStartedAt = System.nanoTime();
        List<Task> filteredTasks = new ArrayList<>(mergedTasks.size());
        int skippedCcCount = 0;
        for (Task task : mergedTasks) {
            List<RuntimeAppendLinkRecord> runningAppendLinks = context.appendLinksByInstanceId().getOrDefault(task.getProcessInstanceId(), List.of());
            String taskKind = runningAppendLinks.isEmpty()
                    ? resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), context)
                    : resolveTaskKind(task, context);
            if ("CC".equals(taskKind)) {
                skippedCcCount++;
                continue;
            }
            if (runningAppendLinks.isEmpty()) {
                filteredTasks.add(task);
                continue;
            }
            if (isVisibleTask(task, taskKind, context, definitionResolver)) {
                filteredTasks.add(task);
            }
        }
        long taskKindAndVisibilityMs = elapsedMs(taskKindStartedAt);
        List<Task> visibleTasks = filteredTasks.stream()
                .sorted(Comparator.comparing(Task::getCreateTime).reversed())
                .toList();
        long mergeFilterMs = elapsedMs(mergeFilterStartedAt);
        log.info(
                "approval-perf requestId={} stage=visible-active-tasks candidateQueryMs={} groupQueryMs={} mergeFilterMs={} taskKindVisibilityMs={} totalMs={} candidateCount={} groupCount={} mergedCount={} skippedCcCount={} visibleCount={} userId={}",
                RequestContext.getOrCreateRequestId(),
                candidateQueryMs,
                groupQueryMs,
                mergeFilterMs,
                taskKindAndVisibilityMs,
                elapsedMs(startedAt),
                candidateOrAssignedTasks.size(),
                departmentCandidateTasks.size(),
                mergedTasks.size(),
                skippedCcCount,
                visibleTasks.size(),
                currentUserId
        );
        return visibleTasks;
    }

    public Map<String, Object> taskLocalVariables(String taskId, RuntimeTaskQueryContext context) {
        return context.taskLocalVariablesByTaskId().computeIfAbsent(taskId, this::queryTaskLocalVariables);
    }

    public String resolveTaskKind(Task task, RuntimeTaskQueryContext context) {
        String runtimeTaskKind = stringValue(taskLocalVariables(task.getId(), context).get("westflowTaskKind"));
        if (runtimeTaskKind != null) {
            return runtimeTaskKind;
        }
        return resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), context);
    }

    public String resolveTaskKind(String engineProcessDefinitionId, String nodeId, RuntimeTaskQueryContext context) {
        if (nodeId == null || nodeId.isBlank()) {
            return "NORMAL";
        }
        String key = engineProcessDefinitionId + "::" + nodeId;
        return context.taskKindByNodeKey().computeIfAbsent(key, ignored -> {
            BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(engineProcessDefinitionId);
            if (model == null) {
                return "NORMAL";
            }
            BaseElement element = model.getFlowElement(nodeId);
            if (element == null) {
                return "NORMAL";
            }
            List<org.flowable.bpmn.model.ExtensionAttribute> attrs = element.getAttributes().get("taskKind");
            if (attrs == null || attrs.isEmpty() || attrs.get(0).getValue() == null || attrs.get(0).getValue().isBlank()) {
                return "NORMAL";
            }
            return attrs.get(0).getValue();
        });
    }

    public boolean isVisibleTask(
            Task task,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        if (task == null) {
            return false;
        }
        return isVisibleTask(task, resolveTaskKind(task, context), context, definitionResolver);
    }

    public boolean isVisibleTask(
            Task task,
            String taskKind,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        if (task == null) {
            return false;
        }
        if (!"NORMAL".equals(taskKind)) {
            return true;
        }
        return !isBlockedByPendingAppendStructures(task, context, definitionResolver);
    }

    private void prefetchRunningAppendLinks(Collection<Task> tasks, RuntimeTaskQueryContext context) {
        List<String> instanceIds = tasks.stream()
                .map(Task::getProcessInstanceId)
                .filter(instanceId -> instanceId != null && !instanceId.isBlank())
                .filter(instanceId -> !context.appendLinksByInstanceId().containsKey(instanceId))
                .distinct()
                .toList();
        if (instanceIds.isEmpty()) {
            return;
        }
        Map<String, List<RuntimeAppendLinkRecord>> runningLinksByInstanceId = runtimeAppendLinkService.listByParentInstanceIds(instanceIds).stream()
                .filter(link -> "RUNNING".equals(link.status()))
                .collect(Collectors.groupingBy(RuntimeAppendLinkRecord::parentInstanceId, LinkedHashMap::new, Collectors.toList()));
        for (String instanceId : instanceIds) {
            context.appendLinksByInstanceId().put(instanceId, runningLinksByInstanceId.getOrDefault(instanceId, List.of()));
        }
    }

    private boolean isBlockedByPendingAppendStructures(
            Task task,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        if (task == null) {
            return false;
        }
        List<RuntimeAppendLinkRecord> runningAppendLinks = context.appendLinksByInstanceId().computeIfAbsent(
                task.getProcessInstanceId(),
                instanceId -> runtimeAppendLinkService.listByParentInstanceId(instanceId).stream()
                        .filter(link -> "RUNNING".equals(link.status()))
                        .toList()
        );
        if (runningAppendLinks.isEmpty()) {
            return false;
        }
        return isBlockedBySerialBeforeNext(task, runningAppendLinks, context, definitionResolver)
                || isBlockedBySerialAfterCurrent(task, runningAppendLinks, context);
    }

    private boolean isBlockedBySerialBeforeNext(
            Task task,
            List<RuntimeAppendLinkRecord> runningAppendLinks,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        return blockingDynamicBuilderNodeIds(task.getProcessInstanceId(), task.getTaskDefinitionKey(), context, definitionResolver).stream()
                .anyMatch(sourceNodeId -> runningAppendLinks.stream()
                        .anyMatch(link -> sourceNodeId.equals(link.sourceNodeId())));
    }

    private boolean isBlockedBySerialAfterCurrent(
            Task task,
            List<RuntimeAppendLinkRecord> runningAppendLinks,
            RuntimeTaskQueryContext context
    ) {
        return runningAppendLinks.stream()
                .filter(link -> "SERIAL_AFTER_CURRENT".equals(normalizeAppendPolicy(link.policy())))
                .anyMatch(link -> shouldBlockBySerialAfterCurrent(task, link, context));
    }

    private boolean shouldBlockBySerialAfterCurrent(
            Task task,
            RuntimeAppendLinkRecord link,
            RuntimeTaskQueryContext context
    ) {
        if (!Objects.equals(task.getProcessInstanceId(), link.parentInstanceId())) {
            return false;
        }
        if ("DYNAMIC_BUILD".equals(link.triggerMode())) {
            return true;
        }
        String sourceTaskId = link.sourceTaskId();
        if (sourceTaskId == null || sourceTaskId.isBlank()) {
            return true;
        }
        return context.sourceTaskCompletedByTaskId().computeIfAbsent(sourceTaskId, taskId -> flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskId(taskId)
                .singleResult() == null);
    }

    private List<String> blockingDynamicBuilderNodeIds(
            String processInstanceId,
            String nodeId,
            RuntimeTaskQueryContext context,
            Function<String, Optional<PublishedProcessDefinition>> definitionResolver
    ) {
        if (nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        String key = processInstanceId + "::" + nodeId;
        return context.blockingDynamicBuilderNodeIdsByTargetKey().computeIfAbsent(
                key,
                ignored -> definitionResolver.apply(processInstanceId)
                        .map(definition -> definition.dsl().edges().stream()
                                .filter(edge -> nodeId.equals(edge.target()))
                                .map(edge -> definition.dsl().nodes().stream()
                                        .filter(node -> edge.source().equals(node.id()))
                                        .findFirst()
                                        .orElse(null))
                                .filter(Objects::nonNull)
                                .filter(node -> "dynamic-builder".equals(node.type()))
                                .filter(node -> {
                                    String appendPolicy = normalizeAppendPolicy(stringValue(nodeConfig(definition.dsl(), node.id()).get("appendPolicy")));
                                    return "SERIAL_BEFORE_NEXT".equals(appendPolicy);
                                })
                                .map(ProcessDslPayload.Node::id)
                                .distinct()
                                .toList())
                        .orElse(List.of())
        );
    }

    private List<String> currentCandidateGroupIds() {
        CurrentUserResponse currentUser = identityAuthService.currentUser();
        LinkedHashSet<String> groupIds = new LinkedHashSet<>();
        currentUser.postAssignments().stream()
                .filter(assignment -> assignment.postId().equals(currentUser.activePostId()))
                .findFirst()
                .ifPresentOrElse(
                        assignment -> assignment.roleIds().stream()
                                .filter(roleId -> roleId != null && !roleId.isBlank())
                                .forEach(groupIds::add),
                        () -> identityAccessMapper.selectRoleIdsByUserId(currentUser.userId()).stream()
                                .filter(roleId -> roleId != null && !roleId.isBlank())
                                .forEach(groupIds::add)
                );
        if (currentUser.activeDepartmentId() != null && !currentUser.activeDepartmentId().isBlank()) {
            groupIds.add(currentUser.activeDepartmentId());
        }
        return List.copyOf(groupIds);
    }

    private Map<String, Object> queryTaskLocalVariables(String taskId) {
        Map<String, Object> variables;
        try {
            variables = flowableEngineFacade.taskService().getVariablesLocal(taskId);
        } catch (FlowableObjectNotFoundException ignored) {
            return Map.of();
        }
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    private Map<String, Object> nodeConfig(ProcessDslPayload payload, String nodeId) {
        if (payload == null || nodeId == null) {
            return Map.of();
        }
        return payload.nodes().stream()
                .filter(node -> nodeId.equals(node.id()))
                .findFirst()
                .map(node -> node.config() == null ? Map.<String, Object>of() : node.config())
                .orElse(Map.of());
    }

    private String normalizeAppendPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return "SERIAL_AFTER_CURRENT";
        }
        return policy.trim().toUpperCase();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
