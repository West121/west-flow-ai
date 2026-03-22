package com.westflow.system.org.post.api;

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
