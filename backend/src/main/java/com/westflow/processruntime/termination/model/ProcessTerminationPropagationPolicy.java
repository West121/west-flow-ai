package com.westflow.processruntime.termination.model;

import java.util.Locale;

// 终止传播策略。
public enum ProcessTerminationPropagationPolicy {
    SELF_ONLY,
    CASCADE_CHILDREN,
    CASCADE_APPENDS,
    CASCADE_DESCENDANTS,
    CASCADE_ALL
    ;

    public static ProcessTerminationPropagationPolicy from(String value) {
        if (value == null || value.isBlank()) {
            return SELF_ONLY;
        }
        return ProcessTerminationPropagationPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean includesChildProcesses() {
        return this == CASCADE_CHILDREN || this == CASCADE_DESCENDANTS || this == CASCADE_ALL;
    }

    public boolean includesAppends() {
        return this == CASCADE_APPENDS || this == CASCADE_DESCENDANTS || this == CASCADE_ALL;
    }
}
