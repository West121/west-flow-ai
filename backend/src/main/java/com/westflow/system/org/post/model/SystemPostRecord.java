package com.westflow.system.org.post.model;

/**
 * 岗位表实体。
 */
public record SystemPostRecord(
        // 岗位主键。
        String id,
        // 所属部门主键。
        String departmentId,
        // 岗位名称。
        String postName,
        // 是否启用。
        Boolean enabled
) {
}
