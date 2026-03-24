package com.westflow.processruntime.service.append;

import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.RuntimeAppendLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单独记录 dynamic-builder 的跳过/失败结果，避免主流程异常回滚时丢失审计痕迹。
 */
@Service
@RequiredArgsConstructor
public class DynamicBuildOutcomeRecorder {

    private final RuntimeAppendLinkService runtimeAppendLinkService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RuntimeAppendLinkRecord record) {
        runtimeAppendLinkService.createLink(record);
    }
}
