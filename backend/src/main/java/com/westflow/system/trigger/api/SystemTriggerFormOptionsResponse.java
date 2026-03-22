package com.westflow.system.trigger.api;

import java.util.List;

/**
 * 触发器表单下拉选项响应。
 */
public record SystemTriggerFormOptionsResponse(
        List<TriggerEventOption> triggerEvents
) {

    public record TriggerEventOption(
            String value,
            String label
    ) {
    }
}
