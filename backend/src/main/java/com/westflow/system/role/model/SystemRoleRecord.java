package com.westflow.system.role.model;

/**
 * 角色表实体。
 */
public record SystemRoleRecord(
        String id,
        String roleCode,
        String roleName,
        String roleCategory,
        String description,
        Boolean enabled
) {
}
