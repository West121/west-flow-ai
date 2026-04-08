package com.westflow.orchestrator.model;

/**
 * 编排器支持的自动化类型。
 */
public enum OrchestratorAutomationType {
    TIMEOUT_APPROVAL,
    AUTO_REMINDER,
    ESCALATION,
    TIMER_NODE,
    TRIGGER_NODE,
    PREDICTION_AUTO_URGE,
    PREDICTION_SLA_REMINDER,
    PREDICTION_NEXT_NODE_PRE_NOTIFY,
    PREDICTION_COLLABORATION_ACTION
}
