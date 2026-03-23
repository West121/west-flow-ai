package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 运行时 ToolCallback 提供器测试。
 */
class AiRuntimeToolCallbackProviderTest {

    @Test
    void shouldExposeReadableToolsThroughInternalMcpProvider() {
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
        AiMcpClientFactory aiMcpClientFactory = mock(AiMcpClientFactory.class);
        AiToolRegistry aiToolRegistry = new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "task.query",
                        AiToolSource.PLATFORM,
                        "查询待办",
                        context -> Map.of("count", 2, "keyword", context.request().arguments().get("keyword"))
                )
        ));
        when(aiRegistryCatalogService.findMcp("usr_001", "westflow-internal-mcp"))
                .thenReturn(java.util.Optional.of(new AiRegistryCatalogService.AiMcpCatalogItem(
                        "westflow-internal-mcp",
                        "平台内置 MCP 桥",
                        null,
                        "INTERNAL",
                        "ai:copilot:open",
                        List.of("OA"),
                        100,
                        Map.of()
                )));
        when(aiRegistryCatalogService.listReadableTools("usr_001", "OA"))
                .thenReturn(List.of(new AiRegistryCatalogService.AiToolCatalogItem(
                        "task.query",
                        "查询待办",
                        AiToolSource.PLATFORM,
                        AiToolType.READ,
                        "ai:copilot:open",
                        List.of("OA"),
                        List.of("待办", "轨迹"),
                        List.of("/workbench/"),
                        "westflow-internal-mcp",
                        "",
                        95,
                        Map.of()
                )));
        when(aiMcpClientFactory.createClients("usr_001", "OA")).thenReturn(List.of());

        AiRuntimeToolCallbackProvider provider = new AiRuntimeToolCallbackProvider(
                aiRegistryCatalogService,
                aiToolRegistry,
                aiMcpClientFactory,
                new ObjectMapper()
        );

        ToolCallback[] callbacks = provider.createProvider("usr_001", "OA").getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        String json = callbacks[0].call(
                "{\"keyword\":\"审批轨迹\"}",
                new ToolContext(Map.of("conversationId", "conv_001"))
        );
        assertThat(json).contains("\"count\":2");
        assertThat(json).contains("审批轨迹");
    }
}
