package com.westflow.oa.api;

import com.westflow.processruntime.api.RuntimeTaskView;
import java.util.List;

/**
 * OA 单据发起后的响应载体。
 */
public record OALaunchResponse(
        String billId,
        String billNo,
        String processInstanceId,
        RuntimeTaskView firstActiveTask,
        List<RuntimeTaskView> activeTasks
) {
}
