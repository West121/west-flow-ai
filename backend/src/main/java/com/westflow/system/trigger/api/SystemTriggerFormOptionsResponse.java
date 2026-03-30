package com.westflow.system.trigger.api;

import java.util.List;

/**
 * 触发器表单下拉选项响应。
 */
public record SystemTriggerFormOptionsResponse(
        // 触发事件选项。
        List<TriggerEventOption> triggerEvents
) {

    public record TriggerEventOption(
            // 选项值。
            String value,
            // 选项名称。
            String label
    ) {
    }
}
