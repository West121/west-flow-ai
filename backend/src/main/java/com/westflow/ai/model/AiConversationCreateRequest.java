package com.westflow.ai.model;

import java.util.List;

/**
 * 新建会话请求。
 */
public record AiConversationCreateRequest(
        String title,
        List<String> contextTags
) {
    public AiConversationCreateRequest {
        contextTags = contextTags == null ? List.of() : List.copyOf(contextTags);
    }
}
