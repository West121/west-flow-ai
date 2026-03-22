package com.westflow.identity.dto;

import java.util.List;

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
    public record DataScope(
            String scopeType,
            String scopeValue
    ) {
    }

    public record PartTimePost(
            String postId,
            String departmentId,
            String postName
    ) {
    }

    public record Delegation(
            String principalUserId,
            String delegateUserId,
            String status
    ) {
    }

    public record MenuItem(
            String id,
            String title,
            String path
    ) {
    }
}
