package com.westflow.system.dict.item.api;

import java.util.List;

/**
 * 字典项表单选项。
 */
public record SystemDictItemFormOptionsResponse(
        List<DictTypeOption> dictTypes,
        List<StatusOption> statusOptions
) {

    public record DictTypeOption(
            String dictTypeId,
            String typeCode,
            String typeName
    ) {
    }

    public record StatusOption(
            String code,
            String name
    ) {
    }
}
