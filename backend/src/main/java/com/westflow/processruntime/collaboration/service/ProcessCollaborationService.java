package com.westflow.processruntime.collaboration.service;

import com.westflow.processruntime.collaboration.api.CreateProcessCollaborationEventRequest;
import com.westflow.processruntime.collaboration.api.ProcessCollaborationEventResponse;
import com.westflow.processruntime.collaboration.api.ProcessCollaborationQueryRequest;
import com.westflow.common.query.PageResponse;
import java.util.List;

/**
 * 协同事件服务接口。
 */
public interface ProcessCollaborationService {

    ProcessCollaborationEventResponse createEvent(CreateProcessCollaborationEventRequest request);

    PageResponse<ProcessCollaborationEventResponse> page(ProcessCollaborationQueryRequest request);

    List<ProcessCollaborationEventResponse> trace(String instanceId);
}
