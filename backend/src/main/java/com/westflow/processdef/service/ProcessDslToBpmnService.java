package com.westflow.processdef.service;

import com.westflow.processdef.model.ProcessDslPayload;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
// 把设计器 DSL 转成最小可执行的 BPMN 片段。
public class ProcessDslToBpmnService {

    public String convert(ProcessDslPayload payload, String processDefinitionId, int version) {
        // 这里把设计器 DSL 归一成最小可执行的 BPMN 片段。
        StringBuilder builder = new StringBuilder();
        builder.append("<process id=\"")
                .append(escape(processDefinitionId))
                .append("\" key=\"")
                .append(escape(payload.processKey()))
                .append("\" name=\"")
                .append(escape(payload.processName()))
                .append("\" version=\"")
                .append(version)
                .append("\">");

        payload.nodes().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Node::id))
                .forEach(node -> builder.append(nodeXml(node)));

        payload.edges().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Edge::id))
                .forEach(edge -> builder.append(edgeXml(edge)));

        builder.append("</process>");
        return builder.toString();
    }

    private String nodeXml(ProcessDslPayload.Node node) {
        Map<String, Object> config = mapValue(node.config());
        return switch (node.type()) {
            case "start" -> emptyTag("startEvent", nodeAttrs(node, "initiatorEditable", booleanValue(config.get("initiatorEditable"))));
            case "approver" -> emptyTag("userTask", approverAttrs(node, config));
            case "cc" -> emptyTag("serviceTask", ccAttrs(node, config));
            case "condition" -> emptyTag("exclusiveGateway", nodeAttrs(node, "defaultEdgeId", stringValue(config.get("defaultEdgeId"))));
            case "parallel_split" -> emptyTag("parallelGateway", nodeAttrs(node, "gatewayType", "split"));
            case "parallel_join" -> emptyTag("parallelGateway", nodeAttrs(node, "gatewayType", "join"));
            case "timer" -> emptyTag("intermediateCatchEvent", timerAttrs(node, config));
            case "trigger" -> emptyTag("serviceTask", triggerAttrs(node, config));
            case "end" -> emptyTag("endEvent", nodeAttrs(node));
            default -> emptyTag("node", nodeAttrs(node, "type", node.type()));
        };
    }

    private Map<String, Object> approverAttrs(ProcessDslPayload.Node node, Map<String, Object> config) {
        // 审批节点要带上选人策略和表单字段绑定。
        Map<String, Object> attrs = new LinkedHashMap<>();
        Map<String, Object> assignment = mapValue(config.get("assignment"));
        Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
        attrs.put("id", node.id());
        attrs.put("name", node.name());
        attrs.put("description", node.description());
        attrs.put("assignmentMode", stringValue(assignment.get("mode")));
        attrs.put("userIds", joinValues(assignment.get("userIds")));
        attrs.put("roleCodes", joinValues(assignment.get("roleCodes")));
        attrs.put("departmentRef", stringValue(assignment.get("departmentRef")));
        attrs.put("formFieldKey", stringValue(assignment.get("formFieldKey")));
        attrs.put("approvalPolicyType", stringValue(approvalPolicy.get("type")));
        attrs.put("voteThreshold", stringValue(approvalPolicy.get("voteThreshold")));
        attrs.put("operations", joinValues(config.get("operations")));
        attrs.put("commentRequired", booleanValue(config.get("commentRequired")));
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
        return attrs;
    }

    private Map<String, Object> ccAttrs(ProcessDslPayload.Node node, Map<String, Object> config) {
        // 抄送节点只保留接收人范围和已阅要求。
        Map<String, Object> attrs = new LinkedHashMap<>();
        Map<String, Object> targets = mapValue(config.get("targets"));
        attrs.put("id", node.id());
        attrs.put("name", node.name());
        attrs.put("description", node.description());
        attrs.put("targetMode", stringValue(targets.get("mode")));
        attrs.put("userIds", joinValues(targets.get("userIds")));
        attrs.put("roleCodes", joinValues(targets.get("roleCodes")));
        attrs.put("departmentRef", stringValue(targets.get("departmentRef")));
        attrs.put("readRequired", booleanValue(config.get("readRequired")));
        return attrs;
    }

    private Map<String, Object> timerAttrs(ProcessDslPayload.Node node, Map<String, Object> config) {
        // 定时节点只保留调度方式和执行时间信息。
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", node.id());
        attrs.put("name", node.name());
        attrs.put("description", node.description());
        attrs.put("scheduleType", stringValue(config.get("scheduleType")));
        attrs.put("runAt", stringValue(config.get("runAt")));
        attrs.put("delayMinutes", stringValue(config.get("delayMinutes")));
        attrs.put("comment", stringValue(config.get("comment")));
        return attrs;
    }

    private Map<String, Object> triggerAttrs(ProcessDslPayload.Node node, Map<String, Object> config) {
        // 触发节点在 BPMN 中先落成 serviceTask，并带上触发器执行参数。
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", node.id());
        attrs.put("name", node.name());
        attrs.put("description", node.description());
        attrs.put("triggerMode", stringValue(config.get("triggerMode")));
        attrs.put("scheduleType", stringValue(config.get("scheduleType")));
        attrs.put("runAt", stringValue(config.get("runAt")));
        attrs.put("delayMinutes", stringValue(config.get("delayMinutes")));
        attrs.put("triggerKey", stringValue(config.get("triggerKey")));
        attrs.put("retryTimes", stringValue(config.get("retryTimes")));
        attrs.put("retryIntervalMinutes", stringValue(config.get("retryIntervalMinutes")));
        attrs.put("payloadTemplate", stringValue(config.get("payloadTemplate")));
        return attrs;
    }

    private Map<String, Object> nodeAttrs(ProcessDslPayload.Node node, Object... keyValues) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", node.id());
        attrs.put("name", node.name());
        if (node.description() != null) {
            attrs.put("description", node.description());
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = keyValues[index + 1];
            if (value != null) {
                attrs.put(key, value);
            }
        }
        return attrs;
    }

    private String edgeXml(ProcessDslPayload.Edge edge) {
        // 连线信息承载条件和优先级，便于后续流程引擎执行。
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", edge.id());
        attrs.put("source", edge.source());
        attrs.put("target", edge.target());
        attrs.put("priority", edge.priority());
        attrs.put("label", edge.label());
        Map<String, Object> condition = mapValue(edge.condition());
        attrs.put("conditionType", condition.isEmpty() ? null : stringValue(condition.get("type")));
        attrs.put("conditionExpression", condition.isEmpty() ? null : stringValue(condition.get("expression")));
        return emptyTag("transition", attrs);
    }

    private String emptyTag(String name, Map<String, Object> attrs) {
        StringBuilder builder = new StringBuilder();
        builder.append("<").append(name);
        attrs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                builder.append(" ").append(key).append("=\"").append(escape(String.valueOf(value))).append("\"");
            }
        });
        builder.append("/>");
        return builder.toString();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private String joinValues(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
