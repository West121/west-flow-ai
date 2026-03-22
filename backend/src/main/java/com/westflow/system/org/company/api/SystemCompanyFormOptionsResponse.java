package com.westflow.system.org.company.api;

import java.util.List;

public record SystemCompanyFormOptionsResponse(
        List<CompanyOption> companies
) {

    public record CompanyOption(
            String id,
            String name,
            boolean enabled
    ) {
    }
}
