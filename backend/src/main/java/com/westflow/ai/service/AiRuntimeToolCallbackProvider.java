package com.westflow.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolExecutionContext;
import com.westflow.ai.tool.AiToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.stereotype.Service;

/**
 * 运行时 ToolCallback 提供器，把平台读工具通过内部 MCP 桥暴露给 Spring AI。
 */
@Service
public class AiRuntimeToolCallbackProvider {

    private static final String INTERNAL_MCP_CODE = "westflow-internal-mcp";

    private final AiRegistryCatalogService aiRegistryCatalogService;
    private final AiToolRegistry aiToolRegistry;
    private final AiMcpClientFactory aiMcpClientFactory;
    private final ObjectMapper objectMapper;

    public AiRuntimeToolCallbackProvider(
            AiRegistryCatalogService aiRegistryCatalogService,
            AiToolRegistry aiToolRegistry,
            AiMcpClientFactory aiMcpClientFactory,
            ObjectMapper objectMapper
    ) {
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.aiToolRegistry = aiToolRegistry;
        this.aiMcpClientFactory = aiMcpClientFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 为当前会话构造可直接执行的只读工具回调。
     */
    public ToolCallbackProvider createProvider(String userId, String domain) {
        List<ToolCallback> callbacks = new ArrayList<>(internalCallbacks(userId, domain));
        callbacks.addAll(externalCallbacks(userId, domain));
        return ToolCallbackProvider.from(callbacks);
    }

    private List<ToolCallback> internalCallbacks(String userId, String domain) {
        if (aiRegistryCatalogService.findMcp(userId, INTERNAL_MCP_CODE).isEmpty()) {
            return List.of();
        }
        return aiRegistryCatalogService.listReadableTools(userId, domain).stream()
                .filter(item -> INTERNAL_MCP_CODE.equalsIgnoreCase(item.mcpCode()))
                .map(this::toToolCallback)
                .toList();
    }

    private List<ToolCallback> externalCallbacks(String userId, String domain) {
        List<McpSyncClient> clients = aiMcpClientFactory.createClients(userId, domain);
        if (clients.isEmpty()) {
            return List.of();
        }
        return SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
    }

    private ToolCallback toToolCallback(AiRegistryCatalogService.AiToolCatalogItem item) {
        AiToolDefinition definition = aiToolRegistry.require(item.toolCode());
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return DefaultToolDefinition.builder()
                        .name(item.toolCode())
                        .description(item.toolName())
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return DefaultToolMetadata.builder().returnDirect(false).build();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, new ToolContext(Map.of()));
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                Map<String, Object> arguments = parseArguments(toolInput);
                Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
                String conversationId = stringValue(context.get("conversationId"), "runtime_tool");
                var request = new AiToolCallRequest(item.toolCode(), definition.toolType(), definition.toolSource(), arguments);
                Map<String, Object> result = definition.handler().execute(new AiToolExecutionContext(
                        conversationId,
                        request,
                        definition,
                        OffsetDateTime.now(ZoneOffset.ofHours(8))
                ));
                try {
                    return objectMapper.writeValueAsString(result);
                } catch (JsonProcessingException exception) {
                    return "{\"error\":\"TOOL_SERIALIZE_FAILED\"}";
                }
            }
        };
    }

    private Map<String, Object> parseArguments(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of("raw", toolInput);
        }
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isBlank() ? defaultValue : text;
    }
}
