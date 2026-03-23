package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 客户端工厂测试。
 */
class AiMcpClientFactoryTest {

    @Test
    void shouldSkipInternalAndInvalidMcpRows() {
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
        when(aiRegistryCatalogService.listMcps("usr_001", "OA")).thenReturn(List.of(
                new AiRegistryCatalogService.AiMcpCatalogItem(
                        "westflow-internal-mcp",
                        "平台内置 MCP 桥",
                        null,
                        "INTERNAL",
                        "ai:copilot:open",
                        List.of("OA"),
                        100,
                        Map.of()
                ),
                new AiRegistryCatalogService.AiMcpCatalogItem(
                        "broken-http",
                        "无效 HTTP MCP",
                        "",
                        "STREAMABLE_HTTP",
                        "ai:copilot:open",
                        List.of("OA"),
                        80,
                        Map.of()
                ),
                new AiRegistryCatalogService.AiMcpCatalogItem(
                        "broken-stdio",
                        "无效 STDIO MCP",
                        null,
                        "STDIO",
                        "ai:copilot:open",
                        List.of("OA"),
                        70,
                        Map.of()
                )
        ));

        AiMcpClientFactory factory = new AiMcpClientFactory(aiRegistryCatalogService, new ObjectMapper());

        List<McpSyncClient> clients = factory.createClients("usr_001", "OA");

        assertThat(clients).isEmpty();
    }

    @Test
    void shouldCacheClientForSameMcpCode() {
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
        AiRegistryCatalogService.AiMcpCatalogItem item = new AiRegistryCatalogService.AiMcpCatalogItem(
                "external-mcp",
                "外部 MCP",
                "http://localhost:19090/mcp",
                "STREAMABLE_HTTP",
                "ai:copilot:open",
                List.of("OA"),
                90,
                Map.of()
        );
        when(aiRegistryCatalogService.listMcps("usr_001", "OA")).thenReturn(List.of(item));

        McpSyncClient client = mock(McpSyncClient.class);
        AiMcpClientFactory factory = new AiMcpClientFactory(aiRegistryCatalogService, new ObjectMapper()) {
            @Override
            Optional<McpSyncClient> createClient(AiRegistryCatalogService.AiMcpCatalogItem candidate) {
                return Optional.of(client);
            }
        };

        List<McpSyncClient> first = factory.createClients("usr_001", "OA");
        List<McpSyncClient> second = factory.createClients("usr_001", "OA");

        assertThat(first).containsExactly(client);
        assertThat(second).containsExactly(client);
    }
}
