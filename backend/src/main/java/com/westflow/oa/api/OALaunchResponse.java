package com.westflow.oa.api;

import com.westflow.processruntime.api.ProcessTaskSnapshot;
import java.util.List;

/**
 * OA 单据发起后的响应载体。
 */
public record OALaunchResponse(
        String billId,
        String billNo,
        String processInstanceId,
        ProcessTaskSnapshot firstActiveTask,
        List<ProcessTaskSnapshot> activeTasks
) {
}
