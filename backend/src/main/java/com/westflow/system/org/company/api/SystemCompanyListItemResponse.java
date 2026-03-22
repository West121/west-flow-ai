package com.westflow.system.org.company.api;

import java.time.OffsetDateTime;

public record SystemCompanyListItemResponse(
        String companyId,
        String companyName,
        String status,
        OffsetDateTime createdAt
) {
}
