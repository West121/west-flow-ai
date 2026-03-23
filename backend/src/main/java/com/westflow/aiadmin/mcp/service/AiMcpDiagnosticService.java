package com.westflow.aiadmin.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.mcp.api.AiMcpDiagnosticResponse;
import com.westflow.aiadmin.mcp.api.AiMcpDiagnosticStepResponse;
import com.westflow.aiadmin.mcp.mapper.AiMcpRegistryMapper;
import com.westflow.aiadmin.mcp.model.AiMcpRegistryRecord;
import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminSupport;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI MCP 连通性诊断服务。
 */
@Service
@RequiredArgsConstructor
public class AiMcpDiagnosticService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "transportType", "connectionStatus");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("checkedAt", "responseTimeMillis", "mcpCode", "connectionStatus", "transportType", "status");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiMcpRegistryMapper aiMcpRegistryMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询 MCP 连通性诊断。
     */
    public PageResponse<AiMcpDiagnosticResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<DiagnosticSnapshot> comparator = resolveComparator(request.sorts());
        List<AiMcpDiagnosticResponse> matched = aiMcpRegistryMapper.selectAll().stream()
                .map(this::inspect)
                .filter(snapshot -> matchesKeyword(snapshot, request.keyword()))
                .filter(snapshot -> filters.status() == null || filters.status().equals(snapshot.response().registryStatus()))
                .filter(snapshot -> filters.transportType() == null || filters.transportType().equals(snapshot.response().transportType()))
                .filter(snapshot -> filters.connectionStatus() == null || filters.connectionStatus().equals(snapshot.response().connectionStatus()))
                .sorted(comparator)
                .map(DiagnosticSnapshot::response)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询单个 MCP 的连通性诊断。
     */
    public AiMcpDiagnosticResponse detail(String mcpId) {
        aiAdminAccessService.ensureAiAdminAccess();
        AiMcpRegistryRecord record = requireRecord(mcpId);
        return inspect(record).response();
    }

    /**
     * 重新执行单个 MCP 的连通性诊断。
     */
    public AiMcpDiagnosticResponse recheck(String mcpId) {
        aiAdminAccessService.ensureAiAdminAccess();
        AiMcpRegistryRecord record = requireRecord(mcpId);
        return inspect(record).response();
    }

    private DiagnosticSnapshot inspect(AiMcpRegistryRecord record) {
        OffsetDateTime checkedAt = OffsetDateTime.now(Clock.system(ZONE_ID));
        List<AiMcpDiagnosticStepResponse> steps = new ArrayList<>();
        steps.add(diagnosticStep(
                "registry",
                "INFO",
                "已读取 MCP 注册记录",
                0L
        ));
        if (!record.enabled()) {
            steps.add(diagnosticStep(
                    "registry-status",
                    "WARN",
                    "MCP 已停用，跳过真实连通检测",
                    0L
            ));
            return buildSnapshot(
                    record,
                    checkedAt,
                    "DISABLED",
                    0L,
                    0,
                    "MCP 已停用",
                    "注册状态为 DISABLED，已跳过真实连通检测。",
                    "REGISTRY_DISABLED",
                    steps
            );
        }

        String transportType = normalizeTransport(record.transportType());
        if ("INTERNAL".equalsIgnoreCase(transportType)) {
            steps.add(diagnosticStep(
                    "transport",
                    "INFO",
                    "平台内置 MCP，跳过外部网络连通检测",
                    0L
            ));
            return buildSnapshot(
                    record,
                    checkedAt,
                    "INTERNAL",
                    0L,
                    null,
                    null,
                    null,
                    null,
                    steps
            );
        }

        Instant start = Instant.now();
        try (McpSyncClient client = createClient(record)) {
            steps.add(diagnosticStep(
                    "transport",
                    "PASS",
                    "MCP 客户端创建成功",
                    elapsedMillis(start)
            ));

            Instant initializeStart = Instant.now();
            try {
                client.initialize();
                steps.add(diagnosticStep(
                        "initialize",
                        "PASS",
                        "MCP 客户端初始化成功",
                        elapsedMillis(initializeStart)
                ));
            } catch (RuntimeException exception) {
                long responseTimeMillis = elapsedMillis(start);
                steps.add(diagnosticStep(
                        "initialize",
                        "FAIL",
                        resolveFailureReason(exception),
                        elapsedMillis(initializeStart)
                ));
                return buildSnapshot(
                        record,
                        checkedAt,
                        "DOWN",
                        responseTimeMillis,
                        null,
                        resolveFailureReason(exception),
                        describeFailure(exception),
                        "INITIALIZE",
                        steps
                );
            }

            Instant listToolsStart = Instant.now();
            try {
                int toolCount = client.listTools().tools().size();
                steps.add(diagnosticStep(
                        "list-tools",
                        "PASS",
                        "读取工具列表成功，共 %d 个工具".formatted(toolCount),
                        elapsedMillis(listToolsStart)
                ));
                long responseTimeMillis = elapsedMillis(start);
                return buildSnapshot(
                        record,
                        checkedAt,
                        "UP",
                        responseTimeMillis,
                        toolCount,
                        null,
                        null,
                        null,
                        steps
                );
            } catch (RuntimeException exception) {
                long responseTimeMillis = elapsedMillis(start);
                steps.add(diagnosticStep(
                        "list-tools",
                        "FAIL",
                        resolveFailureReason(exception),
                        elapsedMillis(listToolsStart)
                ));
                return buildSnapshot(
                        record,
                        checkedAt,
                        "DOWN",
                        responseTimeMillis,
                        null,
                        resolveFailureReason(exception),
                        describeFailure(exception),
                        "LIST_TOOLS",
                        steps
                );
            }
        } catch (RuntimeException exception) {
            long responseTimeMillis = Math.max(Duration.between(start, Instant.now()).toMillis(), 1L);
            steps.add(diagnosticStep(
                    "transport",
                    "FAIL",
                    resolveFailureReason(exception),
                    responseTimeMillis
            ));
            return buildSnapshot(
                    record,
                    checkedAt,
                    "DOWN",
                    responseTimeMillis,
                    null,
                    resolveFailureReason(exception),
                    describeFailure(exception),
                    "TRANSPORT_CONFIG",
                    steps
            );
        }
    }

    private McpSyncClient createClient(AiMcpRegistryRecord record) {
        String transportType = normalizeTransport(record.transportType());
        return switch (transportType) {
            case "STREAMABLE_HTTP" -> createStreamableHttpClient(record);
            case "STDIO" -> createStdioClient(record);
            default -> throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "不支持的 MCP 传输类型",
                    Map.of("transportType", record.transportType())
            );
        };
    }

    private McpSyncClient createStreamableHttpClient(AiMcpRegistryRecord record) {
        if (record.endpointUrl() == null || record.endpointUrl().isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "MCP 地址不能为空",
                    Map.of("mcpId", record.mcpId())
            );
        }
        Map<String, Object> metadata = readMetadata(record.metadataJson());
        Map<String, String> headers = readStringMap(metadata, "headers");
        ParsedEndpoint endpoint = parseEndpoint(record.endpointUrl());
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(endpoint.baseUrl())
                .clientBuilder(HttpClient.newBuilder())
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .endpoint(endpoint.endpointPath())
                .connectTimeout(Duration.ofSeconds(readInt(metadata, "connectTimeoutSeconds", 5)))
                .openConnectionOnStartup(readBoolean(metadata, "openConnectionOnStartup", false))
                .customizeRequest(builder -> headers.forEach(builder::header))
                .build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("west-flow-ai-admin", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(readInt(metadata, "requestTimeoutSeconds", 15)))
                .build();
    }

    private McpSyncClient createStdioClient(AiMcpRegistryRecord record) {
        Map<String, Object> metadata = readMetadata(record.metadataJson());
        String command = readString(metadata, "command", "");
        if (command.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "STDIO MCP 命令不能为空",
                    Map.of("mcpId", record.mcpId())
            );
        }
        ServerParameters parameters = ServerParameters.builder(command)
                .args(readStringList(metadata, "args"))
                .env(readStringMap(metadata, "env"))
                .build();
        StdioClientTransport transport = new StdioClientTransport(parameters, new JacksonMcpJsonMapper(objectMapper));
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("west-flow-ai-admin", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(readInt(metadata, "requestTimeoutSeconds", 20)))
                .build();
    }

    private boolean matchesKeyword(DiagnosticSnapshot snapshot, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return snapshot.response().mcpCode().toLowerCase().contains(normalized)
                || snapshot.response().mcpName().toLowerCase().contains(normalized)
                || (snapshot.response().endpointUrl() != null && snapshot.response().endpointUrl().toLowerCase().contains(normalized))
                || snapshot.response().transportType().toLowerCase().contains(normalized)
                || snapshot.response().registryStatus().toLowerCase().contains(normalized)
                || snapshot.response().connectionStatus().toLowerCase().contains(normalized)
                || (snapshot.response().failureReason() != null && snapshot.response().failureReason().toLowerCase().contains(normalized))
                || (snapshot.response().failureStage() != null && snapshot.response().failureStage().toLowerCase().contains(normalized))
                || (snapshot.response().failureDetail() != null && snapshot.response().failureDetail().toLowerCase().contains(normalized));
    }

    private Comparator<DiagnosticSnapshot> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().checkedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<DiagnosticSnapshot> comparator = switch (sort.field()) {
            case "mcpCode" -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().mcpCode(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "connectionStatus" -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().connectionStatus(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "transportType" -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().transportType(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "status" -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().registryStatus(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "responseTimeMillis" -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().responseTimeMillis(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                    (DiagnosticSnapshot snapshot) -> snapshot.response().checkedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        String transportType = null;
        String connectionStatus = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> status = AiAdminSupport.normalize(value);
                case "transportType" -> transportType = AiAdminSupport.normalize(value);
                case "connectionStatus" -> connectionStatus = AiAdminSupport.normalize(value);
                default -> {
                }
            }
        }
        return new Filters(status, transportType, connectionStatus);
    }

    private AiMcpRegistryRecord requireRecord(String mcpId) {
        AiMcpRegistryRecord record = aiMcpRegistryMapper.selectById(mcpId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "MCP 注册记录不存在",
                    Map.of("mcpId", mcpId)
            );
        }
        return record;
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
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

    private AiMcpDiagnosticStepResponse diagnosticStep(
            String step,
            String status,
            String message,
            Long durationMillis
    ) {
        return new AiMcpDiagnosticStepResponse(step, status, message, durationMillis);
    }

    private DiagnosticSnapshot buildSnapshot(
            AiMcpRegistryRecord record,
            OffsetDateTime checkedAt,
            String connectionStatus,
            Long responseTimeMillis,
            Integer toolCount,
            String failureReason,
            String failureDetail,
            String failureStage,
            List<AiMcpDiagnosticStepResponse> diagnosticSteps
    ) {
        return new DiagnosticSnapshot(new AiMcpDiagnosticResponse(
                record.mcpId(),
                record.mcpCode(),
                record.mcpName(),
                record.endpointUrl(),
                record.transportType(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                connectionStatus,
                responseTimeMillis,
                toolCount,
                failureReason,
                failureDetail,
                failureStage,
                diagnosticSteps,
                checkedAt,
                record.metadataJson()
        ));
    }

    private long elapsedMillis(Instant start) {
        return Math.max(Duration.between(start, Instant.now()).toMillis(), 1L);
    }

    private String resolveFailureReason(Exception exception) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        if (exception.getCause() != null && exception.getCause().getMessage() != null && !exception.getCause().getMessage().isBlank()) {
            return exception.getCause().getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private String describeFailure(Exception exception) {
        String message = resolveFailureReason(exception);
        if (exception instanceof ContractException contractException) {
            return """
                    message=%s
                    code=%s
                    status=%s
                    details=%s
                    """.formatted(
                    message,
                    contractException.getCode(),
                    contractException.getStatus().value(),
                    formatDetails(contractException.getDetails())
            ).trim();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(exception.getClass().getSimpleName()).append(": ").append(message);
        if (exception.getCause() != null && exception.getCause().getMessage() != null && !exception.getCause().getMessage().isBlank()) {
            builder.append("\nCause: ")
                    .append(exception.getCause().getClass().getSimpleName())
                    .append(": ")
                    .append(exception.getCause().getMessage());
        }
        return builder.toString();
    }

    private String formatDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            return details.toString();
        }
    }

    private String normalizeTransport(String transportType) {
        return transportType == null ? "" : transportType.trim().toUpperCase();
    }

    private ParsedEndpoint parseEndpoint(String endpointUrl) {
        URI uri = URI.create(endpointUrl);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        String baseUrl = port > 0
                ? "%s://%s:%d".formatted(scheme, host, port)
                : "%s://%s".formatted(scheme, host);
        String path = uri.getPath();
        String endpointPath = (path == null || path.isBlank()) ? "/mcp" : path;
        return new ParsedEndpoint(baseUrl, endpointPath);
    }

    private ContractException unsupported(String message, String value, List<String> allowedValues) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("value", value, "allowedValues", allowedValues)
        );
    }

    private record Filters(
            String status,
            String transportType,
            String connectionStatus
    ) {
    }

    private record ParsedEndpoint(
            String baseUrl,
            String endpointPath
    ) {
    }

    private record DiagnosticSnapshot(
            AiMcpDiagnosticResponse response
    ) {
    }
}
