package com.westflow.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// Webhook 通知的发送适配器。
public class WebhookNotificationProvider implements NotificationProvider {

    private final ObjectMapper objectMapper;

    @Override
    // 返回 Webhook 渠道类型。
    public NotificationChannelType type() {
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    // 通过 JDK HttpClient 向配置的 URL 发送 JSON 回调。
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // Webhook 直接使用 JDK HttpClient，方便后续接真实外部回调。
        Map<String, Object> config = channel.config();
        String url = requireString(config, "url");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channelCode", channel.channelCode());
        body.put("channelType", channel.channelType());
        body.put("recipient", request.recipient());
        body.put("title", request.title());
        body.put("content", request.content());
        body.put("payload", request.payload());

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));

            Object headers = config.get("headers");
            if (headers instanceof Map<?, ?> headerMap) {
                for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                    builder.header(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }

            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new NotificationSendResult(true, "WEBHOOK", "Webhook 发送成功");
            }
            throw new ContractException(
                    "NOTIFICATION.WEBHOOK_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "Webhook 返回非成功状态",
                    Map.of("statusCode", response.statusCode(), "url", url)
            );
        } catch (Exception exception) {
            if (exception instanceof ContractException contractException) {
                throw contractException;
            }
            throw new ContractException(
                    "NOTIFICATION.WEBHOOK_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "Webhook 发送失败",
                    Map.of("url", url, "error", exception.getMessage())
            );
        }
    }

    // 读取并校验 Webhook 配置中的必填字符串。
    private String requireString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "Webhook 渠道配置缺少必要参数",
                    Map.of("field", key)
            );
        }
        return String.valueOf(value);
    }
}
