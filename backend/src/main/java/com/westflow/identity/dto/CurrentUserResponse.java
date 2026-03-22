package com.westflow.identity.dto;

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
        String activePostId,
        String activeDepartmentId,
        List<String> roles,
        List<String> permissions,
        List<DataScope> dataScopes,
        List<PartTimePost> partTimePosts,
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
            String postName
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
