package com.westflow.aimcpdemo;

import com.westflow.ai.service.AiRegistryCatalogService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP Demo 端点集成测试。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "westflow.ai.mcp-demo.enabled=true"
})
class AiMcpDemoEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AiRegistryCatalogService aiRegistryCatalogService;

    /**
     * 验证 MCP Demo 能被注册表读取，并且能被真实 MCP 客户端调用。
     */
    @Test
    void shouldExposeStreamableHttpToolsAndCallThemSuccessfully() {
        assertThat(aiRegistryCatalogService.listMcps("usr_admin", "OA"))
                .anyMatch(item -> "westflow-demo-mcp".equals(item.mcpCode()));

        String endpoint = "http://localhost:%d/api/mcp-demo".formatted(port);
        URI endpointUri = URI.create(endpoint);
        String baseUrl = "%s://%s:%d".formatted(endpointUri.getScheme(), endpointUri.getHost(), endpointUri.getPort());
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .clientBuilder(HttpClient.newBuilder())
                .openConnectionOnStartup(true)
                .connectTimeout(Duration.ofSeconds(10))
                .endpoint(endpointUri.getPath())
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("west-flow-ai-test", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(15))
                .build();

        try {
            client.initialize();
            assertThat(client.isInitialized()).isTrue();

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("westflow.demo.time.now", "westflow.demo.approval.summary");

            String currentTimeText = firstText(client.callTool(new McpSchema.CallToolRequest("westflow.demo.time.now", Map.of())));
            String summaryText = firstText(client.callTool(new McpSchema.CallToolRequest(
                    "westflow.demo.approval.summary",
                    Map.of("processKey", "oa_leave", "category", "OA", "limit", 3)
            )));

            assertThat(currentTimeText).contains("Asia/Shanghai");
            assertThat(summaryText).contains("oa_leave");
            assertThat(summaryText).contains("请假审批");
        } finally {
            client.closeGracefully();
        }
    }

    /**
     * 提取 MCP 文本返回内容。
     */
    private String firstText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("MCP 返回里没有文本内容"));
    }

}
