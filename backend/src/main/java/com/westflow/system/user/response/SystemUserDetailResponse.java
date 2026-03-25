package com.westflow.system.user.response;

import java.util.List;

/**
 * 用户详情响应。
 */
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
        List<String> roleIds,
        boolean enabled
) {
}
