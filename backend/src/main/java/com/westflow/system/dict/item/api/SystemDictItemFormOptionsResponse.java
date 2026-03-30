package com.westflow.system.dict.item.api;

import java.util.List;

/**
 * 字典项表单选项。
 */
public record SystemDictItemFormOptionsResponse(
        // 字典类型选项。
        List<DictTypeOption> dictTypes,
        // 状态选项。
        List<StatusOption> statusOptions
) {

    public record DictTypeOption(
            // 字典类型标识。
            String dictTypeId,
            // 类型编码。
            String typeCode,
            // 类型名称。
            String typeName
    ) {
    }

    public record StatusOption(
            // 选项值。
            String code,
            // 选项名称。
            String name
    ) {
    }
}
