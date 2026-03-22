package com.westflow.system.agent.api;

import java.util.List;

/**
 * 代理关系表单选项响应。
 */
public record SystemAgentFormOptionsResponse(
        List<UserOption> principalUsers,
        List<UserOption> delegateUsers,
        List<StatusOption> statusOptions
) {
    /**
     * 用户选项。
     */
    public record UserOption(
            String userId,
            String displayName,
            String username,
            String departmentName,
            String postName,
            boolean enabled
    ) {
    }

    /**
     * 状态选项。
     */
    public record StatusOption(
            String value,
            String label
    ) {
    }
}
