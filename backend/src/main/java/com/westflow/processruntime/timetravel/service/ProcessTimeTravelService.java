package com.westflow.processruntime.timetravel.service;

import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.api.ExecuteProcessTimeTravelRequest;
import com.westflow.processruntime.api.ProcessTimeTravelExecutionResponse;
import com.westflow.processruntime.api.ProcessTimeTravelQueryRequest;
import java.util.List;

/**
 * 穿越时空执行服务接口。
 */
public interface ProcessTimeTravelService {

    ProcessTimeTravelExecutionResponse execute(ExecuteProcessTimeTravelRequest request);

    PageResponse<ProcessTimeTravelExecutionResponse> page(ProcessTimeTravelQueryRequest request);

    List<ProcessTimeTravelExecutionResponse> trace(String instanceId);
}
