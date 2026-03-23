package com.westflow.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableNotificationProvidersTest {

    @Test
    void shouldBuildSmsRequestWithConfiguredEndpointAndCredential() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>("");
        AtomicReference<String> authRef = new AtomicReference<>("");
        HttpServer server = startServer("/sms", exchange -> handle(exchange, bodyRef, authRef));
        try {
            SmsMockNotificationProvider provider = new SmsMockNotificationProvider(new ObjectMapper());
            NotificationSendResult result = provider.send(
                    channel(
                            "sms_ops",
                            "SMS",
                            false,
                            Map.of(
                                    "endpoint", endpoint(server, "/sms"),
                                    "accessToken", "sms-token",
                                    "templateCode", "SMS_001",
                                    "signName", "WestFlow"
                            )
                    ),
                    new NotificationDispatchRequest(
                            "13800138000",
                            "流程提醒",
                            "请尽快审批",
                            Map.of("instanceId", "pi_001")
                    )
            );

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("SMS");
            assertThat(authRef.get()).isEqualTo("Bearer sms-token");
            assertThat(bodyRef.get()).contains("\"phoneNumbers\":\"13800138000\"");
            assertThat(bodyRef.get()).contains("\"templateCode\":\"SMS_001\"");
            assertThat(bodyRef.get()).contains("\"signName\":\"WestFlow\"");
            assertThat(bodyRef.get()).contains("\"instanceId\":\"pi_001\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldBuildWechatRequestWithConfiguredEndpointAndCredential() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>("");
        AtomicReference<String> authRef = new AtomicReference<>("");
        HttpServer server = startServer("/wechat", exchange -> handle(exchange, bodyRef, authRef));
        try {
            WechatMockNotificationProvider provider = new WechatMockNotificationProvider(new ObjectMapper());
            NotificationSendResult result = provider.send(
                    channel(
                            "wechat_ops",
                            "WECHAT",
                            false,
                            Map.of(
                                    "endpoint", endpoint(server, "/wechat"),
                                    "accessToken", "wechat-token",
                                    "agentId", "1000002",
                                    "corpId", "corp-001"
                            )
                    ),
                    new NotificationDispatchRequest(
                            "zhangsan",
                            "待办提醒",
                            "请查看当前流程待办",
                            Map.of("taskId", "task_001")
                    )
            );

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("WECHAT");
            assertThat(authRef.get()).isEqualTo("Bearer wechat-token");
            assertThat(bodyRef.get()).contains("\"touser\":\"zhangsan\"");
            assertThat(bodyRef.get()).contains("\"agentid\":\"1000002\"");
            assertThat(bodyRef.get()).contains("\"corpId\":\"corp-001\"");
            assertThat(bodyRef.get()).contains("请查看当前流程待办");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldBuildDingTalkRequestWithConfiguredEndpointAndCredential() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>("");
        AtomicReference<String> authRef = new AtomicReference<>("");
        HttpServer server = startServer("/dingtalk", exchange -> handle(exchange, bodyRef, authRef));
        try {
            DingTalkMockNotificationProvider provider = new DingTalkMockNotificationProvider(new ObjectMapper());
            NotificationSendResult result = provider.send(
                    channel(
                            "ding_ops",
                            "DINGTALK",
                            false,
                            Map.of(
                                    "endpoint", endpoint(server, "/dingtalk"),
                                    "accessToken", "ding-token",
                                    "agentId", "ding-agent",
                                    "appKey", "app-key-001"
                            )
                    ),
                    new NotificationDispatchRequest(
                            "lisi",
                            "钉钉提醒",
                            "请更新处理意见",
                            Map.of("taskId", "task_002")
                    )
            );

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("DINGTALK");
            assertThat(authRef.get()).isEqualTo("Bearer ding-token");
            assertThat(bodyRef.get()).contains("\"userid_list\":\"lisi\"");
            assertThat(bodyRef.get()).contains("\"agent_id\":\"ding-agent\"");
            assertThat(bodyRef.get()).contains("\"appKey\":\"app-key-001\"");
            assertThat(bodyRef.get()).contains("请更新处理意见");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            handler.handle(exchange);
            byte[] response = "{\"message\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        return server;
    }

    private void handle(
            HttpExchange exchange,
            AtomicReference<String> bodyRef,
            AtomicReference<String> authRef
    ) throws IOException {
        bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private String endpoint(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private NotificationChannelRecord channel(
            String channelCode,
            String channelType,
            boolean mockMode,
            Map<String, Object> config
    ) {
        return new NotificationChannelRecord(
                "nch_" + channelCode,
                channelCode,
                channelType,
                channelCode + " 渠道",
                true,
                mockMode,
                config,
                "测试渠道",
                Instant.now(),
                Instant.now(),
                null
        );
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
