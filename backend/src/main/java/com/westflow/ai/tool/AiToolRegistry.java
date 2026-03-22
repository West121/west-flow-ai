package com.westflow.ai.tool;

import com.westflow.common.error.ContractException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AI 工具注册表。
 */
@Component
public class AiToolRegistry {

    private final Map<String, AiToolDefinition> definitions = new LinkedHashMap<>();

    public AiToolRegistry() {
    }

    public AiToolRegistry(Collection<AiToolDefinition> definitions) {
        if (definitions != null) {
            definitions.forEach(this::register);
        }
    }

    public AiToolDefinition register(AiToolDefinition definition) {
        definitions.put(definition.toolKey(), definition);
        return definition;
    }

    public Optional<AiToolDefinition> find(String toolKey) {
        return Optional.ofNullable(definitions.get(toolKey));
    }

    public AiToolDefinition require(String toolKey) {
        return find(toolKey).orElseThrow(() -> new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "工具未注册"));
    }

    public List<AiToolDefinition> list() {
        return List.copyOf(definitions.values());
    }
}
