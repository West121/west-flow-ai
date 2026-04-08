package com.westflow.plm.service;

import com.westflow.plm.api.PlmConnectorDispatchLogResponse;
import com.westflow.plm.api.PlmConnectorHealthSummaryResponse;
import com.westflow.plm.api.PlmConnectorJobResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 连接器任务编排入口。
 */
@Service
@RequiredArgsConstructor
public class PlmConnectorOrchestrationService {

    private final PlmConnectorJobService plmConnectorJobService;
    private final PlmConnectorDispatcher plmConnectorDispatcher;

    public List<PlmConnectorJobResponse> listBillJobs(String businessType, String billId) {
        return plmConnectorJobService.listBillJobs(businessType, billId);
    }

    public PlmConnectorHealthSummaryResponse summarizeBillHealth(String businessType, String billId) {
        return plmConnectorJobService.summarizeBillHealth(businessType, billId);
    }

    public PlmConnectorJobService.JobLocator requireJobLocator(String jobId) {
        return plmConnectorJobService.requireJobLocator(jobId);
    }

    public List<PlmConnectorDispatchLogResponse> listDispatchLogs(String jobId) {
        return plmConnectorJobService.listDispatchLogs(jobId);
    }

    public PlmConnectorJobResponse retryJob(String jobId, String operatorUserId) {
        return plmConnectorJobService.retryJob(jobId, operatorUserId);
    }

    public PlmConnectorJobResponse dispatchJob(String jobId, String operatorUserId) {
        return plmConnectorDispatcher.dispatchJob(jobId, operatorUserId);
    }
}
