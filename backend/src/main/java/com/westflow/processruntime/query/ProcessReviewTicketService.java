package com.westflow.processruntime.query;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.processruntime.api.response.ProcessReviewTicketResponse;
import com.westflow.processruntime.api.response.ProcessTaskDetailResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 为微信小程序 web-view 提供短期流程回顾票据。
 */
@Service
public class ProcessReviewTicketService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final FlowableProcessRuntimeService flowableProcessRuntimeService;
    private final Clock clock;
    private final Map<String, TicketRecord> tickets = new ConcurrentHashMap<>();

    @Autowired
    public ProcessReviewTicketService(FlowableProcessRuntimeService flowableProcessRuntimeService) {
        this(flowableProcessRuntimeService, Clock.systemDefaultZone());
    }

    private ProcessReviewTicketService(FlowableProcessRuntimeService flowableProcessRuntimeService, Clock clock) {
        this.flowableProcessRuntimeService = flowableProcessRuntimeService;
        this.clock = clock;
    }

    /**
     * 创建当前登录人可用的只读流程回顾票据。
     */
    public ProcessReviewTicketResponse create(String taskId) {
        String loginUserId = StpUtil.getLoginIdAsString();
        ProcessTaskDetailResponse detail = flowableProcessRuntimeService.detail(taskId);
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(DEFAULT_TTL);
        String ticket = "rtk_" + UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new TicketRecord(loginUserId, detail.taskId(), expiresAt));
        pruneExpired(now);
        return new ProcessReviewTicketResponse(ticket, expiresAt);
    }

    /**
     * 通过票据解析只读流程回顾详情。
     */
    public ProcessTaskDetailResponse resolve(String ticket) {
        Instant now = Instant.now(clock);
        TicketRecord record = tickets.get(ticket);
        if (record == null || record.expiresAt().isBefore(now)) {
            tickets.remove(ticket);
            throw new ContractException(
                "PROCESS.RUNTIME_REVIEW_TICKET_INVALID",
                HttpStatus.UNAUTHORIZED,
                "流程回顾票据无效或已过期。"
            );
        }

        return flowableProcessRuntimeService.detail(record.taskId(), record.ownerUserId());
    }

    private void pruneExpired(Instant now) {
        tickets.entrySet().removeIf((entry) -> entry.getValue().expiresAt().isBefore(now));
    }

    private record TicketRecord(
        String ownerUserId,
        String taskId,
        Instant expiresAt
    ) {
    }
}
