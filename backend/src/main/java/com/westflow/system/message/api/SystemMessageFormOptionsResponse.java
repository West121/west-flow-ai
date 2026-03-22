package com.westflow.system.message.api;

import java.util.List;

/**
 * 站内消息表单选项。
 */
public record SystemMessageFormOptionsResponse(
        List<StatusOption> statusOptions,
        List<TargetTypeOption> targetTypeOptions,
        List<ReadStatusOption> readStatusOptions
) {

    public record StatusOption(
            String code,
            String name
    ) {
    }

    public record TargetTypeOption(
            String code,
            String name
    ) {
    }

    public record ReadStatusOption(
            String code,
            String name
    ) {
    }
}
