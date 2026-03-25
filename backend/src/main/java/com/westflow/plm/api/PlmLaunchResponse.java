package com.westflow.plm.api;

import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import java.util.List;

/**
 * PLM 单据发起后的响应。
 */
public record PlmLaunchResponse(
        String billId,
        String billNo,
        String processInstanceId,
        ProcessTaskSnapshot firstActiveTask,
        List<ProcessTaskSnapshot> activeTasks
) {
}
