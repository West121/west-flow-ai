package com.westflow.system.trigger.api;

import java.util.List;

public record SystemTriggerFormOptionsResponse(
        List<TriggerEventOption> triggerEvents
) {

    public record TriggerEventOption(
            String value,
            String label
    ) {
    }
}
