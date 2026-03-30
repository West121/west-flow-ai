package com.westflow.system.agent.api;

import java.util.List;

/**
 * 代理关系表单选项响应。
 */
public record SystemAgentFormOptionsResponse(
        // 主体用户选项。
        List<UserOption> principalUsers,
        // 委派用户选项。
        List<UserOption> delegateUsers,
        // 状态选项。
        List<StatusOption> statusOptions
) {
    /**
     * 用户选项。
     */
    public record UserOption(
            // 用户标识。
            String userId,
            // 展示名称。
            String displayName,
            // 登录用户名。
            String username,
            // 部门名称。
            String departmentName,
            // 岗位名称。
            String postName,
            // 是否启用。
            boolean enabled
    ) {
    }

    /**
     * 状态选项。
     */
    public record StatusOption(
            // 选项值。
            String value,
            // 选项名称。
            String label
    ) {
    }
}
