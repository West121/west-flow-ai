package com.westflow.system.org.post.api;

/**
 * 岗位详情响应。
 */
public record SystemPostDetailResponse(
        String postId,
        String companyId,
        String companyName,
        String departmentId,
        String departmentName,
        String postName,
        boolean enabled
) {
}
