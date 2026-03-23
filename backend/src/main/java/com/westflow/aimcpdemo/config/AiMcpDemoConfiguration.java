package com.westflow.aimcpdemo.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aimcpdemo.service.AiMcpDemoQueryService;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Demo 的服务器配置。
 */
@Configuration
@EnableConfigurationProperties(AiMcpDemoProperties.class)
@ConditionalOnProperty(prefix = "westflow.ai.mcp-demo", name = "enabled", havingValue = "true")
public class AiMcpDemoConfiguration {

    /**
     * 创建 Streamable HTTP 传输服务。
     */
    @Bean
    public HttpServletStreamableServerTransportProvider aiMcpDemoTransportProvider(
            AiMcpDemoProperties properties,
            ObjectMapper objectMapper
    ) {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(properties.endpointPath())
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .build();
    }

    /**
     * 注册 MCP Demo 的 Servlet 映射。
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> aiMcpDemoServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider,
            AiMcpDemoProperties properties
    ) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registrationBean = new ServletRegistrationBean<>(
                transportProvider,
                properties.endpointPath(),
                properties.endpointPath() + "/*"
        );
        registrationBean.setName("aiMcpDemoServlet");
        registrationBean.setLoadOnStartup(1);
        return registrationBean;
    }

    /**
     * 构建 MCP Demo 服务。
     */
    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer aiMcpDemoServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            AiMcpDemoProperties properties,
            AiMcpDemoQueryService queryService,
            ObjectMapper objectMapper
    ) {
        return McpServer.sync(transportProvider)
                .serverInfo(properties.serverName(), properties.serverVersion())
                .instructions(properties.description())
                .tools(
                        currentTimeTool(queryService, objectMapper),
                        approvalSummaryTool(queryService, objectMapper, properties.summaryLimit())
                )
                .build();
    }

    /**
     * 当前时间工具。
     */
    private McpServerFeatures.SyncToolSpecification currentTimeTool(AiMcpDemoQueryService queryService, ObjectMapper objectMapper) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("westflow.demo.time.now")
                        .title("查询当前时间")
                        .description("返回本地时区的当前时间快照。")
                        .inputSchema(emptyObjectSchema())
                        .build())
                .callHandler((exchange, request) -> toCallToolResult(queryService.currentTimeSnapshot(), objectMapper, false))
                .build();
    }

    /**
     * 审批摘要工具。
     */
    private McpServerFeatures.SyncToolSpecification approvalSummaryTool(
            AiMcpDemoQueryService queryService,
            ObjectMapper objectMapper,
            int summaryLimit
    ) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("westflow.demo.approval.summary")
                        .title("查询审批摘要")
                        .description("返回本地审批定义、绑定关系和会签统计摘要。可选传入 processKey、category 和 limit。")
                        .inputSchema(new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "processKey", Map.of("type", "string", "description", "流程定义编码，非必填"),
                                        "category", Map.of("type", "string", "description", "业务域，例如 OA 或 PLM"),
                                        "limit", Map.of("type", "integer", "description", "返回的最近流程定义条数")
                                ),
                                List.of(),
                                Boolean.TRUE,
                                Map.of(),
                                Map.of()
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
                    String processKey = arguments.get("processKey") == null ? "" : arguments.get("processKey").toString();
                    String category = arguments.get("category") == null ? "" : arguments.get("category").toString();
                    int limit = summaryLimit;
                    Object limitValue = arguments.get("limit");
                    if (limitValue instanceof Number number) {
                        limit = number.intValue();
                    } else if (limitValue instanceof String text) {
                        try {
                            limit = Integer.parseInt(text);
                        } catch (NumberFormatException ignored) {
                            limit = summaryLimit;
                        }
                    }
                    return toCallToolResult(queryService.approvalSummary(processKey, category, limit), objectMapper, false);
                })
                .build();
    }

    /**
     * 创建空对象入参 schema。
     */
    private McpSchema.JsonSchema emptyObjectSchema() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), Boolean.TRUE, Map.of(), Map.of());
    }

    /**
     * 把查询结果转为 MCP 返回值。
     */
    private io.modelcontextprotocol.spec.McpSchema.CallToolResult toCallToolResult(
            Object payload,
            ObjectMapper objectMapper,
            boolean isError
    ) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                    .addTextContent(json)
                    .isError(isError)
                    .build();
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("message", exception.getMessage());
            fallback.put("payloadType", payload == null ? "null" : payload.getClass().getName());
            return io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                    .addTextContent(fallback.toString())
                    .isError(true)
                    .build();
        }
    }
}
