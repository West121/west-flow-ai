package com.westflow.ai.executor;

import java.util.List;

/**
 * 执行上下文。
 */
public record AiExecutionContext(
        String conversationId,
        String userId,
        String content,
        String domain,
        String pageRoute,
        List<String> contextTags
) {
    public AiExecutionContext {
        conversationId = normalize(conversationId);
        userId = normalize(userId);
        content = normalize(content);
        domain = normalize(domain);
        pageRoute = normalize(pageRoute);
        contextTags = contextTags == null ? List.of() : List.copyOf(contextTags);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
