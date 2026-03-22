package com.westflow.notification.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.api.NotificationChannelMutationResponse;
import com.westflow.notification.api.SaveNotificationChannelRequest;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationDispatchRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatchServiceTest {

    @Autowired
    private NotificationChannelService notificationChannelService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    private final AtomicReference<String> receivedPayload = new AtomicReference<>("");

    @BeforeEach
    void resetStorage() {
        notificationChannelMapper.clear();
        notificationLogMapper.clear();
        receivedPayload.set("");
    }

    @Test
    void shouldDispatchWebhookThroughRealProviderSkeletonAndPersistLog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", this::handleWebhook);
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            createChannel("webhook_ops", "WEBHOOK", Map.of("url", url, "headers", Map.of("X-Flow", "west")));

            var result = notificationDispatchService.dispatchByChannelCode(
                    "webhook_ops",
                    new NotificationDispatchRequest("user_001", "流程提醒", "请尽快处理", Map.of("instanceId", "pi_001"))
            );

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("WEBHOOK");
            assertThat(notificationLogMapper.selectAll()).hasSize(1);
            assertThat(notificationLogMapper.selectAll().get(0).status()).isEqualTo("SUCCESS");
            assertThat(notificationLogMapper.selectAll().get(0).channelCode()).isEqualTo("webhook_ops");
            assertThat(receivedPayload.get()).contains("流程提醒");
            assertThat(receivedPayload.get()).contains("请尽快处理");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDispatchSmsThroughMockProviderAndPersistLog() {
        createChannel("sms_ops", "SMS", Map.of("gateway", "mock-gateway"));

        var result = notificationDispatchService.dispatchByChannelCode(
                "sms_ops",
                new NotificationDispatchRequest("user_002", "短信提醒", "这是 mock 短信", Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.providerName()).isEqualTo("SMS_MOCK");
        assertThat(notificationLogMapper.selectAll()).hasSize(1);
        assertThat(notificationLogMapper.selectAll().get(0).status()).isEqualTo("SUCCESS");
        assertThat(notificationLogMapper.selectAll().get(0).providerName()).isEqualTo("SMS_MOCK");
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String payload = new String(body, StandardCharsets.UTF_8);
        receivedPayload.set(payload);
        byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private void createChannel(String channelCode, String channelType, Map<String, Object> config) {
        NotificationChannelMutationResponse response = notificationChannelService.create(
                new SaveNotificationChannelRequest(
                        channelCode,
                        channelType,
                        channelCode + " 渠道",
                        true,
                        false,
                        config,
                        "测试渠道"
                )
        );
        assertThat(response.channelId()).isNotBlank();
    }
}
