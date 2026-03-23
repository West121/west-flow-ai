package com.westflow.processruntime.termination.model;

// 终止监控树中的节点类型。
public enum ProcessTerminationNodeKind {
    ROOT,
    SUBPROCESS,
    APPEND_TASK,
    APPEND_SUBPROCESS
}
