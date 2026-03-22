package com.westflow.processdef.service;

import com.westflow.processdef.model.ProcessDslPayload;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.stereotype.Service;

@Service
// 把设计器 DSL 转成可由 Flowable 真实部署的 BPMN XML。
public class ProcessDslToBpmnService {

    private static final String WESTFLOW_NS = "https://westflow.dev/schema/bpmn";
    private static final String WESTFLOW_PREFIX = "westflow";
    private static final String DEFAULT_TRIGGER_DELEGATE = "${flowableTriggerDelegate}";

    public String convert(ProcessDslPayload payload, String processDefinitionId, int version) {
        BpmnModel model = new BpmnModel();
        model.setTargetNamespace(WESTFLOW_NS);

        Process process = new Process();
        process.setId(payload.processKey());
        process.setName(payload.processName());
        process.setDocumentation(buildProcessDocumentation(processDefinitionId, version, payload));
        process.setExecutable(true);
        model.addProcess(process);

        payload.nodes().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Node::id))
                .map(this::toFlowElement)
                .forEach(process::addFlowElement);

        payload.edges().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Edge::id))
                .map(this::toSequenceFlow)
                .forEach(process::addFlowElement);

        byte[] xmlBytes = new BpmnXMLConverter().convertToXML(model, StandardCharsets.UTF_8.name());
        return new String(xmlBytes, StandardCharsets.UTF_8);
    }

    // 把 DSL 节点转换为真实 BPMN 流程元素。
    private FlowElement toFlowElement(ProcessDslPayload.Node node) {
        Map<String, Object> config = mapValue(node.config());
        FlowElement element = switch (node.type()) {
            case "start" -> buildStartEvent(node, config);
            case "approver" -> buildApproverTask(node, config);
            case "cc" -> buildCcTask(node, config);
            case "condition" -> buildExclusiveGateway(node, config);
            case "parallel_split" -> buildParallelGateway(node, "split");
            case "parallel_join" -> buildParallelGateway(node, "join");
            case "timer" -> buildTimerEvent(node, config);
            case "trigger" -> buildTriggerTask(node, config);
            case "end" -> buildEndEvent(node);
            default -> buildServicePlaceholder(node);
        };
        attachNodeMetadata(element, node, config);
        return element;
    }

    // 起始节点映射为开始事件。
    private StartEvent buildStartEvent(ProcessDslPayload.Node node, Map<String, Object> config) {
        StartEvent event = new StartEvent();
        event.setId(node.id());
        event.setName(node.name());
        return event;
    }

    // 审批节点映射为真实用户任务。
    private UserTask buildApproverTask(ProcessDslPayload.Node node, Map<String, Object> config) {
        UserTask task = new UserTask();
        task.setId(node.id());
        task.setName(node.name());
        Map<String, Object> assignment = mapValue(config.get("assignment"));
        List<String> userIds = stringListValue(assignment.get("userIds"));
        if (userIds.size() == 1) {
            task.setAssignee(userIds.get(0));
        } else if (!userIds.isEmpty()) {
            task.setCandidateUsers(userIds);
        }
        return task;
    }

    // 抄送节点先落成候选人用户任务，后续运行态按 CC 语义处理。
    private UserTask buildCcTask(ProcessDslPayload.Node node, Map<String, Object> config) {
        UserTask task = new UserTask();
        task.setId(node.id());
        task.setName(node.name());
        Map<String, Object> targets = mapValue(config.get("targets"));
        List<String> userIds = stringListValue(targets.get("userIds"));
        if (!userIds.isEmpty()) {
            task.setCandidateUsers(userIds);
        }
        addExtensionAttribute(task, "taskKind", "CC");
        return task;
    }

    // 条件节点映射为排他网关。
    private ExclusiveGateway buildExclusiveGateway(ProcessDslPayload.Node node, Map<String, Object> config) {
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId(node.id());
        gateway.setName(node.name());
        String defaultEdgeId = stringValue(config.get("defaultEdgeId"));
        if (defaultEdgeId != null) {
            gateway.setDefaultFlow(defaultEdgeId);
        }
        return gateway;
    }

    // 并行网关分别承载分支和汇聚语义。
    private ParallelGateway buildParallelGateway(ProcessDslPayload.Node node, String gatewayType) {
        ParallelGateway gateway = new ParallelGateway();
        gateway.setId(node.id());
        gateway.setName(node.name());
        addExtensionAttribute(gateway, "gatewayType", gatewayType);
        return gateway;
    }

    // 定时节点映射为中间捕获事件。
    private IntermediateCatchEvent buildTimerEvent(ProcessDslPayload.Node node, Map<String, Object> config) {
        IntermediateCatchEvent event = new IntermediateCatchEvent();
        event.setId(node.id());
        event.setName(node.name());

        TimerEventDefinition definition = new TimerEventDefinition();
        String runAt = stringValue(config.get("runAt"));
        String delayMinutes = stringValue(config.get("delayMinutes"));
        if (runAt != null) {
            definition.setTimeDate(runAt);
        } else if (delayMinutes != null) {
            definition.setTimeDuration("PT" + delayMinutes + "M");
        } else {
            definition.setTimeDuration("PT1M");
        }
        event.getEventDefinitions().add(definition);
        return event;
    }

    // 触发节点先映射为 ServiceTask，并统一走平台 delegate。
    private ServiceTask buildTriggerTask(ProcessDslPayload.Node node, Map<String, Object> config) {
        ServiceTask task = new ServiceTask();
        task.setId(node.id());
        task.setName(node.name());
        task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        task.setImplementation(DEFAULT_TRIGGER_DELEGATE);
        return task;
    }

    // 结束节点映射为结束事件。
    private EndEvent buildEndEvent(ProcessDslPayload.Node node) {
        EndEvent event = new EndEvent();
        event.setId(node.id());
        event.setName(node.name());
        return event;
    }

    // 未覆盖的节点先落成占位服务任务，保证 XML 可部署。
    private ServiceTask buildServicePlaceholder(ProcessDslPayload.Node node) {
        ServiceTask task = new ServiceTask();
        task.setId(node.id());
        task.setName(node.name());
        task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        task.setImplementation(DEFAULT_TRIGGER_DELEGATE);
        addExtensionAttribute(task, "dslNodeType", node.type());
        return task;
    }

    // 连线映射为真实 sequence flow，并保留条件表达式。
    private SequenceFlow toSequenceFlow(ProcessDslPayload.Edge edge) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(edge.id());
        flow.setSourceRef(edge.source());
        flow.setTargetRef(edge.target());
        flow.setName(edge.label());
        Map<String, Object> condition = mapValue(edge.condition());
        String expression = stringValue(condition.get("expression"));
        if (expression != null) {
            flow.setConditionExpression(expression);
        }
        addExtensionAttribute(flow, "priority", edge.priority() == null ? null : String.valueOf(edge.priority()));
        String conditionType = stringValue(condition.get("type"));
        if (conditionType != null) {
            addExtensionAttribute(flow, "conditionType", conditionType);
        }
        return flow;
    }

    // 把 DSL 节点元数据和配置统一写成扩展属性，供运行态读取。
    private void attachNodeMetadata(BaseElement element, ProcessDslPayload.Node node, Map<String, Object> config) {
        addExtensionAttribute(element, "dslNodeId", node.id());
        addExtensionAttribute(element, "dslNodeType", node.type());
        addExtensionAttribute(element, "description", normalizeNullable(node.description()));
        flattenConfig(config).forEach((key, value) -> addExtensionAttribute(element, key, value));
    }

    // 展平嵌套配置，保留旧实现里测试依赖的关键字段名。
    private Map<String, String> flattenConfig(Map<String, Object> config) {
        Map<String, String> attrs = new LinkedHashMap<>();

        attrs.put("initiatorEditable", stringValue(config.get("initiatorEditable")));
        attrs.put("operations", joinValues(config.get("operations")));
        attrs.put("commentRequired", booleanValue(config.get("commentRequired")));
        attrs.put("nodeFormKey", stringValue(config.get("nodeFormKey")));
        attrs.put("nodeFormVersion", stringValue(config.get("nodeFormVersion")));
        attrs.put("fieldBindings", stringValue(config.get("fieldBindings")));
        attrs.put("readRequired", booleanValue(config.get("readRequired")));
        attrs.put("triggerMode", stringValue(config.get("triggerMode")));
        attrs.put("scheduleType", stringValue(config.get("scheduleType")));
        attrs.put("runAt", stringValue(config.get("runAt")));
        attrs.put("delayMinutes", stringValue(config.get("delayMinutes")));
        attrs.put("triggerKey", stringValue(config.get("triggerKey")));
        attrs.put("retryTimes", stringValue(config.get("retryTimes")));
        attrs.put("retryIntervalMinutes", stringValue(config.get("retryIntervalMinutes")));
        attrs.put("payloadTemplate", stringValue(config.get("payloadTemplate")));

        Map<String, Object> assignment = mapValue(config.get("assignment"));
        attrs.put("assignmentMode", stringValue(assignment.get("mode")));
        attrs.put("userIds", joinValues(assignment.get("userIds")));
        attrs.put("roleCodes", joinValues(assignment.get("roleCodes")));
        attrs.put("departmentRef", stringValue(assignment.get("departmentRef")));
        attrs.put("formFieldKey", stringValue(assignment.get("formFieldKey")));

        Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
        attrs.put("approvalPolicyType", stringValue(approvalPolicy.get("type")));
        attrs.put("voteThreshold", stringValue(approvalPolicy.get("voteThreshold")));

        Map<String, Object> timeoutPolicy = mapValue(config.get("timeoutPolicy"));
        attrs.put("timeoutEnabled", booleanValue(timeoutPolicy.get("enabled")));
        attrs.put("timeoutDurationMinutes", stringValue(timeoutPolicy.get("durationMinutes")));
        attrs.put("timeoutAction", stringValue(timeoutPolicy.get("action")));

        Map<String, Object> reminderPolicy = mapValue(config.get("reminderPolicy"));
        attrs.put("reminderEnabled", booleanValue(reminderPolicy.get("enabled")));
        attrs.put("reminderFirstReminderAfterMinutes", stringValue(reminderPolicy.get("firstReminderAfterMinutes")));
        attrs.put("reminderRepeatIntervalMinutes", stringValue(reminderPolicy.get("repeatIntervalMinutes")));
        attrs.put("reminderMaxTimes", stringValue(reminderPolicy.get("maxTimes")));
        attrs.put("reminderChannels", joinValues(reminderPolicy.get("channels")));

        Map<String, Object> targets = mapValue(config.get("targets"));
        attrs.put("targetMode", stringValue(targets.get("mode")));
        attrs.put("targetUserIds", joinValues(targets.get("userIds")));
        attrs.put("targetRoleCodes", joinValues(targets.get("roleCodes")));
        attrs.put("targetDepartmentRef", stringValue(targets.get("departmentRef")));

        return attrs;
    }

    // 统一写入 westflow 扩展属性，兼顾可部署和可回读。
    private void addExtensionAttribute(BaseElement element, String name, String value) {
        if (name == null || value == null || value.isBlank()) {
            return;
        }
        ExtensionAttribute attribute = new ExtensionAttribute();
        attribute.setName(name);
        attribute.setValue(value);
        attribute.setNamespace(WESTFLOW_NS);
        attribute.setNamespacePrefix(WESTFLOW_PREFIX);
        element.addAttribute(attribute);
    }

    private String buildProcessDocumentation(String processDefinitionId, int version, ProcessDslPayload payload) {
        return "processDefinitionId=" + processDefinitionId
                + ";version=" + version
                + ";category=" + normalizeNullable(payload.category());
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

    private String joinValues(Object value) {
        List<String> values = stringListValue(value);
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String booleanValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
