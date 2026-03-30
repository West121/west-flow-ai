package com.westflow.notification.service;

import com.westflow.common.error.ContractException;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationDispatchResult;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.notification.model.NotificationSendResult;
import com.westflow.notification.provider.NotificationProvider;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * 根据渠道类型选择 provider 并落通知发送日志。
 */
public class NotificationDispatchService {

    private final NotificationChannelMapper notificationChannelMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final List<NotificationProvider> providers;
    private Map<NotificationChannelType, NotificationProvider> providerMap;

    @PostConstruct
    /**
     * 启动时把所有 provider 按渠道类型注册到内存索引。
     */
    void init() {
        providerMap = new EnumMap<>(NotificationChannelType.class);
        for (NotificationProvider provider : providers) {
            providerMap.put(provider.type(), provider);
        }
    }

    /**
     * 按渠道编码发送通知，并记录结果和日志。
     */
    public NotificationDispatchResult dispatchByChannelCode(String channelCode, NotificationDispatchRequest request) {
        NotificationChannelRecord channel = requireChannel(channelCode);
        NotificationProvider provider = requireProvider(channel.channelType());
        Instant sentAt = Instant.now();
        String logId = buildId("nlg");
        try {
            NotificationSendResult sendResult = provider.send(channel, request);
            NotificationLogRecord logRecord = buildLog(logId, channel, request, sendResult.providerName(), sendResult.success(), sendResult.responseMessage(), sentAt);
            notificationLogMapper.insert(logRecord);
            if (sendResult.success()) {
                notificationChannelMapper.markLastSentAt(channel.channelId(), sentAt);
            }
            return new NotificationDispatchResult(
                    logId,
                    channel.channelId(),
                    channel.channelCode(),
                    channel.channelType(),
                    sendResult.success(),
                    sendResult.providerName(),
                    sendResult.responseMessage(),
                    sentAt
            );
        } catch (Exception exception) {
            NotificationLogRecord logRecord = buildLog(logId, channel, request, provider.type().name(), false, exception.getMessage(), sentAt);
            notificationLogMapper.insert(logRecord);
            return new NotificationDispatchResult(
                    logId,
                    channel.channelId(),
                    channel.channelCode(),
                    channel.channelType(),
                    false,
                    provider.type().name(),
                    exception.getMessage(),
                    sentAt
            );
        }
    }

    /**
     * 按渠道编码读取通知渠道。
     */
    private NotificationChannelRecord requireChannel(String channelCode) {
        NotificationChannelRecord channel = notificationChannelMapper.selectByCode(channelCode);
        if (channel == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "通知渠道不存在",
                    Map.of("channelCode", channelCode)
            );
        }
        return channel;
    }

    /**
     * 按渠道类型解析对应 provider。
     */
    private NotificationProvider requireProvider(String channelType) {
        NotificationChannelType type;
        try {
            type = NotificationChannelType.fromCode(channelType);
        } catch (IllegalArgumentException exception) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道类型不合法",
                    Map.of("channelType", channelType)
            );
        }
        NotificationProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "当前渠道类型未绑定 provider",
                    Map.of("channelType", channelType)
            );
        }
        return provider;
    }

    /**
     * 组装通知发送日志。
     */
    private NotificationLogRecord buildLog(
            String logId,
            NotificationChannelRecord channel,
            NotificationDispatchRequest request,
            String providerName,
            boolean success,
            String responseMessage,
            Instant sentAt
    ) {
        return new NotificationLogRecord(
                logId,
                channel.channelId(),
                channel.channelCode(),
                channel.channelType(),
                request.recipient(),
                request.title(),
                request.content(),
                providerName,
                success,
                success ? "SUCCESS" : "FAILED",
                responseMessage,
                request.payload(),
                sentAt
        );
    }

    /**
     * 生成通知日志主键。
     */
    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
