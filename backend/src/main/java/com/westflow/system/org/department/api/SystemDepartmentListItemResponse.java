package com.westflow.system.org.department.api;

import java.time.OffsetDateTime;

public record SystemDepartmentListItemResponse(
        String departmentId,
        String companyName,
        String parentDepartmentName,
        String departmentName,
        String status,
        OffsetDateTime createdAt
) {
}
