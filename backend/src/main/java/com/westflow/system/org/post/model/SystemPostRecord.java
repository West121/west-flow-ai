package com.westflow.system.org.post.model;

/**
 * 岗位表实体。
 */
public record SystemPostRecord(
        String id,
        String departmentId,
        String postName,
        Boolean enabled
) {
}
