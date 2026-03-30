package com.westflow.processbinding.service;

import com.westflow.common.error.ContractException;
import com.westflow.processbinding.mapper.BusinessProcessBindingMapper;
import com.westflow.processbinding.model.BusinessProcessBindingRecord;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 根据业务类型和场景码解析对应的流程键。
 */
@Service
@RequiredArgsConstructor
public class BusinessProcessBindingService {

    private final BusinessProcessBindingMapper businessProcessBindingMapper;

    /**
     * 解析业务场景对应的流程键。
     *
     * @param businessType 业务类型
     * @param sceneCode 场景码
     * @return 流程键
     */
    public String resolveProcessKey(String businessType, String sceneCode) {
        String normalizedSceneCode = sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
        BusinessProcessBindingRecord record = businessProcessBindingMapper.selectEnabledBinding(businessType, normalizedSceneCode);
        if (record == null || record.processKey() == null || record.processKey().isBlank()) {
            throw new ContractException(
                    "BIZ.BUSINESS_PROCESS_BINDING_NOT_FOUND",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前业务场景未配置可用流程绑定",
                    Map.of("businessType", businessType, "sceneCode", normalizedSceneCode)
            );
        }
        return record.processKey();
    }
}
