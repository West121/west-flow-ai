package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.ProcessTaskSnapshot;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
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

    /**
     * 启动指定流程定义的最新发布版本。
     */
    public StartProcessResponse start(StartProcessRequest request) {
        PublishedProcessDefinition definition = processDefinitionService.getLatestByProcessKey(request.processKey());
        Map<String, Object> variables = buildStartVariables(definition, request);
        ProcessInstance instance = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey(definition.processKey(), request.businessKey(), variables);
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
        List<String> userIds = stringListValue(assignment.get("userIds"));
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
}
