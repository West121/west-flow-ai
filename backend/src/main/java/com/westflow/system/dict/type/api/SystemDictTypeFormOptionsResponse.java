package com.westflow.system.dict.type.api;

import java.util.List;

/**
 * 字典类型表单选项。
 */
public record SystemDictTypeFormOptionsResponse(
        // 状态选项。
        List<StatusOption> statusOptions
) {

    public record StatusOption(
            // 选项值。
            String code,
            // 选项名称。
            String name
    ) {
    }
}
