package com.westflow.identity.response;

import java.util.List;

/**
 * 当前登录用户信息。
 */
public record CurrentUserResponse(
        String userId,
        String username,
        String displayName,
        String mobile,
        String email,
        String avatar,
        String companyId,
        String companyName,
        String activePostId,
        String activePostName,
        String activeDepartmentId,
        String activeDepartmentName,
        List<String> roles,
        List<String> permissions,
        List<DataScope> dataScopes,
        List<PartTimePost> partTimePosts,
        List<PostAssignment> postAssignments,
        List<Delegation> delegations,
        List<String> aiCapabilities,
        List<MenuItem> menus
) {
    /**
     * 数据权限范围项。
     */
    public record DataScope(
            String scopeType,
            String scopeValue
    ) {
    }

    /**
     * 兼职岗位信息。
     */
    public record PartTimePost(
            String postId,
            String departmentId,
            String departmentName,
            String companyId,
            String companyName,
            String postName,
            List<String> roleIds,
            List<String> roleNames,
            boolean enabled
    ) {
    }

    /**
     * 当前用户全部任职信息。
     */
    public record PostAssignment(
            String postId,
            String departmentId,
            String departmentName,
            String companyId,
            String companyName,
            String postName,
            List<String> roleIds,
            List<String> roleNames,
            boolean primary,
            boolean enabled
    ) {
    }

    /**
     * 代理关系信息。
     */
    public record Delegation(
            String principalUserId,
            String delegateUserId,
            String status
    ) {
    }

    /**
     * 菜单导航项。
     */
    public record MenuItem(
            String id,
            String title,
            String path
    ) {
    }
}
