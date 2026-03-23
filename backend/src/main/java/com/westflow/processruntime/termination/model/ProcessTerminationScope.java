package com.westflow.processruntime.termination.model;

import java.util.Locale;

// 终止作用域。
public enum ProcessTerminationScope {
    ROOT,
    CHILD,
    CURRENT
    ;

    public static ProcessTerminationScope from(String value) {
        if (value == null || value.isBlank()) {
            return CURRENT;
        }
        return ProcessTerminationScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
