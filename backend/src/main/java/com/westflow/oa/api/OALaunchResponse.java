package com.westflow.oa.api;

import com.westflow.processruntime.api.DemoTaskView;
import java.util.List;

public record OALaunchResponse(
        String billId,
        String billNo,
        String processInstanceId,
        DemoTaskView firstActiveTask,
        List<DemoTaskView> activeTasks
) {
}
