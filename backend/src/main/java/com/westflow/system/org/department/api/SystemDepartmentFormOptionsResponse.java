package com.westflow.system.org.department.api;

import java.util.List;

public record SystemDepartmentFormOptionsResponse(
        List<CompanyOption> companies,
        List<ParentDepartmentOption> parentDepartments
) {

    public record CompanyOption(
            String id,
            String name,
            boolean enabled
    ) {
    }

    public record ParentDepartmentOption(
            String id,
            String name,
            String companyId,
            String companyName,
            boolean enabled
    ) {
    }
}
