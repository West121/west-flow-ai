package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.ProcessTaskSnapshot;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.model.ProcessLinkRecord;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import com.westflow.workflowadmin.service.WorkflowOperationLogService;

/**
 * 基于真实 Flowable 引擎发起流程实例，并回填最小活动任务快照。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableRuntimeStartService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final WorkflowOperationLogService workflowOperationLogService;
    private final FlowableCountersignService flowableCountersignService;
    private final ProcessLinkService processLinkService;
    private final CountersignAssigneeResolver countersignAssigneeResolver;

    /**
     * 启动指定流程定义的最新发布版本。
     */
    public StartProcessResponse start(StartProcessRequest request) {
        PublishedProcessDefinition definition = processDefinitionService.getLatestByProcessKey(request.processKey());
        Map<String, Object> variables = buildStartVariables(definition, request);
        ProcessInstance instance = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey(definition.processKey(), request.businessKey(), variables);
        persistSubprocessLinks(definition, instance);
        List<ProcessTaskSnapshot> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instance.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .map(this::toTaskView)
                .toList();
        flowableCountersignService.initializeTaskGroups(instance.getProcessDefinitionId(), instance.getProcessInstanceId());
        String status = activeTasks.isEmpty() ? "COMPLETED" : "RUNNING";
        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                instance.getProcessInstanceId(),
                definition.processDefinitionId(),
                instance.getProcessDefinitionId(),
                request.businessType(),
                request.businessKey(),
                activeTasks.isEmpty() ? null : activeTasks.get(0).taskId(),
                activeTasks.isEmpty() ? null : activeTasks.get(0).nodeId(),
                "START_PROCESS",
                "发起流程",
                "INSTANCE",
                StpUtil.getLoginIdAsString(),
                null,
                null,
                null,
                null,
                Map.of(
                        "processKey", definition.processKey(),
                        "status", status
                ),
                java.time.Instant.now()
        ));
        return new StartProcessResponse(definition.processDefinitionId(), instance.getProcessInstanceId(), status, activeTasks);
    }

    /**
     * 主流程发起后扫描即时创建的 callActivity 子流程，并落平台关联表。
     */
    private void persistSubprocessLinks(PublishedProcessDefinition definition, ProcessInstance parentInstance) {
        String rootInstanceId = java.util.Optional.ofNullable(processLinkService.getByChildInstanceId(parentInstance.getProcessInstanceId()))
                .map(ProcessLinkRecord::rootInstanceId)
                .orElse(parentInstance.getProcessInstanceId());
        persistSubprocessLinks(definition, parentInstance, rootInstanceId);
    }

    private void persistSubprocessLinks(
            PublishedProcessDefinition definition,
            ProcessInstance parentInstance,
            String rootInstanceId
    ) {
        Map<String, Deque<com.westflow.processdef.model.ProcessDslPayload.Node>> subprocessNodesByKey =
                buildSubprocessNodesByKey(definition);
        if (subprocessNodesByKey.isEmpty()) {
            return;
        }
        List<ProcessInstance> childInstances = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .superProcessInstanceId(parentInstance.getProcessInstanceId())
                .list();
        for (ProcessInstance childInstance : childInstances) {
            ProcessDefinition childDefinition = flowableEngineFacade.repositoryService()
                    .createProcessDefinitionQuery()
                    .processDefinitionId(childInstance.getProcessDefinitionId())
                    .singleResult();
            if (childDefinition == null) {
                continue;
            }
            Deque<com.westflow.processdef.model.ProcessDslPayload.Node> matchedNodes =
                    subprocessNodesByKey.get(childDefinition.getKey());
            if (matchedNodes == null || matchedNodes.isEmpty()) {
                continue;
            }
            com.westflow.processdef.model.ProcessDslPayload.Node subprocessNode = matchedNodes.removeFirst();
            Map<String, Object> config = mapValue(subprocessNode.config());
            Instant now = Instant.now();
            processLinkService.createLink(new ProcessLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    parentInstance.getProcessInstanceId(),
                    childInstance.getProcessInstanceId(),
                    subprocessNode.id(),
                    childDefinition.getKey(),
                    childInstance.getProcessDefinitionId(),
                    "CALL_ACTIVITY",
                    "RUNNING",
                    stringValue(config.get("terminatePolicy")),
                    stringValue(config.get("childFinishPolicy")),
                    stringValueOrDefault(config.get("callScope"), "CHILD_ONLY"),
                    stringValueOrDefault(config.get("joinMode"), "AUTO_RETURN"),
                    stringValueOrDefault(config.get("childStartStrategy"), "LATEST_PUBLISHED"),
                    stringValueOrDefault(config.get("parentResumeStrategy"), "AUTO_RETURN"),
                    now,
                    null
            ));
            PublishedProcessDefinition childPublishedDefinition = processDefinitionService.getByFlowableDefinitionId(
                    childInstance.getProcessDefinitionId()
            );
            persistSubprocessLinks(childPublishedDefinition, childInstance, rootInstanceId);
        }
    }

    /**
     * 预先按子流程 key 建立节点队列，便于一对多 callActivity 场景顺序匹配。
     */
    private Map<String, Deque<com.westflow.processdef.model.ProcessDslPayload.Node>> buildSubprocessNodesByKey(
            PublishedProcessDefinition definition
    ) {
        Map<String, Deque<com.westflow.processdef.model.ProcessDslPayload.Node>> nodesByKey = new LinkedHashMap<>();
        for (com.westflow.processdef.model.ProcessDslPayload.Node node : definition.dsl().nodes()) {
            if (!"subprocess".equals(node.type())) {
                continue;
            }
            String calledProcessKey = stringValue(mapValue(node.config()).get("calledProcessKey"));
            if (calledProcessKey == null) {
                continue;
            }
            nodesByKey.computeIfAbsent(calledProcessKey, unused -> new ArrayDeque<>()).addLast(node);
        }
        return nodesByKey;
    }

    /**
     * 把平台上下文和表单数据统一写入流程变量，供后续运行态与详情查询复用。
     */
    private Map<String, Object> buildStartVariables(PublishedProcessDefinition definition, StartProcessRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request.formData() != null && !request.formData().isEmpty()) {
            variables.putAll(request.formData());
            variables.put("westflowFormData", new LinkedHashMap<>(request.formData()));
        }
        variables.put("westflowProcessDefinitionId", definition.processDefinitionId());
        variables.put("westflowProcessKey", definition.processKey());
        variables.put("westflowProcessName", definition.processName());
        variables.put("westflowBusinessType", request.businessType());
        variables.put("westflowBusinessKey", request.businessKey());
        variables.put("westflowInitiatorUserId", StpUtil.getLoginIdAsString());
        definition.dsl().nodes().stream()
                .filter(node -> "approver".equals(node.type()))
                .forEach(node -> appendCountersignStartVariables(variables, node));
        return variables;
    }

    /**
     * 会签节点在发起时预置处理人集合，供 Flowable 多实例用户任务直接消费。
     */
    private void appendCountersignStartVariables(Map<String, Object> variables, com.westflow.processdef.model.ProcessDslPayload.Node node) {
        Map<String, Object> config = mapValue(node.config());
        if (!config.containsKey("approvalMode")) {
            return;
        }
        String approvalMode = stringValue(config.get("approvalMode"));
        if (!List.of("SEQUENTIAL", "PARALLEL", "OR_SIGN", "VOTE").contains(approvalMode)) {
            return;
        }
        Map<String, Object> assignment = mapValue(config.get("assignment"));
        List<String> userIds = countersignAssigneeResolver.resolve(assignment, variables);
        if (userIds.size() < 2) {
            return;
        }
        variables.put("wfCountersignAssignees_" + node.id(), userIds);
        if ("OR_SIGN".equals(approvalMode) || "VOTE".equals(approvalMode)) {
            variables.put("wfCountersignDecision_" + node.id(), "PENDING");
        }
    }

    /**
     * 将 Flowable 活动任务转换为可复用的运行态任务快照。
     */
    private ProcessTaskSnapshot toTaskView(Task task) {
        List<String> candidateUserIds = flowableEngineFacade.taskService()
                .getIdentityLinksForTask(task.getId())
                .stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .map(IdentityLink::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
        String taskKind = resolveTaskKind(task);
        String assignmentMode = task.getAssignee() != null || !candidateUserIds.isEmpty() ? "USER" : null;
        String status = task.getAssignee() == null && !candidateUserIds.isEmpty() ? "PENDING_CLAIM" : "PENDING";
        return new ProcessTaskSnapshot(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                taskKind,
                status,
                assignmentMode,
                candidateUserIds,
                task.getAssignee(),
                null,
                null,
                null,
                null
        );
    }

    /**
     * 从 BPMN 扩展属性中读取平台任务类型，保证 CC 等节点语义不丢。
     */
    private String resolveTaskKind(Task task) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(task.getProcessDefinitionId());
        if (model == null) {
            return "NORMAL";
        }
        BaseElement element = model.getFlowElement(task.getTaskDefinitionKey());
        if (element == null) {
            return "NORMAL";
        }
        List<org.flowable.bpmn.model.ExtensionAttribute> attrs = element.getAttributes().get("taskKind");
        if (attrs == null || attrs.isEmpty()) {
            return "NORMAL";
        }
        String value = attrs.get(0).getValue();
        return value == null || value.isBlank() ? "NORMAL" : value;
    }

    /**
     * Flowable identity link 类型常量。
     */
    private static final class IdentityLinkType {
        private static final String CANDIDATE = "candidate";

        private IdentityLinkType() {
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String stringValueOrDefault(Object value, String defaultValue) {
        String stringValue = stringValue(value);
        return stringValue == null ? defaultValue : stringValue;
    }
}
