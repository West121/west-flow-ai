package com.westflow.plm.api;

/**
 * PLM 实施任务依赖响应。
 */
public record PlmImplementationDependencyResponse(
        String id,
        String businessType,
        String billId,
        String predecessorTaskId,
        String predecessorTaskNo,
        String predecessorTaskTitle,
        String successorTaskId,
        String successorTaskNo,
        String successorTaskTitle,
        String dependencyType,
        Boolean requiredFlag
) {
}
