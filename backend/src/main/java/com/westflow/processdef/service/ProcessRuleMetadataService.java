package com.westflow.processdef.service;

import com.westflow.processdef.api.ProcessDefinitionDetailResponse;
import com.westflow.processdef.api.ProcessRuleMetadataResponse;
import com.westflow.processdef.api.ProcessRuleMetadataResponse.RuleSnippet;
import com.westflow.processdef.api.ProcessRuleMetadataResponse.RuleVariable;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.support.WorkflowFormulaEvaluator;
import com.westflow.processruntime.support.WorkflowFormulaEvaluator.FunctionMetadata;
import com.westflow.processruntime.support.WorkflowFormulaEvaluator.FunctionParameterMetadata;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 分支规则编辑器的元数据服务。
@Service
@RequiredArgsConstructor
public class ProcessRuleMetadataService {

    private final ProcessDefinitionService processDefinitionService;

    // 构建规则编辑器元数据。
    public ProcessRuleMetadataResponse build(String processDefinitionId, String nodeId) {
        ProcessDefinitionDetailResponse detail = resolveDetail(processDefinitionId);
        ProcessDslPayload dsl = detail == null ? null : detail.dsl();
        return new ProcessRuleMetadataResponse(
                buildVariables(dsl, nodeId),
                buildFunctions(),
                buildSnippets()
        );
    }

    private ProcessDefinitionDetailResponse resolveDetail(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        return processDefinitionService.detail(processDefinitionId.trim());
    }

    private List<RuleVariable> buildVariables(ProcessDslPayload dsl, String nodeId) {
        List<RuleVariable> variables = new ArrayList<>();
        variables.add(new RuleVariable(
                "form",
                "表单主表字段",
                "group",
                "form",
                dsl == null || dsl.formFields() == null || dsl.formFields().isEmpty()
                        ? "当前流程未配置表单字段。"
                        : "当前流程表单主表的可引用字段。",
                null,
                buildFormFields(dsl)
        ));
        variables.add(new RuleVariable(
                "subtable",
                "子表聚合上下文",
                "group",
                "subtable",
                "子表仅暴露聚合型上下文，适合按行数、合计值和汇总结果配置规则。",
                null,
                buildSubtableAggregateVariables()
        ));
        variables.add(new RuleVariable(
                "process",
                "流程上下文",
                "group",
                "process",
                "流程实例和发起信息，供分支规则直接引用。",
                null,
                buildProcessContextVariables(dsl)
        ));
        variables.add(new RuleVariable(
                "node",
                "节点上下文",
                "group",
                "node",
                nodeId == null || nodeId.isBlank()
                        ? "当前任务和节点信息，供分支规则直接引用。"
                        : "当前任务和节点信息，已按所选节点做上下文预置。",
                null,
                buildNodeContextVariables(dsl, nodeId)
        ));
        variables.add(new RuleVariable(
                "system",
                "系统上下文",
                "group",
                "system",
                "系统运行时可直接使用的基础上下文。",
                null,
                buildSystemContextVariables()
        ));
        return variables;
    }

    private List<RuleVariable> buildFormFields(ProcessDslPayload dsl) {
        if (dsl == null || dsl.formFields() == null) {
            return List.of();
        }
        return dsl.formFields().stream()
                .map(field -> toLeafVariable(
                        field.fieldKey(),
                        field.label(),
                        field.valueType(),
                        "form",
                        "表单主表字段，可直接在规则中引用。",
                        field.fieldKey()
                ))
                .toList();
    }

    private List<RuleVariable> buildSubtableAggregateVariables() {
        return List.of(
                toLeafVariable(
                        "subtable.rowCount",
                        "子表行数",
                        "number",
                        "subtable",
                        "子表明细行的数量。",
                        "subtable.rowCount"
                ),
                toLeafVariable(
                        "subtable.sum",
                        "子表合计值",
                        "number",
                        "subtable",
                        "子表聚合后的合计值占位，复杂场景可改用后端注册公式。",
                        "subtable.sum"
                ),
                toLeafVariable(
                        "subtable.any",
                        "子表任一满足",
                        "boolean",
                        "subtable",
                        "子表中任一明细满足条件时返回真。",
                        "subtable.any"
                ),
                toLeafVariable(
                        "subtable.all",
                        "子表全部满足",
                        "boolean",
                        "subtable",
                        "子表中全部明细满足条件时返回真。",
                        "subtable.all"
                )
        );
    }

    private List<RuleVariable> buildProcessContextVariables(ProcessDslPayload dsl) {
        return List.of(
                toLeafVariable("processDefinitionId", "流程定义标识", "string", "process", "当前流程定义的主键。", "processDefinitionId"),
                toLeafVariable("processKey", "流程键", "string", "process", "当前流程的业务键。", "processKey"),
                toLeafVariable("processName", "流程名称", "string", "process", "当前流程的展示名称。", "processName"),
                toLeafVariable("processVersion", "流程版本", "number", "process", "当前流程定义版本号。", "processVersion"),
                toLeafVariable("processCategory", "流程分类", "string", "process", "当前流程的分类。", "processCategory"),
                toLeafVariable("processInstanceId", "流程实例标识", "string", "process", "运行时流程实例标识。", "processInstanceId"),
                toLeafVariable("businessType", "业务类型", "string", "process", "运行态业务类型。", "businessType"),
                toLeafVariable("businessId", "业务标识", "string", "process", "运行态业务主键。", "businessId"),
                toLeafVariable("initiatorId", "发起人标识", "string", "process", "流程发起人的用户标识。", "initiatorId"),
                toLeafVariable("initiatorName", "发起人姓名", "string", "process", "流程发起人的显示名。", "initiatorName"),
                toLeafVariable("initiatorPostId", "发起岗位标识", "string", "process", "流程发起时的任职标识。", "initiatorPostId"),
                toLeafVariable("initiatorPostName", "发起岗位名称", "string", "process", "流程发起时的岗位名称。", "initiatorPostName"),
                toLeafVariable("initiatorDepartmentId", "发起部门标识", "string", "process", "流程发起时的部门标识。", "initiatorDepartmentId"),
                toLeafVariable("initiatorDepartmentName", "发起部门名称", "string", "process", "流程发起时的部门名称。", "initiatorDepartmentName")
        );
    }

