package com.westflow.system.user.api;

import java.time.OffsetDateTime;

public record SystemUserListItemResponse(
        String userId,
        String displayName,
        String username,
        String mobile,
        String email,
        String departmentName,
        String postName,
        String status,
        OffsetDateTime createdAt
) {
}
