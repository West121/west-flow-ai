package com.westflow.plm.service;

import com.westflow.plm.api.PlmConnectorExternalAckRequest;
import com.westflow.plm.api.PlmConnectorExternalAckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 连接器回执服务。
 */
@Service
@RequiredArgsConstructor
public class PlmConnectorAckService {

    private final PlmConnectorJobService plmConnectorJobService;

    public PlmConnectorExternalAckResponse writeAck(String jobId, PlmConnectorExternalAckRequest request) {
        return plmConnectorJobService.writeAck(jobId, request);
    }
}
