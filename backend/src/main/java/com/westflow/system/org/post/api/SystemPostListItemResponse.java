package com.westflow.system.org.post.api;

import java.time.OffsetDateTime;

public record SystemPostListItemResponse(
        String postId,
        String companyName,
        String departmentName,
        String postName,
        String status,
        OffsetDateTime createdAt
) {
}
