package com.westflow.system.org.company.api;

public record SystemCompanyDetailResponse(
        String companyId,
        String companyName,
        boolean enabled
) {
}
