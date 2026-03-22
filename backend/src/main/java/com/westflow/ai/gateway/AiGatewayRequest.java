package com.westflow.ai.gateway;

import java.util.List;

/**
 * AI 网关路由请求。
 */
public record AiGatewayRequest(
        String conversationId,
        String userId,
        String content,
        String domain,
        boolean writeAction,
        List<String> skillIds,
        List<String> contextTags,
        String pageRoute
) {
    public AiGatewayRequest {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        contextTags = contextTags == null ? List.of() : List.copyOf(contextTags);
    }

    public AiGatewayRequest(
            String conversationId,
            String userId,
            String content,
            String domain,
            boolean writeAction,
            List<String> skillIds,
            List<String> contextTags
    ) {
        this(conversationId, userId, content, domain, writeAction, skillIds, contextTags, null);
    }

    public AiGatewayRequest(String conversationId, List<String> contextTags, String content, String pageRoute) {
        this(conversationId, null, content, inferDomain(pageRoute), false, List.of(), contextTags, pageRoute);
    }

    private static String inferDomain(String pageRoute) {
        if (pageRoute == null) {
            return null;
        }
        if (pageRoute.contains("/plm/")) {
            return "PLM";
        }
        if (pageRoute.contains("/workflow/")) {
            return "OA";
        }
        return null;
    }
}