    private List<RuleVariable> buildNodeContextVariables(ProcessDslPayload dsl, String nodeId) {
        ProcessDslPayload.Node node = resolveNode(dsl, nodeId);
        return List.of(
                toLeafVariable("taskId", "任务标识", "string", "node", "当前待办任务标识。", "taskId"),
                toLeafVariable("taskName", "任务名称", "string", "node", "当前待办任务名称。", "taskName"),
                toLeafVariable("taskDefinitionKey", "任务定义键", "string", "node", "当前任务定义键。", "taskDefinitionKey"),
                toLeafVariable("currentNodeId", "当前节点标识", "string", "node", "当前流程节点标识。", "currentNodeId"),
                toLeafVariable("currentNodeKey", "当前节点键", "string", "node", "当前节点业务键。", "currentNodeKey"),
                toLeafVariable("currentNodeName", "当前节点名称", "string", "node", "当前节点显示名。", "currentNodeName"),
                toLeafVariable("currentNodeType", "当前节点类型", "string", "node", "当前节点类型。", "currentNodeType"),
                toLeafVariable("assigneeId", "处理人标识", "string", "node", "当前处理人标识。", "assigneeId"),
                toLeafVariable("assigneeName", "处理人姓名", "string", "node", "当前处理人姓名。", "assigneeName"),
                toLeafVariable("candidateUserId", "候选用户标识", "string", "node", "当前候选用户标识。", "candidateUserId"),
                toLeafVariable("candidateGroupId", "候选组标识", "string", "node", "当前候选组标识。", "candidateGroupId")
        );
    }

    private List<RuleVariable> buildSystemContextVariables() {
        return List.of(
                toLeafVariable("currentUserId", "当前用户标识", "string", "system", "登录用户的用户标识。", "currentUserId"),
                toLeafVariable("currentUserName", "当前用户姓名", "string", "system", "登录用户的显示名。", "currentUserName"),
                toLeafVariable("currentDepartmentId", "当前部门标识", "string", "system", "登录用户当前任职的部门标识。", "currentDepartmentId"),
                toLeafVariable("currentDepartmentName", "当前部门名称", "string", "system", "登录用户当前任职的部门名称。", "currentDepartmentName"),
                toLeafVariable("currentPostId", "当前岗位标识", "string", "system", "登录用户当前任职的岗位标识。", "currentPostId"),
                toLeafVariable("currentPostName", "当前岗位名称", "string", "system", "登录用户当前任职的岗位名称。", "currentPostName"),
                toLeafVariable("now", "当前时间", "datetime", "system", "规则执行时的当前时间。", "now"),
                toLeafVariable("today", "当天日期", "date", "system", "规则执行时的当天日期。", "today")
        );
    }

    private List<ProcessRuleMetadataResponse.RuleFunction> buildFunctions() {
        return WorkflowFormulaEvaluator.functionMetadata().stream()
                .map(this::toFunction)
                .toList();
    }

    private List<RuleSnippet> buildSnippets() {
        return List.of(
                new RuleSnippet(
                        "boolean-template",
                        "布尔条件模板",
                        "最常见的条件判断模板。",
                        "${$days > 3}"
                ),
                new RuleSnippet(
                        "if-else",
                        "条件分支模板",
                        "当条件成立时返回真值，否则返回假值。",
                        "${ifElse($days > 3, true, false)}"
                ),
                new RuleSnippet(
                        "contains",
                        "包含判断模板",
                        "判断字符串或集合是否包含目标值。",
                        "${contains($roleNames, 'HR')}"
                ),
                new RuleSnippet(
                        "days-between",
                        "日期差值模板",
                        "计算两个日期之间的天数。",
                        "${daysBetween($startDate, $endDate)}"
                ),
                new RuleSnippet(
                        "is-blank",
                        "空值判断模板",
                        "判断字段或表达式是否为空白。",
                        "${isBlank($comment)}"
                )
        );
    }

    private RuleVariable toLeafVariable(
            String key,
            String label,
            String valueType,
            String scope,
            String description,
            String expression
    ) {
        return new RuleVariable(key, label, valueType, scope, description, expression, List.of());
    }

    private ProcessRuleMetadataResponse.RuleFunction toFunction(FunctionMetadata metadata) {
        String signature = buildSignature(metadata);
        String label = switch (metadata.name()) {
            case "ifElse" -> "条件分支";
            case "contains" -> "包含判断";
            case "daysBetween" -> "日期差值";
            case "isBlank" -> "空值判断";
            case "isLongLeave" -> "长假判断";
            default -> metadata.name();
        };
        return new ProcessRuleMetadataResponse.RuleFunction(
                metadata.name(),
                label,
                signature,
                metadata.description(),
                metadata.category(),
                metadata.example()
        );
    }

    private String buildSignature(FunctionMetadata metadata) {
        String arguments = metadata.parameters().stream()
                .map(FunctionParameterMetadata::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return metadata.name() + "(" + arguments + ")";
    }

    private ProcessDslPayload.Node resolveNode(ProcessDslPayload dsl, String nodeId) {
        if (dsl == null || dsl.nodes() == null || nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return dsl.nodes().stream()
                .filter(node -> nodeId.equals(node.id()))
                .findFirst()
                .orElse(null);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
