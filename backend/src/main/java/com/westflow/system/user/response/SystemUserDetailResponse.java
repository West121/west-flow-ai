package com.westflow.system.user.response;

import java.util.List;

/**
 * 用户详情响应。
 */
public record SystemUserDetailResponse(
        // 用户主键。
        String userId,
        // 显示名称。
        String displayName,
        // 登录用户名。
        String username,
        // 手机号。
        String mobile,
        // 邮箱。
        String email,
        // 主职公司主键。
        String companyId,
        // 主职公司名称。
        String companyName,
        // 主职部门主键。
        String departmentId,
        // 主职部门名称。
        String departmentName,
        // 主职岗位主键。
        String postId,
        // 主职岗位名称。
        String postName,
        // 主职角色主键列表。
        List<String> roleIds,
        // 是否启用。
        boolean enabled,
        // 主职任职信息。
        Assignment primaryAssignment,
        // 兼职任职列表。
        List<Assignment> partTimeAssignments
) {

    public record Assignment(
            // 任职主键。
            String userPostId,
            // 公司主键。
            String companyId,
            // 公司名称。
            String companyName,
            // 部门主键。
            String departmentId,
            // 部门名称。
            String departmentName,
            // 岗位主键。
            String postId,
            // 岗位名称。
            String postName,
            // 角色主键列表。
            List<String> roleIds,
            // 角色名称列表。
            List<String> roleNames,
            // 是否主职。
            boolean primary,
            // 任职是否启用。
            boolean enabled
    ) {
    }
}
