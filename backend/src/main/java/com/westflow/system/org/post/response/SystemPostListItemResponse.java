package com.westflow.system.org.post.response;

import java.time.OffsetDateTime;

/**
 * 岗位列表项响应。
 */
public record SystemPostListItemResponse(
        // 岗位主键。
        String postId,
        // 公司名称。
        String companyName,
        // 部门名称。
        String departmentName,
        // 岗位名称。
        String postName,
        // 启用状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
