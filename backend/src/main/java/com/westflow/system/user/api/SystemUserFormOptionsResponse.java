package com.westflow.system.user.api;

import java.util.List;

public record SystemUserFormOptionsResponse(
        List<CompanyOption> companies,
        List<PostOption> posts
) {

    public record CompanyOption(
            String id,
            String name
    ) {
    }

    public record PostOption(
            String id,
            String name,
            String departmentId,
            String departmentName
    ) {
    }
}
