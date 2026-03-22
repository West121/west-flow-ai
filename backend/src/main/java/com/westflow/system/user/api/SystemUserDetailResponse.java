package com.westflow.system.user.api;

public record SystemUserDetailResponse(
        String userId,
        String displayName,
        String username,
        String mobile,
        String email,
        String companyId,
        String companyName,
        String departmentId,
        String departmentName,
        String postId,
        String postName,
        boolean enabled
) {
}
