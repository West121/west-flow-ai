package com.westflow.ai.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 基于大模型的 text2sql 生成器。
 */
@Service
public class ChatClientAiStatsSqlGenerator implements AiStatsSqlGenerator {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ChatClientAiStatsSqlGenerator(ChatClient aiCopilotChatClient, ObjectMapper objectMapper) {
        this.chatClient = aiCopilotChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiStatsSqlPlan generate(String keyword, String schemaContext) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String response = chatClient.prompt()
                        .system("""
                                你是一个只读统计 text2sql 生成器。
                                你必须只输出一个 JSON 对象，不要输出解释、前后缀或 Markdown。
                                JSON 格式：
                                {
                                  "title": "统计标题",
                                  "sql": "SELECT ...",
                                  "presentation": "metric|stats|table|bar|line|pie|donut|area",
                                  "xField": "可选，图表横轴字段",
                                  "yField": "可选，图表纵轴字段",
                                  "metricLabel": "可选，指标名称",
                                  "description": "可选，结果摘要"
                                }
                                约束：
                                - 只允许 PostgreSQL SELECT 查询。
                                - 不允许 INSERT/UPDATE/DELETE/DDL。
                                - 明细查询必须包含 LIMIT，最大 50。
                                - 单值统计优先 metric。
                                - 单行多指标统计优先 stats。
                                - 分组统计或分布图，按问题选择 table/bar/line/pie/donut/area。
                                - 必须严格使用给定 schema 中的真实表名、真实字段名和给定 join。
                                """)
                        .user("""
                                用户问题：
                                %s

                                可用 schema 上下文：
                                %s
                                """.formatted(keyword, schemaContext))
                        .call()
                        .content();
                JsonNode jsonNode = objectMapper.readTree(extractJson(response));
                return new AiStatsSqlPlan(
                        text(jsonNode, "title"),
                        text(jsonNode, "sql"),
                        text(jsonNode, "presentation"),
                        text(jsonNode, "xField"),
                        text(jsonNode, "yField"),
                        text(jsonNode, "metricLabel"),
                        text(jsonNode, "description")
                );
            } catch (RuntimeException exception) {
                // 重试一次，兼容模型偶发 EOF/空响应。
            } catch (Exception exception) {
                // 重试一次，兼容模型偶发返回 markdown 包裹或非完整 JSON。
            }
        }
        throw new ContractException("AI.STATS_SQL_INVALID", HttpStatus.BAD_REQUEST, "统计 SQL 生成失败");
    }

    private String text(JsonNode jsonNode, String field) {
        JsonNode value = jsonNode.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
