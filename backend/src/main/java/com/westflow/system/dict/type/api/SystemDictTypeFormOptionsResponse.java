package com.westflow.system.dict.type.api;

import java.util.List;

/**
 * 字典类型表单选项。
 */
public record SystemDictTypeFormOptionsResponse(
        List<StatusOption> statusOptions
) {

    public record StatusOption(
            String code,
            String name
    ) {
    }
}
