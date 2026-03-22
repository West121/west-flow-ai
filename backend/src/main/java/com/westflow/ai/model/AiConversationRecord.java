package com.westflow.ai.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话持久化记录。
 */
public record AiConversationRecord(
        String conversationId,
        String title,
        String preview,
        String status,
        String contextTagsJson,
        int messageCount,
        String operatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 允许调用层直接传入标签列表，内部统一转换成 JSON 字符串落库。
     */
    public AiConversationRecord(
            String conversationId,
            String title,
            String preview,
            String status,
            List<String> contextTags,
            int messageCount,
            String operatorUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                conversationId,
                title,
                preview,
                status,
                toJson(contextTags),
                messageCount,
                operatorUserId,
                createdAt,
                updatedAt
        );
    }

    private static String toJson(List<String> contextTags) {
        try {
            return OBJECT_MAPPER.writeValueAsString(contextTags == null ? List.of() : List.copyOf(contextTags));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("会话标签序列化失败", exception);
        }
    }
}
