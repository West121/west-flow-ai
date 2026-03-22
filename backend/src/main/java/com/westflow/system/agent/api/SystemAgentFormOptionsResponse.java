package com.westflow.system.agent.api;

import java.util.List;

public record SystemAgentFormOptionsResponse(
        List<UserOption> principalUsers,
        List<UserOption> delegateUsers,
        List<StatusOption> statusOptions
) {
    public record UserOption(
            String userId,
            String displayName,
            String username,
            String departmentName,
            String postName,
            boolean enabled
    ) {
    }

    public record StatusOption(
            String value,
            String label
    ) {
    }
}
