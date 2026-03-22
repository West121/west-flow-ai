package com.westflow.processbinding.service;

import com.westflow.common.error.ContractException;
import com.westflow.processbinding.mapper.BusinessProcessBindingMapper;
import com.westflow.processbinding.model.BusinessProcessBindingRecord;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusinessProcessBindingService {

    private final BusinessProcessBindingMapper businessProcessBindingMapper;

    public String resolveProcessKey(String businessType, String sceneCode) {
        // 业务类型和场景码共同决定流程绑定，不在业务层硬编码流程键。
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
