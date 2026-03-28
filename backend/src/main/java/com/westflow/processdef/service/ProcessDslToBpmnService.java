package com.westflow.processdef.service;

import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDefinitionRecord;
import com.westflow.processdef.model.ProcessDslPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.IOParameter;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
// 把设计器 DSL 转成可由 Flowable 真实部署的 BPMN XML。
public class ProcessDslToBpmnService {

    private static final String WESTFLOW_NS = "https://westflow.dev/schema/bpmn";
    private static final String WESTFLOW_PREFIX = "westflow";
    private static final String DEFAULT_TRIGGER_DELEGATE = "${flowableTriggerDelegate}";
    private static final String DEFAULT_DYNAMIC_BUILDER_DELEGATE = "${flowableDynamicBuilderDelegate}";
    private final ProcessDefinitionMapper processDefinitionMapper;

    ProcessDslToBpmnService() {
        this.processDefinitionMapper = null;
    }

    @Autowired
    public ProcessDslToBpmnService(ProcessDefinitionMapper processDefinitionMapper) {
        this.processDefinitionMapper = processDefinitionMapper;
    }

    public String convert(ProcessDslPayload payload, String processDefinitionId, int version) {
        BpmnModel model = new BpmnModel();
        model.setTargetNamespace(WESTFLOW_NS);

        Process process = new Process();
        process.setId(payload.processKey());
        process.setName(payload.processName());
        process.setDocumentation(buildProcessDocumentation(processDefinitionId, version, payload));
        process.setExecutable(true);
        model.addProcess(process);

        Map<String, ProcessDslPayload.Node> nodeById = new LinkedHashMap<>();
        payload.nodes().forEach(node -> nodeById.put(node.id(), node));

        payload.nodes().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Node::id))
                .map(this::toFlowElement)
                .forEach(process::addFlowElement);

        payload.edges().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Edge::id))
                .map(edge -> toSequenceFlow(edge, nodeById.get(edge.source())))
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
            case "subprocess" -> buildSubprocessCallActivity(node, config);
            case "dynamic-builder" -> buildDynamicBuilderPlaceholder(node, config);
            case "cc", "supervise", "meeting", "read", "circulate" -> buildCcTask(node, config);
            case "condition" -> buildExclusiveGateway(node, config);
            case "inclusive_split" -> buildInclusiveGateway(node, "split");
            case "inclusive_join" -> buildInclusiveGateway(node, "join");
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
        List<String> roleCodes = stringListValue(assignment.get("roleCodes"));
        String departmentRef = stringValue(assignment.get("departmentRef"));
        String formFieldKey = stringValue(assignment.get("formFieldKey"));
        String formulaExpression = stringValue(assignment.get("formulaExpression"));
        String approvalMode = resolveApprovalMode(config);
        if (isCountersignApprovalMode(config, approvalMode)) {
            task.setAssignee("${" + countersignElementVariable(node.id()) + "}");
            task.setLoopCharacteristics(buildCountersignLoopCharacteristics(node.id(), approvalMode));
        } else if (userIds.size() == 1) {
            task.setAssignee(userIds.get(0));
        } else if (!userIds.isEmpty()) {
            task.setCandidateUsers(userIds);
        } else if (!roleCodes.isEmpty()) {
            task.setCandidateGroups(roleCodes);
        } else if (departmentRef != null) {
            task.setCandidateGroups(List.of(departmentRef));
        } else if (formFieldKey != null) {
            task.setAssignee("${" + formFieldKey + "}");
        } else if (formulaExpression != null) {
            task.setAssignee(wrapExpression(formulaExpression));
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
        addExtensionAttribute(task, "ccSemanticMode", node.type());
        return task;
    }

    // 子流程节点统一映射为 Flowable callActivity。
    private CallActivity buildSubprocessCallActivity(ProcessDslPayload.Node node, Map<String, Object> config) {
        CallActivity activity = new CallActivity();
        activity.setId(node.id());
        activity.setName(node.name());
        String calledProcessKey = stringValue(config.get("calledProcessKey"));
        String calledVersionPolicy = stringValue(config.get("calledVersionPolicy"));
        Integer calledVersion = integerValue(config.get("calledVersion"));
        String childStartStrategy = resolveSubprocessChildStartStrategy(config);
        ProcessDefinitionRecord fixedVersionRecord = resolveFixedVersionSubprocessDefinition(
                calledProcessKey,
                calledVersionPolicy,
                calledVersion
        );
        if (fixedVersionRecord != null) {
            activity.setCalledElementType("id");
            activity.setCalledElement(fixedVersionRecord.flowableDefinitionId());
        } else if ("SCENE_BINDING".equals(childStartStrategy)) {
            activity.setCalledElement(wrapExpression(subprocessCalledElementVariable(node.id())));
        } else {
            activity.setCalledElement(calledProcessKey);
        }
        activity.setInParameters(buildIoParameters(config.get("inputMappings")));
        activity.setOutParameters(buildIoParameters(config.get("outputMappings")));
        return activity;
    }

    private ProcessDefinitionRecord resolveFixedVersionSubprocessDefinition(
            String calledProcessKey,
            String calledVersionPolicy,
            Integer calledVersion
    ) {
        if (!"FIXED_VERSION".equals(calledVersionPolicy) || calledProcessKey == null || calledProcessKey.isBlank() || calledVersion == null) {
            return null;
        }
        if (processDefinitionMapper == null) {
            return null;
        }
        ProcessDefinitionRecord record = processDefinitionMapper.selectPublishedByProcessKeyAndVersion(calledProcessKey, calledVersion);
        if (record == null) {
            throw new IllegalStateException("FIXED_VERSION 子流程未找到已发布定义: processKey=" + calledProcessKey + ", version=" + calledVersion);
        }
        if (record.flowableDefinitionId() == null || record.flowableDefinitionId().isBlank()) {
            throw new IllegalStateException("FIXED_VERSION 子流程缺少 Flowable definition id: processKey=" + calledProcessKey + ", version=" + calledVersion);
        }
        return record;
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

    // 包容网关分别承载分支和汇聚语义。
    private InclusiveGateway buildInclusiveGateway(ProcessDslPayload.Node node, String gatewayType) {
        InclusiveGateway gateway = new InclusiveGateway();
        gateway.setId(node.id());
        gateway.setName(node.name());
        addExtensionAttribute(gateway, "gatewayType", gatewayType);
        if ("split".equals(gatewayType)) {
            gateway.setExecutionListeners(buildInclusiveBranchSelectionListeners());
        }
        return gateway;
    }

    private List<FlowableListener> buildInclusiveBranchSelectionListeners() {
        FlowableListener listener = new FlowableListener();
        listener.setEvent("start");
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        listener.setImplementation("${inclusiveBranchSelectionListener}");
        return List.of(listener);
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

    // 动态构建节点先映射为平台钩子的占位服务任务，后续由运行态服务补充附属结构。
    private ServiceTask buildDynamicBuilderPlaceholder(ProcessDslPayload.Node node, Map<String, Object> config) {
        ServiceTask task = new ServiceTask();
        task.setId(node.id());
        task.setName(node.name());
        task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        task.setImplementation(DEFAULT_DYNAMIC_BUILDER_DELEGATE);
        addExtensionAttribute(task, "taskKind", "DYNAMIC_BUILDER");
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
    private SequenceFlow toSequenceFlow(ProcessDslPayload.Edge edge, ProcessDslPayload.Node sourceNode) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(edge.id());
        flow.setSourceRef(edge.source());
        flow.setTargetRef(edge.target());
        flow.setName(edge.label());
        Map<String, Object> condition = mapValue(edge.condition());
        String expression = resolveConditionExpression(condition);
        addExtensionAttribute(flow, "priority", edge.priority() == null ? null : String.valueOf(edge.priority()));
        addExtensionAttribute(flow, "branchPriority", edge.priority() == null ? null : String.valueOf(edge.priority()));
        if (sourceNode != null && "inclusive_split".equals(sourceNode.type())) {
            Map<String, Object> sourceConfig = mapValue(sourceNode.config());
            addExtensionAttribute(flow, "defaultBranchId", stringValue(sourceConfig.get("defaultBranchId")));
            addExtensionAttribute(flow, "requiredBranchCount", stringValue(sourceConfig.get("requiredBranchCount")));
            addExtensionAttribute(flow, "branchMergePolicy", stringValue(sourceConfig.get("branchMergePolicy")));
            addExtensionAttribute(flow, "branchConditionExpression", expression);
            flow.setConditionExpression(inclusiveSelectionExpression(edge.id()));
            addExtensionElement(flow, "branchPriority", edge.priority() == null ? null : String.valueOf(edge.priority()));
            addExtensionElement(flow, "defaultBranchId", stringValue(sourceConfig.get("defaultBranchId")));
            addExtensionElement(flow, "requiredBranchCount", stringValue(sourceConfig.get("requiredBranchCount")));
            addExtensionElement(flow, "branchMergePolicy", stringValue(sourceConfig.get("branchMergePolicy")));
            addExtensionElement(flow, "branchConditionExpression", expression);
        } else if (expression != null) {
            flow.setConditionExpression(normalizeSequenceFlowConditionExpression(expression));
        }
        String conditionType = stringValue(condition.get("type"));
        if (conditionType != null) {
            addExtensionAttribute(flow, "conditionType", conditionType);
        }
        addExtensionAttribute(flow, "conditionFieldKey", stringValue(condition.get("fieldKey")));
        addExtensionAttribute(flow, "conditionOperator", stringValue(condition.get("operator")));
        addExtensionAttribute(flow, "conditionValue", serializeConditionValue(condition.get("value")));
        addExtensionAttribute(flow, "conditionFormulaExpression", stringValue(condition.get("formulaExpression")));
        return flow;
    }

    private String resolveConditionExpression(Map<String, Object> condition) {
        String expression = stringValue(condition.get("expression"));
        if (expression != null) {
            return expression;
        }
        String type = stringValue(condition.get("type"));
        if (type == null) {
            return null;
        }
        if ("FIELD".equals(type)) {
            return buildFieldConditionExpression(condition);
        }
        if ("FORMULA".equals(type)) {
            String formulaExpression = stringValue(condition.get("formulaExpression"));
            if (formulaExpression != null) {
                return formulaExpression;
            }
            return stringValue(condition.get("formula"));
        }
        return null;
    }

    private String buildFieldConditionExpression(Map<String, Object> condition) {
        String fieldKey = stringValue(condition.get("fieldKey"));
        String operator = stringValue(condition.get("operator"));
        Object value = condition.get("value");
        if (fieldKey == null || operator == null || value == null) {
            return null;
        }
        String mappedOperator = switch (operator) {
            case "EQ" -> "==";
            case "NE" -> "!=";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LT" -> "<";
            case "LE" -> "<=";
            default -> null;
        };
        if (mappedOperator == null) {
            return null;
        }
        return "${" + fieldKey + " " + mappedOperator + " " + stringifyConditionValue(value) + "}";
    }

    private String normalizeSequenceFlowConditionExpression(String expression) {
        return wrapExpression(expression);
    }

    private String inclusiveSelectionExpression(String edgeId) {
        return "${westflowInclusiveSelected_" + edgeId + "}";
    }

    private String subprocessCalledElementVariable(String nodeId) {
        return "wfSubprocessCalledElement_" + nodeId;
    }

    private String wrapExpression(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return "${" + trimmed + "}";
    }

    // 把 DSL 节点元数据和配置统一写成扩展属性，供运行态读取。
    private void attachNodeMetadata(BaseElement element, ProcessDslPayload.Node node, Map<String, Object> config) {
        addExtensionAttribute(element, "dslNodeId", node.id());
        addExtensionAttribute(element, "dslNodeType", node.type());
        addExtensionAttribute(element, "description", normalizeNullable(node.description()));
        flattenConfig(config).forEach((key, value) -> addExtensionAttribute(element, key, value));
        if ("inclusive_split".equals(node.type())) {
            addInclusiveStrategyExtensions(element, config);
        }
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
        attrs.put("calledProcessKey", stringValue(config.get("calledProcessKey")));
        attrs.put("calledVersionPolicy", stringValue(config.get("calledVersionPolicy")));
        attrs.put("calledVersion", stringValue(config.get("calledVersion")));
        attrs.put("businessBindingMode", stringValue(config.get("businessBindingMode")));
        attrs.put("terminatePolicy", stringValue(config.get("terminatePolicy")));
        attrs.put("childFinishPolicy", stringValue(config.get("childFinishPolicy")));
        attrs.put("defaultBranchId", stringValue(config.get("defaultBranchId")));
        attrs.put("requiredBranchCount", stringValue(config.get("requiredBranchCount")));
        attrs.put("branchMergePolicy", stringValue(config.get("branchMergePolicy")));
        if (isSubprocessConfig(config)) {
            attrs.put("callScope", resolveSubprocessCallScope(config));
            attrs.put("joinMode", resolveSubprocessJoinMode(config));
            attrs.put("childStartStrategy", resolveSubprocessChildStartStrategy(config));
            attrs.put("parentResumeStrategy", resolveSubprocessParentResumeStrategy(config));
        }
        attrs.put("buildMode", stringValue(config.get("buildMode")));
        attrs.put("sourceMode", normalizeDynamicBuilderSourceMode(stringValue(config.get("sourceMode"))));
        attrs.put("ruleExpression", stringValue(config.get("ruleExpression")));
        attrs.put("manualTemplateCode", stringValue(config.get("manualTemplateCode")));
        attrs.put("sceneCode", stringValue(config.get("sceneCode")));
        attrs.put("executionStrategy", stringValue(config.get("executionStrategy")));
        attrs.put("fallbackStrategy", stringValue(config.get("fallbackStrategy")));
        attrs.put("appendPolicy", stringValue(config.get("appendPolicy")));
        attrs.put("maxGeneratedCount", stringValue(config.get("maxGeneratedCount")));
        attrs.put("inputMappings", serializeMappings(config.get("inputMappings")));
        attrs.put("outputMappings", serializeMappings(config.get("outputMappings")));

        Map<String, Object> assignment = mapValue(config.get("assignment"));
        attrs.put("assignmentMode", stringValue(assignment.get("mode")));
        attrs.put("userIds", joinValues(assignment.get("userIds")));
        attrs.put("roleCodes", joinValues(assignment.get("roleCodes")));
        attrs.put("departmentRef", stringValue(assignment.get("departmentRef")));
        attrs.put("formFieldKey", stringValue(assignment.get("formFieldKey")));
        attrs.put("formulaExpression", stringValue(assignment.get("formulaExpression")));

        Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
        attrs.put("approvalPolicyType", stringValue(approvalPolicy.get("type")));
        attrs.put("voteThreshold", stringValue(approvalPolicy.get("voteThreshold")));
        attrs.put("approvalMode", resolveApprovalMode(config));
        attrs.put("reapprovePolicy", stringValue(config.get("reapprovePolicy")));
        attrs.put("autoFinishRemaining", booleanValue(config.get("autoFinishRemaining")));

        Map<String, Object> voteRule = mapValue(config.get("voteRule"));
        attrs.put("voteThresholdPercent", stringValue(voteRule.get("thresholdPercent")));
        attrs.put("votePassCondition", stringValue(voteRule.get("passCondition")));
        attrs.put("voteRejectCondition", stringValue(voteRule.get("rejectCondition")));
        attrs.put("voteWeights", serializeVoteWeights(voteRule.get("weights")));

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

        Map<String, Object> escalationPolicy = mapValue(config.get("escalationPolicy"));
        attrs.put("escalationEnabled", booleanValue(escalationPolicy.get("enabled")));
        attrs.put("escalationAfterMinutes", stringValue(escalationPolicy.get("afterMinutes")));
        attrs.put("escalationTargetMode", stringValue(escalationPolicy.get("targetMode")));
        attrs.put("escalationTargetUserIds", joinValues(escalationPolicy.get("targetUserIds")));
        attrs.put("escalationTargetRoleCodes", joinValues(escalationPolicy.get("targetRoleCodes")));
        attrs.put("escalationChannels", joinValues(escalationPolicy.get("channels")));

        Map<String, Object> targets = mapValue(config.get("targets"));
        attrs.put("targetMode", stringValue(targets.get("mode")));
        attrs.put("targetUserIds", joinValues(targets.get("userIds")));
        attrs.put("targetRoleCodes", joinValues(targets.get("roleCodes")));
        attrs.put("targetDepartmentRef", stringValue(targets.get("departmentRef")));

        return attrs;
    }

    private String resolveSubprocessCallScope(Map<String, Object> config) {
        return normalizeSubprocessValue(config.get("callScope"), "CHILD_ONLY");
    }

    private String resolveSubprocessJoinMode(Map<String, Object> config) {
        return normalizeSubprocessValue(config.get("joinMode"), "AUTO_RETURN");
    }

    private String resolveSubprocessChildStartStrategy(Map<String, Object> config) {
        return normalizeSubprocessValue(config.get("childStartStrategy"), "LATEST_PUBLISHED");
    }

    private String resolveSubprocessParentResumeStrategy(Map<String, Object> config) {
        return normalizeSubprocessValue(config.get("parentResumeStrategy"), "AUTO_RETURN");
    }

    private boolean isSubprocessConfig(Map<String, Object> config) {
        return config.containsKey("calledProcessKey")
                || config.containsKey("calledVersionPolicy")
                || config.containsKey("childFinishPolicy")
                || config.containsKey("callScope")
                || config.containsKey("joinMode")
                || config.containsKey("childStartStrategy")
                || config.containsKey("parentResumeStrategy");
    }

    private String normalizeSubprocessValue(Object rawValue, String defaultValue) {
        String value = stringValue(rawValue);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    // 会签模式统一映射到 Flowable 多实例用户任务。
    private MultiInstanceLoopCharacteristics buildCountersignLoopCharacteristics(String nodeId, String approvalMode) {
        MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
        loop.setSequential("SEQUENTIAL".equals(approvalMode));
        loop.setInputDataItem("${" + countersignCollectionVariable(nodeId) + "}");
        loop.setElementVariable(countersignElementVariable(nodeId));
        if ("OR_SIGN".equals(approvalMode)) {
            loop.setCompletionCondition("${" + countersignDecisionVariable(nodeId) + " == 'APPROVED'}");
        } else if ("VOTE".equals(approvalMode)) {
            loop.setCompletionCondition("${" + countersignDecisionVariable(nodeId) + " == 'APPROVED' || " + countersignDecisionVariable(nodeId) + " == 'REJECTED'}");
        }
        return loop;
    }

    private String resolveApprovalMode(Map<String, Object> config) {
        String approvalMode = stringValue(config.get("approvalMode"));
        if (approvalMode != null) {
            return approvalMode;
        }
        Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
        return stringValue(approvalPolicy.get("type"));
    }

    private String normalizeDynamicBuilderSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return null;
        }
        return switch (sourceMode.trim().toUpperCase()) {
            case "RULE", "RULE_DRIVEN" -> "RULE_DRIVEN";
            case "MANUAL_TEMPLATE", "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> sourceMode;
        };
    }

    private boolean isCountersignApprovalMode(Map<String, Object> config, String approvalMode) {
        return config.containsKey("approvalMode")
                && List.of("SEQUENTIAL", "PARALLEL", "OR_SIGN", "VOTE").contains(approvalMode);
    }

    private String countersignCollectionVariable(String nodeId) {
        return "wfCountersignAssignees_" + nodeId;
    }

    private String countersignElementVariable(String nodeId) {
        return "wfCountersignAssignee_" + nodeId;
    }

    private String countersignDecisionVariable(String nodeId) {
        return "wfCountersignDecision_" + nodeId;
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

    private void addExtensionElement(BaseElement element, String name, String value) {
        if (name == null || value == null || value.isBlank()) {
            return;
        }
        org.flowable.bpmn.model.ExtensionElement extensionElement = new org.flowable.bpmn.model.ExtensionElement();
        extensionElement.setName(name);
        extensionElement.setNamespace(WESTFLOW_NS);
        extensionElement.setNamespacePrefix(WESTFLOW_PREFIX);
        extensionElement.setElementText(value);
        element.addExtensionElement(extensionElement);
    }

    private void addInclusiveStrategyExtensions(BaseElement element, Map<String, Object> config) {
        addExtensionElement(element, "defaultBranchId", stringValue(config.get("defaultBranchId")));
        addExtensionElement(element, "requiredBranchCount", stringValue(config.get("requiredBranchCount")));
        addExtensionElement(element, "branchMergePolicy", stringValue(config.get("branchMergePolicy")));
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

    private String serializeVoteWeights(Object value) {
        if (!(value instanceof List<?> weights) || weights.isEmpty()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (Object weightItem : weights) {
            if (!(weightItem instanceof Map<?, ?> weightMap)) {
                continue;
            }
            Object userId = weightMap.get("userId");
            Object weight = weightMap.get("weight");
            if (userId == null || weight == null) {
                continue;
            }
            String userIdText = String.valueOf(userId).trim();
            String weightText = String.valueOf(weight).trim();
            if (userIdText.isBlank() || weightText.isBlank()) {
                continue;
            }
            items.add(userIdText + ":" + weightText);
        }
        return items.isEmpty() ? null : String.join(",", items);
    }

    private String serializeMappings(Object value) {
        if (!(value instanceof List<?> mappings) || mappings.isEmpty()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (Object mappingItem : mappings) {
            if (!(mappingItem instanceof Map<?, ?> mappingMap)) {
                continue;
            }
            Object source = mappingMap.get("source");
            Object target = mappingMap.get("target");
            if (source == null || target == null) {
                continue;
            }
            String sourceText = String.valueOf(source).trim();
            String targetText = String.valueOf(target).trim();
            if (sourceText.isBlank() || targetText.isBlank()) {
                continue;
            }
            items.add(sourceText + "->" + targetText);
        }
        return items.isEmpty() ? null : String.join(",", items);
    }

    private List<IOParameter> buildIoParameters(Object value) {
        if (!(value instanceof List<?> mappings) || mappings.isEmpty()) {
            return List.of();
        }
        List<IOParameter> parameters = new ArrayList<>();
        for (Object mappingItem : mappings) {
            if (!(mappingItem instanceof Map<?, ?> mappingMap)) {
                continue;
            }
            String source = stringValue(mappingMap.get("source"));
            String target = stringValue(mappingMap.get("target"));
            if (source == null || target == null) {
                continue;
            }
            IOParameter parameter = new IOParameter();
            parameter.setSource(source);
            parameter.setTarget(target);
            parameters.add(parameter);
        }
        return parameters;
    }

    private String serializeConditionValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                String serialized = serializeConditionValue(item);
                if (serialized != null) {
                    items.add(serialized);
                }
            }
            return items.isEmpty() ? null : String.join(",", items);
        }
        return String.valueOf(value);
    }

    private String stringifyConditionValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value);
        if (text.startsWith("${") && text.endsWith("}")) {
            return text;
        }
        return "'" + text.replace("'", "\\'") + "'";
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

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
