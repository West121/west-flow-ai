package com.westflow.system.message.api;

import java.util.List;

/**
 * 站内消息表单选项。
 */
public record SystemMessageFormOptionsResponse(
        // 消息状态选项。
        List<StatusOption> statusOptions,
        // 目标类型选项。
        List<TargetTypeOption> targetTypeOptions,
        // 已读状态选项。
        List<ReadStatusOption> readStatusOptions
) {

    public record StatusOption(
            // 状态编码。
            String code,
            // 状态名称。
            String name
    ) {
    }

    public record TargetTypeOption(
            // 目标类型编码。
            String code,
            // 目标类型名称。
            String name
    ) {
    }

    public record ReadStatusOption(
            // 已读状态编码。
            String code,
            // 已读状态名称。
            String name
    ) {
    }
}
