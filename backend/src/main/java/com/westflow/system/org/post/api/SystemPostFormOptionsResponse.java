package com.westflow.system.org.post.api;

import java.util.List;

public record SystemPostFormOptionsResponse(
        List<DepartmentOption> departments
) {

    public record DepartmentOption(
            String id,
            String name,
            String companyId,
            String companyName,
            boolean enabled
    ) {
    }
}
