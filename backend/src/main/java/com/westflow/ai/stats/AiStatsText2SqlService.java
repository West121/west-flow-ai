package com.westflow.ai.stats;

import com.westflow.common.error.ContractException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 受控 text2sql 统计查询执行器。
 */
@Service
public class AiStatsText2SqlService {

    private static final Logger log = LoggerFactory.getLogger(AiStatsText2SqlService.class);

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "grant", "revoke", "comment"
    );

    private final AiStatsSqlGenerator sqlGenerator;
    private final AiStatsSchemaCatalogService schemaCatalogService;
    private final JdbcTemplate jdbcTemplate;

    public AiStatsText2SqlService(
            AiStatsSqlGenerator sqlGenerator,
            AiStatsSchemaCatalogService schemaCatalogService,
            JdbcTemplate jdbcTemplate
    ) {
        this.sqlGenerator = sqlGenerator;
        this.schemaCatalogService = schemaCatalogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> query(Map<String, Object> arguments) {
        String keyword = stringValue(arguments.get("keyword"));
        String domainHint = stringValue(arguments.get("domain"));
        try {
            String schemaContext = schemaCatalogService.buildPromptContext(keyword, domainHint);
            AiStatsSqlPlan plan = sqlGenerator.generate(keyword, schemaContext);
            ValidatedSql validatedSql = validate(plan, schemaCatalogService.allowedTables(keyword, domainHint));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(validatedSql.sql());
            return toResult(keyword, plan, rows);
        } catch (ContractException exception) {
            log.warn("AI stats query rejected keyword={} reason={}", keyword, exception.getMessage());
            return failureResult(keyword, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("AI stats query failed keyword={}", keyword, exception);
            return failureResult(keyword, "统计查询暂时不可用，请稍后重试或换一种问法。");
        }
    }

    private ValidatedSql validate(AiStatsSqlPlan plan, Set<String> allowedTables) {
        if (plan == null || plan.sql() == null || plan.sql().isBlank()) {
            throw new ContractException("AI.STATS_SQL_INVALID", HttpStatus.BAD_REQUEST, "统计 SQL 为空");
        }
        String sql = plan.sql().trim();
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (sql.contains(";")) {
            throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 不允许多语句");
        }
        if (FORBIDDEN_KEYWORDS.stream().anyMatch(normalized::contains)) {
            throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 只允许只读查询");
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)
                    || !(select.getPlainSelect() instanceof PlainSelect plainSelect)) {
                throw new ContractException("AI.STATS_SQL_UNSUPPORTED", HttpStatus.BAD_REQUEST, "当前仅支持简单 SELECT 查询");
            }
            validatePlainSelect(select, plainSelect, allowedTables);
            return new ValidatedSql(sql, plainSelect);
        } catch (ContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ContractException("AI.STATS_SQL_INVALID", HttpStatus.BAD_REQUEST, "统计 SQL 校验失败");
        }
    }

    private void validatePlainSelect(Select select, PlainSelect plainSelect, Set<String> allowedTables) {
        TablesNamesFinder finder = new TablesNamesFinder();
        Set<String> usedTables = new LinkedHashSet<>(finder.getTableList((Statement) select));
        if (usedTables.isEmpty() || !allowedTables.containsAll(usedTables)) {
            throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 使用了未授权的数据表");
        }
        if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()) {
            throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 不允许 INTO");
        }
        if (plainSelect.getSelectItems() == null || plainSelect.getSelectItems().isEmpty()) {
            throw new ContractException("AI.STATS_SQL_INVALID", HttpStatus.BAD_REQUEST, "统计 SQL 缺少查询列");
        }
        for (var item : plainSelect.getSelectItems()) {
            if (item instanceof SelectItem<?> selectItem
                    && (selectItem.getExpression() instanceof AllColumns
                    || selectItem.getExpression() instanceof AllTableColumns)) {
                throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 不允许 SELECT *");
            }
        }
        if (plainSelect.getLimit() == null && plainSelect.getGroupBy() == null && !containsAggregate(plainSelect)) {
            throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "非聚合明细查询必须带 LIMIT");
        }
        Limit limit = plainSelect.getLimit();
        if (limit != null && limit.getRowCount() != null) {
            try {
                long rowCount = Long.parseLong(limit.getRowCount().toString());
                if (rowCount > 50L) {
                    throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 的 LIMIT 不能超过 50");
                }
            } catch (NumberFormatException ignored) {
                throw new ContractException("AI.STATS_SQL_INVALID", HttpStatus.BAD_REQUEST, "统计 SQL 的 LIMIT 非法");
            }
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.isCross()) {
                    throw new ContractException("AI.STATS_SQL_FORBIDDEN", HttpStatus.BAD_REQUEST, "统计 SQL 不允许 CROSS JOIN");
                }
            }
        }
    }

    private boolean containsAggregate(PlainSelect plainSelect) {
        for (var item : plainSelect.getSelectItems()) {
            if (item instanceof SelectItem<?> selectItem) {
                Expression expression = selectItem.getExpression();
                if (expression instanceof Function function && isAggregate(function)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAggregate(Function function) {
        String name = function.getName();
        if (name == null) {
            return false;
        }
        return Set.of("count", "sum", "avg", "min", "max").contains(name.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> toResult(String keyword, AiStatsSqlPlan plan, List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        String presentation = normalizePresentation(plan.presentation(), rows);
        String xField = resolveFieldKey(plan.xField(), rows);
        String yField = resolveFieldKey(plan.yField(), rows);
        String scope = "text2sql.stats";
        result.put("scope", scope);
        result.put("title", defaultTitle(plan.title(), keyword));
        result.put("summary", buildSummary(plan, rows));
        result.put("data", normalizeRows(rows));
        result.put("metrics", buildMetrics(rows, plan, presentation));
        result.put("chart", buildChart(plan, presentation, rows, xField, yField));
        result.put("generatedAt", OffsetDateTime.now(TIME_ZONE).toString());
        return result;
    }

    private Map<String, Object> failureResult(String keyword, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", "text2sql.stats");
        result.put("title", defaultTitle(null, keyword));
        result.put("summary", message);
        result.put("error", true);
        result.put("generatedAt", OffsetDateTime.now(TIME_ZONE).toString());
        return result;
    }

    private String normalizePresentation(String presentation, List<Map<String, Object>> rows) {
        if (presentation == null || presentation.isBlank()) {
            if (rows.size() == 1 && rows.getFirst().size() == 1) {
                return "metric";
            }
            if (rows.size() == 1 && rows.getFirst().size() > 1) {
                return "stats";
            }
            return "table";
        }
        return presentation;
    }

    private String defaultTitle(String title, String keyword) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return keyword == null || keyword.isBlank() ? "统计结果" : keyword;
    }

    private String buildSummary(AiStatsSqlPlan plan, List<Map<String, Object>> rows) {
        if (plan.description() != null && !plan.description().isBlank()) {
            return plan.description();
        }
        if (rows.isEmpty()) {
            return "当前未查询到相关数据。";
        }
        if (rows.size() == 1 && rows.getFirst().size() == 1) {
            Object value = rows.getFirst().values().iterator().next();
            return "当前统计结果为 " + value + "。";
        }
        return "当前共返回 " + rows.size() + " 条统计结果。";
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    row.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
                    return Map.copyOf(normalized);
                })
                .toList();
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(OffsetDateTime.now(TIME_ZONE).getOffset()).toString();
        }
        return value;
    }

    private List<Map<String, Object>> buildMetrics(List<Map<String, Object>> rows, AiStatsSqlPlan plan, String presentation) {
        if (rows.isEmpty()) {
            return List.of();
        }
        if ("metric".equals(presentation) && rows.size() == 1) {
            Map<String, Object> row = rows.getFirst();
            Map.Entry<String, Object> entry = row.entrySet().iterator().next();
            return List.of(Map.of(
                    "label", plan.metricLabel() == null || plan.metricLabel().isBlank() ? beautifyMetricLabel(entry.getKey()) : plan.metricLabel(),
                    "value", entry.getValue(),
                    "tone", "positive"
            ));
        }
        if ("stats".equals(presentation) && rows.size() == 1) {
            return rows.getFirst().entrySet().stream()
                    .map(entry -> Map.of(
                            "label", beautifyMetricLabel(entry.getKey()),
                            "value", entry.getValue(),
                            "tone", "neutral"
                    ))
                    .toList();
        }
        return List.of();
    }

    private String resolveFieldKey(String requestedField, List<Map<String, Object>> rows) {
        if (requestedField == null || requestedField.isBlank() || rows.isEmpty()) {
            return requestedField;
        }
        Set<String> keys = rows.stream()
                .flatMap(row -> row.keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return keys.stream()
                .filter(key -> key.equalsIgnoreCase(requestedField))
                .findFirst()
                .orElse(requestedField);
    }

    private Map<String, Object> buildChart(
            AiStatsSqlPlan plan,
            String presentation,
            List<Map<String, Object>> rows,
            String xField,
            String yField
    ) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", presentation);
        chart.put("title", defaultTitle(plan.title(), "统计结果"));
        chart.put("description", buildSummary(plan, rows));
        if ("metric".equals(presentation)) {
            Object value = rows.isEmpty() || rows.getFirst().isEmpty() ? 0 : rows.getFirst().values().iterator().next();
            chart.put("metricLabel", plan.metricLabel() == null || plan.metricLabel().isBlank() ? "统计值" : plan.metricLabel());
            chart.put("valueLabel", rows.isEmpty() ? "--" : "当前结果");
            chart.put("value", value);
            return chart;
        }
        if ("stats".equals(presentation)) {
            return Map.of();
        }
        if ("table".equals(presentation)) {
            chart.put("columns", rows.isEmpty() ? List.of() : rows.getFirst().keySet().stream()
                    .map(key -> Map.of("key", key, "label", key))
                    .toList());
            return chart;
        }
        chart.put("xField", xField);
        chart.put("yField", yField);
        chart.put("series", List.of(Map.of(
                "dataKey", yField == null || yField.isBlank() ? inferNumericField(rows) : yField,
                "name", plan.metricLabel() == null || plan.metricLabel().isBlank() ? "数量" : plan.metricLabel()
        )));
        return chart;
    }

    private String inferNumericField(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "value";
        }
        return rows.getFirst().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Number)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("value");
    }

    private String beautifyMetricLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return "统计值";
        }
        String normalized = rawLabel.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "count", "totalcount" -> "总数";
            case "rolecount" -> "角色总数";
            case "usercount" -> "用户总数";
            case "departmentcount" -> "部门总数";
            case "companycount" -> "公司总数";
            case "postcount" -> "岗位总数";
            case "menucount" -> "菜单总数";
            case "disabledusercount" -> "停用用户数";
            case "enabledusercount" -> "启用用户数";
            default -> splitIdentifier(normalized);
        };
    }

    private String splitIdentifier(String value) {
        String withSpaces = value
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        if (withSpaces.isBlank()) {
            return value;
        }
        return withSpaces;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ValidatedSql(String sql, PlainSelect plainSelect) {}
}
