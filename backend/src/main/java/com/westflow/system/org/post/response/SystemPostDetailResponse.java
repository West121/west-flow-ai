package com.westflow.system.org.post.response;

/**
 * 岗位详情响应。
 */
public record SystemPostDetailResponse(
        // 岗位主键。
        String postId,
        // 公司主键。
        String companyId,
        // 公司名称。
        String companyName,
        // 部门主键。
        String departmentId,
        // 部门名称。
        String departmentName,
        // 岗位名称。
        String postName,
        // 是否启用。
        boolean enabled
) {
}
