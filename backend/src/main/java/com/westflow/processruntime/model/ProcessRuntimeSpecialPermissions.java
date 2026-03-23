package com.westflow.processruntime.model;

/**
 * 运行态特殊能力权限码。
 */
public final class ProcessRuntimeSpecialPermissions {

    public static final String COLLABORATION_CREATE = "workflow:collaboration:create";
    public static final String COLLABORATION_VIEW = "workflow:collaboration:view";
    public static final String TIME_TRAVEL_EXECUTE = "workflow:time-travel:execute";
    public static final String TIME_TRAVEL_VIEW = "workflow:time-travel:view";
    public static final String TIME_TRAVEL_TRACE = "workflow:time-travel:trace";

    private ProcessRuntimeSpecialPermissions() {
    }
}
