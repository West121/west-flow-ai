package com.westflow.processruntime.action;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RuntimeActionCoreSupportService {

    public String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? currentUserId() : userId.trim();
    }

    public String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    public String stringValueOrDefault(Object value, String defaultValue) {
        String text = stringValue(value);
        return text == null ? defaultValue : text;
    }

    public ContractException taskNotFound(String taskId) {
        return new ContractException(
                "PROCESS.TASK_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "任务不存在",
                Map.of("taskId", taskId)
        );
    }

    public ContractException resourceNotFound(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                details
        );
    }

    public ContractException actionNotAllowed(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.ACTION_NOT_ALLOWED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                message,
                details
        );
    }

    public String requireTargetUserId(String targetUserId, String taskId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserId 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return targetUserId.trim();
    }

    public String normalizeTargetUserId(String userId, String fieldName) {
        if (userId == null || userId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    fieldName + " 不能为空",
                    Map.of("field", fieldName)
            );
        }
        return userId.trim();
    }

    public List<String> normalizeTargetUserIds(List<String> targetUserIds, String taskId) {
        List<String> normalized = targetUserIds == null ? List.of() : targetUserIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserIds 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return normalized;
    }

    public String normalizeAppendPolicy(String appendPolicy) {
        if (appendPolicy == null || appendPolicy.isBlank()) {
            return "SERIAL_AFTER_CURRENT";
        }
        String normalized = appendPolicy.trim().toUpperCase();
        return List.of("SERIAL_AFTER_CURRENT", "PARALLEL_WITH_CURRENT", "SERIAL_BEFORE_NEXT").contains(normalized)
                ? normalized
                : "SERIAL_AFTER_CURRENT";
    }

    public String normalizeVersionPolicy(String versionPolicy) {
        if (versionPolicy == null || versionPolicy.isBlank()) {
            return "LATEST_PUBLISHED";
        }
        String normalized = versionPolicy.trim().toUpperCase();
        return List.of("LATEST_PUBLISHED", "FIXED_VERSION").contains(normalized)
                ? normalized
                : "LATEST_PUBLISHED";
    }

    public String normalizeCalledProcessKey(String calledProcessKey, String taskId) {
        if (calledProcessKey == null || calledProcessKey.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "calledProcessKey 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return calledProcessKey.trim();
    }
}
