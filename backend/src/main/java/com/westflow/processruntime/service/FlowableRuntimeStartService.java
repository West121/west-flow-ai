package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.response.CurrentUserResponse;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.model.ProcessLinkRecord;
import java.util.ArrayList;
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
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
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
    private final BusinessProcessBindingService businessProcessBindingService;
    private final IdentityAuthService identityAuthService;

    /**
     * 启动指定流程定义的最新发布版本。
     */
    public StartProcessResponse start(StartProcessRequest request) {
        PublishedProcessDefinition definition = processDefinitionService.getLatestByProcessKey(request.processKey());
        String effectiveBusinessType = resolveEffectiveBusinessType(definition.processKey(), request.businessType());
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
                effectiveBusinessType,
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
                buildSubprocessNodesByKey(definition, parentInstance.getProcessInstanceId());
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
                    resolveChildStartDecisionReason(config, resolveProcessBusinessType(parentInstance.getProcessInstanceId())),
                    stringValueOrDefault(config.get("parentResumeStrategy"), "AUTO_RETURN"),
                    now,
                    null
            ));
            applySubprocessInputMappings(parentInstance.getProcessInstanceId(), childInstance.getProcessInstanceId(), config);
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
            PublishedProcessDefinition definition,
            String parentInstanceId
    ) {
        Map<String, Deque<com.westflow.processdef.model.ProcessDslPayload.Node>> nodesByKey = new LinkedHashMap<>();
        String businessType = resolveProcessBusinessType(parentInstanceId);
        for (com.westflow.processdef.model.ProcessDslPayload.Node node : definition.dsl().nodes()) {
            if (!"subprocess".equals(node.type())) {
                continue;
            }
            String calledProcessKey = resolveRuntimeSubprocessProcessKey(mapValue(node.config()), businessType, node.id());
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
        CurrentUserResponse currentUser = identityAuthService.currentUser();
        String effectiveBusinessType = resolveEffectiveBusinessType(definition.processKey(), request.businessType());
        if (request.formData() != null && !request.formData().isEmpty()) {
            Map<String, Object> normalizedFormData = normalizeStartFormData(effectiveBusinessType, request.formData());
            variables.putAll(normalizedFormData);
            variables.put("westflowFormData", new LinkedHashMap<>(normalizedFormData));
        }
        variables.put("westflowProcessDefinitionId", definition.processDefinitionId());
        variables.put("westflowProcessKey", definition.processKey());
        variables.put("westflowProcessName", definition.processName());
        variables.put("westflowBusinessType", effectiveBusinessType);
        variables.put("westflowBusinessKey", request.businessKey());
        variables.put("westflowInitiatorUserId", currentUser.userId());
        variables.put("westflowInitiatorPostId", currentUser.activePostId());
        variables.put("westflowInitiatorPostName", currentUser.activePostName());
        variables.put("westflowInitiatorDepartmentId", currentUser.activeDepartmentId());
        variables.put("westflowInitiatorDepartmentName", currentUser.activeDepartmentName());
        variables.put("westflowInitiatorCompanyId", currentUser.companyId());
        variables.put("westflowInitiatorCompanyName", currentUser.companyName());
        definition.dsl().nodes().stream()
                .filter(node -> "subprocess".equals(node.type()))
                .forEach(node -> appendSubprocessStartVariables(variables, effectiveBusinessType, node));
        definition.dsl().nodes().stream()
                .filter(node -> "approver".equals(node.type()))
                .forEach(node -> appendCountersignStartVariables(variables, node));
        return variables;
    }

    private String resolveEffectiveBusinessType(String processKey, String businessType) {
        if (businessType != null && !businessType.isBlank()) {
            return businessType;
        }
        return switch (processKey) {
            case "oa_leave" -> "OA_LEAVE";
            case "oa_expense" -> "OA_EXPENSE";
            case "oa_common" -> "OA_COMMON";
            case "plm_ecr" -> "PLM_ECR";
            case "plm_eco" -> "PLM_ECO";
            case "plm_material" -> "PLM_MATERIAL";
            default -> businessType;
        };
    }

    private Map<String, Object> normalizeStartFormData(String businessType, Map<String, Object> formData) {
        Map<String, Object> normalized = new LinkedHashMap<>(formData);
        if (!"OA_LEAVE".equalsIgnoreCase(businessType)) {
            return normalized;
        }
        Object days = normalized.get("days");
        Object leaveDays = normalized.get("leaveDays");
        if (days == null && leaveDays != null) {
            normalized.put("days", leaveDays);
        }
        if (leaveDays == null && days != null) {
            normalized.put("leaveDays", days);
        }
        if (!normalized.containsKey("leaveType") || normalized.get("leaveType") == null) {
            normalized.put("leaveType", "ANNUAL");
        }
        if (!normalized.containsKey("urgent") || normalized.get("urgent") == null) {
            normalized.put("urgent", Boolean.FALSE);
        }
        if (!normalized.containsKey("managerUserId") || normalized.get("managerUserId") == null
                || String.valueOf(normalized.get("managerUserId")).isBlank()) {
            normalized.put("managerUserId", "usr_002");
        }
        return normalized;
    }

    private void appendSubprocessStartVariables(
            Map<String, Object> variables,
            String businessType,
            com.westflow.processdef.model.ProcessDslPayload.Node node
    ) {
        String calledProcessKey = resolveRuntimeSubprocessProcessKey(mapValue(node.config()), businessType, node.id());
        if (calledProcessKey != null) {
            variables.put(subprocessCalledElementVariable(node.id()), calledProcessKey);
        }
    }

    private void applySubprocessInputMappings(
            String parentInstanceId,
            String childInstanceId,
            Map<String, Object> config
    ) {
        for (Map<String, Object> mapping : listMapValue(config.get("inputMappings"))) {
            String source = stringValue(mapping.get("source"));
            String target = stringValue(mapping.get("target"));
            if (source == null || target == null) {
                continue;
            }
            Object value = flowableEngineFacade.runtimeService().getVariable(parentInstanceId, source);
            if (value != null) {
                flowableEngineFacade.runtimeService().setVariable(childInstanceId, target, value);
            }
        }
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
        List<String> candidateGroupIds = flowableEngineFacade.taskService()
                .getIdentityLinksForTask(task.getId())
                .stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .map(IdentityLink::getGroupId)
                .filter(groupId -> groupId != null && !groupId.isBlank())
                .distinct()
                .toList();
        String taskKind = resolveTaskKind(task);
        String assignmentMode = !candidateGroupIds.isEmpty()
                ? "DEPARTMENT"
                : task.getAssignee() != null || !candidateUserIds.isEmpty() ? "USER" : null;
        String status = task.getAssignee() == null && (!candidateUserIds.isEmpty() || !candidateGroupIds.isEmpty())
                ? "PENDING_CLAIM"
                : "PENDING";
        return new ProcessTaskSnapshot(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                taskKind,
                status,
                assignmentMode,
                candidateUserIds,
                candidateGroupIds,
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

    private List<Map<String, Object>> listMapValue(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> mapped = mapValue(item);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
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

    private String resolveRuntimeSubprocessProcessKey(Map<String, Object> config, String businessType, String nodeId) {
        String childStartStrategy = stringValueOrDefault(config.get("childStartStrategy"), "LATEST_PUBLISHED");
        if (!"SCENE_BINDING".equals(childStartStrategy)) {
            return stringValue(config.get("calledProcessKey"));
        }
        String sceneCode = stringValue(config.get("sceneCode"));
        if (businessType == null || sceneCode == null) {
            throw new IllegalStateException("SCENE_BINDING 子流程缺少业务类型或场景码: nodeId=" + nodeId);
        }
        return businessProcessBindingService.resolveProcessKey(businessType, sceneCode);
    }

    private String resolveChildStartDecisionReason(Map<String, Object> config, String businessType) {
        String childStartStrategy = stringValueOrDefault(config.get("childStartStrategy"), "LATEST_PUBLISHED");
        return switch (childStartStrategy) {
            case "SCENE_BINDING" -> {
                String sceneCode = stringValue(config.get("sceneCode"));
                String resolvedBusinessType = businessType == null || businessType.isBlank() ? "UNKNOWN" : businessType;
                yield sceneCode == null || sceneCode.isBlank()
                        ? "SCENE_BINDING"
                        : "SCENE_BINDING:" + resolvedBusinessType + "/" + sceneCode;
            }
            case "FIXED_VERSION" -> {
                String calledProcessKey = stringValue(config.get("calledProcessKey"));
                Object calledVersion = config.get("calledVersion");
                yield calledProcessKey == null || calledProcessKey.isBlank()
                        ? "FIXED_VERSION"
                        : calledProcessKey + "@" + String.valueOf(calledVersion == null ? "LATEST" : calledVersion);
            }
            default -> "LATEST_PUBLISHED";
        };
    }

    private String subprocessCalledElementVariable(String nodeId) {
        return "wfSubprocessCalledElement_" + nodeId;
    }

    private String resolveProcessBusinessType(String processInstanceId) {
        try {
            return stringValue(flowableEngineFacade.runtimeService().getVariable(processInstanceId, "westflowBusinessType"));
        } catch (FlowableObjectNotFoundException ignored) {
            return null;
        }
    }
}
