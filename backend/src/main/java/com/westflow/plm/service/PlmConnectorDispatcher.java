package com.westflow.plm.service;

import com.westflow.plm.api.PlmConnectorJobResponse;
import com.westflow.common.error.ContractException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 连接器任务派发执行器。
 */
@Service
public class PlmConnectorDispatcher {

    private final PlmConnectorJobService plmConnectorJobService;
    private final Map<String, PlmConnectorHandler> handlers;

    public PlmConnectorDispatcher(
            PlmConnectorJobService plmConnectorJobService,
            List<PlmConnectorHandler> handlers
    ) {
        this.plmConnectorJobService = plmConnectorJobService;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(PlmConnectorHandler::handlerKey, Function.identity(), (left, right) -> left));
    }

    @Transactional
    public PlmConnectorJobResponse dispatchJob(String jobId, String operatorUserId) {
        PlmConnectorHandler.DispatchCommand command = plmConnectorJobService.prepareDispatch(jobId);
        PlmConnectorHandler handler = handlers.get(command.handlerKey());
        if (handler == null) {
            throw new ContractException(
                    "PLM.CONNECTOR_HANDLER_NOT_FOUND",
                    HttpStatus.NOT_IMPLEMENTED,
                    "连接器处理器未注册",
                    Map.of(
                            "jobId", jobId,
                            "handlerKey", command.handlerKey()
                    )
            );
        }
        PlmConnectorHandler.DispatchResult result = handler.dispatch(command, operatorUserId);
        return plmConnectorJobService.completeDispatch(command, result);
    }
}
