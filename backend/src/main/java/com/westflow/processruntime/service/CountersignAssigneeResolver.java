package com.westflow.processruntime.service;

import com.westflow.common.error.ContractException;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import com.westflow.system.user.mapper.SystemUserMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 统一解析会签节点的处理人集合，支持人员、角色、部门、表单字段和公式来源。
 */
@Service
@RequiredArgsConstructor
public class CountersignAssigneeResolver {

    private final SystemUserMapper systemUserMapper;
    private final SystemDepartmentMapper systemDepartmentMapper;

    /**
     * 解析会签节点的实际处理人列表。
     */
    public List<String> resolve(Map<String, Object> assignment, Map<String, Object> startVariables) {
        String mode = stringValue(assignment.get("mode"));
        if (mode == null) {
            return List.of();
        }
        List<String> resolved = switch (mode) {
            case "USER" -> stringListValue(assignment.get("userIds"));
            case "ROLE" -> systemUserMapper.selectEnabledUserIdsByRoleRefs(stringListValue(assignment.get("roleCodes")));
            case "DEPARTMENT" -> systemUserMapper.selectEnabledUserIdsByDepartmentIds(
                    stringListValue(assignment.get("departmentRef"))
            );
            case "DEPARTMENT_AND_CHILDREN" -> systemUserMapper.selectEnabledUserIdsByDepartmentIds(
                    resolveDepartmentIdsWithDescendants(stringValue(assignment.get("departmentRef")))
            );
            case "FORM_FIELD" -> coerceUserIds(startVariables.get(stringValue(assignment.get("formFieldKey"))));
            case "FORMULA" -> coerceUserIds(evaluateFormula(stringValue(assignment.get("formulaExpression")), startVariables));
            default -> List.of();
        };
        return normalizeUserIds(resolved);
    }

    private Object evaluateFormula(String expression, Map<String, Object> startVariables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            return WorkflowFormulaEvaluator.execute(expression, startVariables);
        } catch (RuntimeException exception) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "会签公式解析失败",
                    Map.of("expression", expression, "reason", exception.getMessage())
            );
        }
    }

    private List<String> resolveDepartmentIdsWithDescendants(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> collected = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(departmentId);
        while (!queue.isEmpty()) {
            String currentDepartmentId = queue.poll();
            if (!collected.add(currentDepartmentId)) {
                continue;
            }
            queue.addAll(systemDepartmentMapper.selectDepartmentIdsByParentId(currentDepartmentId));
        }
        return List.copyOf(collected);
    }

    private List<String> coerceUserIds(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            LinkedHashSet<String> userIds = new LinkedHashSet<>();
            for (Object item : collection) {
                String userId = stringValue(item);
                if (userId == null) {
                    continue;
                }
                userIds.addAll(splitCommaSeparated(userId));
            }
            return List.copyOf(userIds);
        }
        return splitCommaSeparated(String.valueOf(value));
    }

    private List<String> splitCommaSeparated(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String normalized = rawValue.replace("[", "").replace("]", "");
        String[] parts = normalized.split("[,;]");
        List<String> userIds = new ArrayList<>();
        for (String part : parts) {
            String candidate = stringValue(part.strip().replace("\"", "").replace("'", ""));
            if (candidate != null && !userIds.contains(candidate)) {
                userIds.add(candidate);
            }
        }
        if (userIds.isEmpty()) {
            String candidate = stringValue(normalized);
            return candidate == null ? List.of() : List.of(candidate);
        }
        return List.copyOf(userIds);
    }

    private List<String> normalizeUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String userId : userIds) {
            String candidate = stringValue(userId);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> stringListValue(Object value) {
        if (value instanceof String stringValue) {
            return splitCommaSeparated(stringValue);
        }
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return List.of();
        }
        return collection.stream()
                .map(this::stringValue)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isBlank() ? null : stringValue;
    }
}
