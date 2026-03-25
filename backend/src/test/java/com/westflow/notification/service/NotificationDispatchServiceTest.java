package com.westflow.notification.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.response.NotificationChannelMutationResponse;
import com.westflow.notification.request.SaveNotificationChannelRequest;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.system.message.mapper.SystemMessageMapper;
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

    @Autowired
    private SystemMessageMapper systemMessageMapper;

    private final AtomicReference<String> receivedPayload = new AtomicReference<>("");

    @BeforeEach
    void resetStorage() {
        notificationChannelMapper.clear();
        notificationLogMapper.clear();
        systemMessageMapper.clear();
        receivedPayload.set("");
    }

    @Test
    void shouldDispatchWebhookThroughRealProviderSkeletonAndPersistLog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", this::handleWebhook);
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            createChannel("webhook_ops", "WEBHOOK", false, Map.of("url", url, "headers", Map.of("X-Flow", "west")));

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
    void shouldAllowDiagnosticSmsMockOnlyForLocalhostChannel() {
        createChannel(
                "sms_ops",
                "SMS",
                true,
                Map.of(
                        "endpoint", "http://127.0.0.1:65535/sms",
                        "accessToken", "dev-token",
                        "mockResponseMessage", "短信诊断 mock 发送成功"
                )
        );

        var result = notificationDispatchService.dispatchByChannelCode(
                "sms_ops",
                new NotificationDispatchRequest("user_002", "短信提醒", "这是 mock 短信", Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.providerName()).isEqualTo("SMS_DIAGNOSTIC_MOCK");
        assertThat(notificationLogMapper.selectAll()).hasSize(1);
        assertThat(notificationLogMapper.selectAll().get(0).status()).isEqualTo("SUCCESS");
        assertThat(notificationLogMapper.selectAll().get(0).providerName()).isEqualTo("SMS_DIAGNOSTIC_MOCK");
    }

    @Test
    void shouldPersistFailedLogWhenRealSmsProviderReturnsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sms", exchange -> {
            byte[] response = "{\"message\":\"gateway failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        try {
            createChannel(
                    "sms_real",
                    "SMS",
                    false,
                    Map.of(
                            "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/sms",
                            "accessToken", "sms-token"
                    )
            );

            var result = notificationDispatchService.dispatchByChannelCode(
                    "sms_real",
                    new NotificationDispatchRequest("13800138000", "短信提醒", "真实短信失败回写", Map.of("taskId", "task_001"))
            );

            assertThat(result.success()).isFalse();
            assertThat(result.providerName()).isEqualTo("SMS");
            assertThat(result.responseMessage()).contains("短信发送失败");
            assertThat(notificationLogMapper.selectAll()).hasSize(1);
            assertThat(notificationLogMapper.selectAll().get(0).status()).isEqualTo("FAILED");
            assertThat(notificationLogMapper.selectAll().get(0).providerName()).isEqualTo("SMS");
            assertThat(notificationLogMapper.selectAll().get(0).responseMessage()).contains("短信发送失败");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDispatchInAppNotificationAndPersistSystemMessage() {
        createChannel("in_app_ops", "IN_APP", false, Map.of("senderUserId", "usr_system"));

        var result = notificationDispatchService.dispatchByChannelCode(
                "in_app_ops",
                new NotificationDispatchRequest(
                        "usr_001",
                        "站内提醒",
                        "请尽快处理待办任务",
                        Map.of("instanceId", "pi_003", "taskId", "task_003")
                )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.providerName()).isEqualTo("IN_APP");
        assertThat(systemMessageMapper.selectAll()).hasSize(1);
        assertThat(systemMessageMapper.selectAll().get(0).title()).isEqualTo("站内提醒");
        assertThat(systemMessageMapper.selectAll().get(0).content()).isEqualTo("请尽快处理待办任务");
        assertThat(systemMessageMapper.selectAll().get(0).status()).isEqualTo("SENT");
        assertThat(systemMessageMapper.selectAll().get(0).targetType()).isEqualTo("USER");
        assertThat(systemMessageMapper.selectAll().get(0).targetUserIds()).containsExactly("usr_001");
        assertThat(systemMessageMapper.selectAll().get(0).senderUserId()).isEqualTo("usr_system");
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

    private void createChannel(String channelCode, String channelType, boolean mockMode, Map<String, Object> config) {
        Map<String, Object> requestConfig = new java.util.LinkedHashMap<>(config);
        if (mockMode) {
            requestConfig.put("diagnosticMockEnabled", true);
        }
        NotificationChannelMutationResponse response = notificationChannelService.create(
                new SaveNotificationChannelRequest(
                        channelCode,
                        channelType,
                        channelCode + " 渠道",
                        true,
                        requestConfig,
                        "测试渠道"
                )
        );
        assertThat(response.channelId()).isNotBlank();
    }
}
