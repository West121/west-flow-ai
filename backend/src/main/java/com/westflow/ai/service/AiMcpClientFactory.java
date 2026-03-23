package com.westflow.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 运行时 MCP 客户端工厂，负责按数据库注册项创建并缓存真实 MCP 客户端。
 */
@Slf4j
@Service
public class AiMcpClientFactory {

    private final AiRegistryCatalogService aiRegistryCatalogService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    public AiMcpClientFactory(
            AiRegistryCatalogService aiRegistryCatalogService,
            ObjectMapper objectMapper
    ) {
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 为当前用户和业务域创建可用的外部 MCP 客户端。
     */
    public List<McpSyncClient> createClients(String userId, String domain) {
        return aiRegistryCatalogService.listMcps(userId, domain).stream()
                .filter(item -> !isInternal(item))
                .map(this::getOrCreateClient)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 关闭缓存中的 MCP 客户端连接。
     */
    @PreDestroy
    public void closeAll() {
        clientCache.values().forEach(client -> {
            try {
                client.closeGracefully();
            } catch (RuntimeException exception) {
                log.debug("close mcp client failed: {}", exception.getMessage());
            }
        });
        clientCache.clear();
    }

    private Optional<McpSyncClient> getOrCreateClient(AiRegistryCatalogService.AiMcpCatalogItem item) {
        if (clientCache.containsKey(item.mcpCode())) {
            return Optional.of(clientCache.get(item.mcpCode()));
        }
        Optional<McpSyncClient> createdClient = createClient(item);
        createdClient.ifPresent(client -> clientCache.put(item.mcpCode(), client));
        return createdClient;
    }

    Optional<McpSyncClient> createClient(AiRegistryCatalogService.AiMcpCatalogItem item) {
        try {
            McpSyncClient client = switch (normalizeTransport(item.transportType())) {
                case "STREAMABLE_HTTP" -> createStreamableHttpClient(item);
                case "STDIO" -> createStdioClient(item);
                default -> null;
            };
            if (client == null) {
                return Optional.empty();
            }
            client.initialize();
            return Optional.of(client);
        } catch (RuntimeException exception) {
            log.warn("init mcp client failed: code={}, message={}", item.mcpCode(), exception.getMessage());
            return Optional.empty();
        }
    }

    private McpSyncClient createStreamableHttpClient(AiRegistryCatalogService.AiMcpCatalogItem item) {
        if (item.endpointUrl() == null || item.endpointUrl().isBlank()) {
            return null;
        }
        Map<String, String> headers = readStringMap(item.metadata(), "headers");
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(item.endpointUrl())
                .clientBuilder(HttpClient.newBuilder())
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .connectTimeout(Duration.ofSeconds(readInt(item.metadata(), "connectTimeoutSeconds", 5)))
                .openConnectionOnStartup(readBoolean(item.metadata(), "openConnectionOnStartup", false))
                .customizeRequest(builder -> headers.forEach(builder::header))
                .build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("west-flow-ai", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(readInt(item.metadata(), "requestTimeoutSeconds", 15)))
                .build();
    }

    private McpSyncClient createStdioClient(AiRegistryCatalogService.AiMcpCatalogItem item) {
        String command = readString(item.metadata(), "command", "");
        if (command.isBlank()) {
            return null;
        }
        ServerParameters parameters = ServerParameters.builder(command)
                .args(readStringList(item.metadata(), "args"))
                .env(readStringMap(item.metadata(), "env"))
                .build();
        StdioClientTransport transport = new StdioClientTransport(parameters, new JacksonMcpJsonMapper(objectMapper));
        transport.setStdErrorHandler(line -> log.debug("mcp stdio [{}] stderr: {}", item.mcpCode(), line));
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("west-flow-ai", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(readInt(item.metadata(), "requestTimeoutSeconds", 20)))
                .build();
    }

    private boolean isInternal(AiRegistryCatalogService.AiMcpCatalogItem item) {
        return "INTERNAL".equalsIgnoreCase(normalizeTransport(item.transportType()));
    }

    private String normalizeTransport(String transportType) {
        return transportType == null ? "" : transportType.trim().toUpperCase();
    }

    private Map<String, String> readStringMap(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            LinkedHashMap<String, String> converted = new LinkedHashMap<>();
            raw.forEach((mapKey, mapValue) -> converted.put(mapKey, mapValue == null ? "" : mapValue.toString()));
            return converted;
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof List<?> values) {
            List<String> result = new ArrayList<>();
            for (Object item : values) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    private String readString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isBlank() ? defaultValue : text;
    }

    private int readInt(Map<String, Object> metadata, String key, int defaultValue) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean readBoolean(Map<String, Object> metadata, String key, boolean defaultValue) {
        Object value = metadata.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }
}
