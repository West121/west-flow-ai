package com.westflow.system.org.post.api;

import java.time.OffsetDateTime;

/**
 * 岗位列表项响应。
 */
public record SystemPostListItemResponse(
        String postId,
        String companyName,
        String departmentName,
        String postName,
        String status,
        OffsetDateTime createdAt
) {
}
