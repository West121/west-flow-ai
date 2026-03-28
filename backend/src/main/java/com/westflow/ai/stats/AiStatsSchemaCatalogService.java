package com.westflow.ai.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 提供 text2sql 所需的表结构、字段语义、关联关系和示例上下文。
 *
 * <p>表和字段使用实时元数据，业务别名、补充关联和 few-shot 示例由系统维护。</p>
 */
@Service
public class AiStatsSchemaCatalogService {

    private static final Map<String, TableConfig> TABLE_CONFIGS = List.of(
            new TableConfig("wf_user", "用户", List.of("用户", "员工", "人员")),
            new TableConfig("wf_role", "角色", List.of("角色", "权限角色")),
            new TableConfig("wf_user_role", "用户角色关系", List.of("用户角色", "角色关联用户")),
            new TableConfig("wf_user_post", "用户任职", List.of("任职", "兼职", "岗位任职")),
            new TableConfig("wf_user_post_role", "任职角色关系", List.of("任职角色", "兼职角色")),
            new TableConfig("wf_company", "公司", List.of("公司", "企业")),
            new TableConfig("wf_department", "部门", List.of("部门", "组织")),
            new TableConfig("wf_post", "岗位", List.of("岗位", "职位")),
            new TableConfig("wf_menu", "菜单", List.of("菜单", "权限点", "功能项")),
            new TableConfig("wf_dict_type", "字典类型", List.of("字典", "字典类型")),
            new TableConfig("wf_dict_item", "字典项", List.of("字典项")),
            new TableConfig("wf_notification_template", "通知模板", List.of("通知模板", "通知模版")),
            new TableConfig("wf_notification_channel", "通知渠道", List.of("通知渠道")),
            new TableConfig("wf_notification_log", "通知记录", List.of("通知记录", "通知日志")),
            new TableConfig("wf_request_audit_log", "请求日志", List.of("请求日志", "审计日志", "接口日志")),
            new TableConfig("wf_workflow_operation_log", "流程操作日志", List.of("流程操作日志", "操作日志")),
            new TableConfig("wf_delegation", "代理关系", List.of("代理", "委派", "离职转办")),
            new TableConfig("wf_system_message", "系统消息", List.of("系统消息", "站内信", "消息")),
            new TableConfig("wf_file", "文件", List.of("文件", "附件")),
            new TableConfig("wf_trigger_definition", "触发器", List.of("触发器", "触发定义")),
            new TableConfig("wf_orchestrator_execution", "编排执行", List.of("编排执行", "监控", "扫描执行")),
            new TableConfig("wf_business_process_link", "审批单", List.of("审批单", "申请", "请假", "报销", "通用申请")),
            new TableConfig("act_ru_task", "运行中任务", List.of("待办", "任务")),
            new TableConfig("wf_process_definition", "流程定义", List.of("流程定义", "发布流程", "流程配置"))
    ).stream().collect(Collectors.toMap(TableConfig::table, config -> config, (left, right) -> left, LinkedHashMap::new));

    private static final List<SupplementalJoin> SUPPLEMENTAL_JOINS = List.of(
            new SupplementalJoin("wf_user.company_id = wf_company.id", "用户 -> 公司", Set.of("wf_user", "wf_company")),
            new SupplementalJoin("wf_user.active_department_id = wf_department.id", "用户 -> 部门", Set.of("wf_user", "wf_department")),
            new SupplementalJoin("wf_user.active_post_id = wf_post.id", "用户 -> 岗位", Set.of("wf_user", "wf_post")),
            new SupplementalJoin("wf_department.company_id = wf_company.id", "部门 -> 公司", Set.of("wf_department", "wf_company")),
            new SupplementalJoin("wf_post.department_id = wf_department.id", "岗位 -> 部门", Set.of("wf_post", "wf_department")),
            new SupplementalJoin("wf_user_role.user_id = wf_user.id", "用户角色 -> 用户", Set.of("wf_user_role", "wf_user")),
            new SupplementalJoin("wf_user_role.role_id = wf_role.id", "用户角色 -> 角色", Set.of("wf_user_role", "wf_role")),
            new SupplementalJoin("wf_user_post.user_id = wf_user.id", "任职 -> 用户", Set.of("wf_user_post", "wf_user")),
            new SupplementalJoin("wf_user_post.post_id = wf_post.id", "任职 -> 岗位", Set.of("wf_user_post", "wf_post")),
            new SupplementalJoin("wf_user_post_role.user_post_id = wf_user_post.id", "任职角色 -> 任职", Set.of("wf_user_post_role", "wf_user_post")),
            new SupplementalJoin("wf_user_post_role.role_id = wf_role.id", "任职角色 -> 角色", Set.of("wf_user_post_role", "wf_role")),
            new SupplementalJoin("wf_dict_item.dict_type_id = wf_dict_type.id", "字典项 -> 字典类型", Set.of("wf_dict_item", "wf_dict_type")),
            new SupplementalJoin("wf_notification_log.channel_id = wf_notification_channel.id", "通知记录 -> 通知渠道", Set.of("wf_notification_log", "wf_notification_channel"))
    );

    private static final List<ExampleMeta> EXAMPLES = List.of(
            new ExampleMeta(List.of("角色", "多少"), "系统有多少个角色",
                    "SELECT COUNT(*) AS roleCount FROM wf_role",
                    "metric", "角色总数"),
            new ExampleMeta(List.of("角色", "关联", "用户"), "帮我查询下系统角色有多少，关联的用户有多少",
                    """
                    SELECT
                      COUNT(DISTINCT r.id) AS roleCount,
                      COUNT(DISTINCT COALESCE(ur.user_id, up.user_id)) AS userCount
                    FROM wf_role r
                    LEFT JOIN wf_user_role ur ON ur.role_id = r.id
                    LEFT JOIN wf_user_post_role upr ON upr.role_id = r.id
                    LEFT JOIN wf_user_post up ON up.id = upr.user_post_id
                    """,
                    "stats", "角色总数与关联用户数"),
            new ExampleMeta(List.of("每个", "角色", "用户数量"), "要列出来每个角色对应的用户数量",
                    """
                    SELECT
                      r.role_name AS roleName,
                      COUNT(DISTINCT COALESCE(ur.user_id, up.user_id)) AS userCount
                    FROM wf_role r
                    LEFT JOIN wf_user_role ur ON ur.role_id = r.id
                    LEFT JOIN wf_user_post_role upr ON upr.role_id = r.id
                    LEFT JOIN wf_user_post up ON up.id = upr.user_post_id
                    GROUP BY r.role_name
                    ORDER BY userCount DESC, roleName ASC
                    LIMIT 20
                    """,
                    "bar", "按角色分组统计关联用户数"),
            new ExampleMeta(List.of("部门", "用户"), "按部门统计启用用户，图表展示",
                    """
                    SELECT
                      d.department_name AS departmentName,
                      COUNT(*) AS userCount
                    FROM wf_user u
                    INNER JOIN wf_department d ON d.id = u.active_department_id
                    WHERE u.enabled = TRUE
                    GROUP BY d.department_name
                    ORDER BY userCount DESC, departmentName ASC
                    LIMIT 20
                    """,
                    "bar", "按部门分组统计启用用户数量"),
            new ExampleMeta(List.of("停用", "用户"), "停用用户有几个",
                    "SELECT COUNT(*) AS disabledUserCount FROM wf_user WHERE enabled = FALSE",
                    "metric", "停用用户总数")
    );

    private final JdbcTemplate jdbcTemplate;
    private volatile SchemaSnapshot cachedSnapshot;

    @Autowired
    public AiStatsSchemaCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    AiStatsSchemaCatalogService(SchemaSnapshot snapshot) {
        this.jdbcTemplate = null;
        this.cachedSnapshot = snapshot;
    }

    public String buildPromptContext(String keyword, String domainHint) {
        SchemaSnapshot snapshot = schemaSnapshot();
        List<TableMeta> relevant = relevantTables(keyword, domainHint, snapshot);
        List<String> allowedTableNames = relevant.stream().map(TableMeta::table).toList();
        List<JoinMeta> relevantJoins = snapshot.joins().stream()
                .filter(join -> allowedTableNames.containsAll(join.tables()))
                .toList();
        List<ExampleMeta> examples = relevantExamples(keyword, domainHint);

        StringBuilder builder = new StringBuilder();
        builder.append("允许访问的表结构如下：\n");
        for (TableMeta table : relevant) {
            builder.append("- 表 ").append(table.table()).append("（").append(table.label()).append("）\n");
            builder.append("  业务别名：").append(String.join("、", table.aliases())).append('\n');
            for (ColumnMeta column : table.columns()) {
                builder.append("  - ").append(column.name())
                        .append("：").append(column.label())
                        .append(" [").append(column.type()).append("]\n");
            }
        }
        if (!relevantJoins.isEmpty()) {
            builder.append("可用关联关系：\n");
            for (JoinMeta join : relevantJoins) {
                builder.append("- ").append(join.expression()).append("（").append(join.label()).append("）\n");
            }
        }
        if (!examples.isEmpty()) {
            builder.append("典型问句与参考 SQL：\n");
            for (ExampleMeta example : examples) {
                builder.append("- 问句：").append(example.question()).append('\n');
                builder.append("  展示：").append(example.presentation()).append("（").append(example.note()).append("）\n");
                builder.append("  SQL：").append(example.sql().replace("\n", " ")).append('\n');
            }
        }
        builder.append("""
                额外约束：
                - 必须严格使用上面给出的真实字段名，不要臆造列名。
                - 只允许使用给出的表和关联关系，不允许臆造新的 join 路径。
                - 统计“角色关联用户”时，必须同时考虑 wf_user_role 和 wf_user_post_role + wf_user_post。
                - 如果问题要“每个角色/每个部门/每个公司”的分布，必须使用 GROUP BY。
                - 单值统计优先 metric；单行多指标优先 stats；列表/明细优先 table；分布或趋势优先图表。
                - 如果没有足够 schema 支撑当前问题，返回保守的只读查询，不要猜测不存在的表和字段。
                """);
        return builder.toString();
    }

    public Set<String> allowedTables(String keyword, String domainHint) {
        return new LinkedHashSet<>(relevantTables(keyword, domainHint, schemaSnapshot()).stream().map(TableMeta::table).toList());
    }

    private SchemaSnapshot schemaSnapshot() {
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }
        synchronized (this) {
            if (cachedSnapshot == null) {
                cachedSnapshot = loadSnapshot();
            }
            return cachedSnapshot;
        }
    }

    private SchemaSnapshot loadSnapshot() {
        if (jdbcTemplate == null) {
            return new SchemaSnapshot(List.of(), List.of());
        }
        List<String> allowedTableNames = new ArrayList<>(TABLE_CONFIGS.keySet());
        String placeholders = String.join(",", java.util.Collections.nCopies(allowedTableNames.size(), "?"));

        List<TableColumnRow> columnRows = jdbcTemplate.query(
                """
                SELECT table_name, column_name, data_type, ordinal_position
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN (%s)
                ORDER BY table_name, ordinal_position
                """.formatted(placeholders),
                (rs, rowNum) -> new TableColumnRow(
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getInt("ordinal_position")
                ),
                allowedTableNames.toArray()
        );

        Map<String, List<TableColumnRow>> columnsByTable = columnRows.stream()
                .collect(Collectors.groupingBy(TableColumnRow::tableName, LinkedHashMap::new, Collectors.toList()));

        List<TableMeta> tables = new ArrayList<>();
        for (String tableName : allowedTableNames) {
            TableConfig config = TABLE_CONFIGS.get(tableName);
            List<ColumnMeta> columns = columnsByTable.getOrDefault(tableName, List.of()).stream()
                    .sorted(Comparator.comparingInt(TableColumnRow::ordinalPosition))
                    .map(row -> new ColumnMeta(
                            row.columnName(),
                            inferColumnLabel(row.columnName(), config.label()),
                            row.dataType()
                    ))
                    .toList();
            if (!columns.isEmpty()) {
                tables.add(new TableMeta(tableName, config.label(), config.aliases(), columns));
            }
        }

        List<JoinMeta> databaseJoins = jdbcTemplate.query(
                """
                SELECT
                  tc.table_name AS source_table,
                  kcu.column_name AS source_column,
                  ccu.table_name AS target_table,
                  ccu.column_name AS target_column
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                ORDER BY tc.table_name, kcu.ordinal_position
                """,
                (rs, rowNum) -> new JoinMeta(
                        rs.getString("source_table") + "." + rs.getString("source_column")
                                + " = "
                                + rs.getString("target_table") + "." + rs.getString("target_column"),
                        TABLE_CONFIGS.getOrDefault(rs.getString("source_table"), new TableConfig(rs.getString("source_table"), rs.getString("source_table"), List.of()))
                                .label()
                                + " -> "
                                + TABLE_CONFIGS.getOrDefault(rs.getString("target_table"), new TableConfig(rs.getString("target_table"), rs.getString("target_table"), List.of()))
                                        .label(),
                        new LinkedHashSet<>(List.of(rs.getString("source_table"), rs.getString("target_table")))
                )
        ).stream().filter(join -> TABLE_CONFIGS.containsKey(join.tables().iterator().next())
                || join.tables().stream().anyMatch(TABLE_CONFIGS::containsKey))
                .toList();

        Map<String, JoinMeta> joinIndex = new LinkedHashMap<>();
        for (JoinMeta join : databaseJoins) {
            if (join.tables().stream().allMatch(TABLE_CONFIGS::containsKey)) {
                joinIndex.put(join.expression(), join);
            }
        }
        for (SupplementalJoin supplementalJoin : SUPPLEMENTAL_JOINS) {
            joinIndex.putIfAbsent(
                    supplementalJoin.expression(),
                    new JoinMeta(supplementalJoin.expression(), supplementalJoin.label(), supplementalJoin.tables())
            );
        }

        return new SchemaSnapshot(
                List.copyOf(tables),
                List.copyOf(joinIndex.values())
        );
    }

    private List<TableMeta> relevantTables(String keyword, String domainHint, SchemaSnapshot snapshot) {
        String normalized = normalize(keyword, domainHint);
        List<TableMeta> matched = new ArrayList<>();
        for (TableMeta table : snapshot.tables()) {
            if (table.aliases().stream().anyMatch(normalized::contains)) {
                matched.add(table);
            }
        }
        if (normalized.contains("角色") && normalized.contains("用户")) {
            addIfMissing(matched, "wf_role", snapshot);
            addIfMissing(matched, "wf_user", snapshot);
            addIfMissing(matched, "wf_user_role", snapshot);
            addIfMissing(matched, "wf_user_post", snapshot);
            addIfMissing(matched, "wf_user_post_role", snapshot);
        }
        if (normalized.contains("部门") && normalized.contains("用户")) {
            addIfMissing(matched, "wf_user", snapshot);
            addIfMissing(matched, "wf_department", snapshot);
            addIfMissing(matched, "wf_company", snapshot);
        }
        if (normalized.contains("岗位") && normalized.contains("用户")) {
            addIfMissing(matched, "wf_user", snapshot);
            addIfMissing(matched, "wf_post", snapshot);
            addIfMissing(matched, "wf_department", snapshot);
        }
        if (normalized.contains("通知")) {
            addIfMissing(matched, "wf_notification_template", snapshot);
            addIfMissing(matched, "wf_notification_channel", snapshot);
            addIfMissing(matched, "wf_notification_log", snapshot);
        }
        if (normalized.contains("日志")) {
            addIfMissing(matched, "wf_request_audit_log", snapshot);
            addIfMissing(matched, "wf_workflow_operation_log", snapshot);
        }
        if (normalized.contains("代理") || normalized.contains("委派") || normalized.contains("离职转办")) {
            addIfMissing(matched, "wf_delegation", snapshot);
        }
        if (normalized.contains("文件") || normalized.contains("附件")) {
            addIfMissing(matched, "wf_file", snapshot);
        }
        if (normalized.contains("监控") || normalized.contains("编排") || normalized.contains("触发")) {
            addIfMissing(matched, "wf_trigger_definition", snapshot);
            addIfMissing(matched, "wf_orchestrator_execution", snapshot);
        }
        if (normalized.contains("审批") || normalized.contains("申请") || normalized.contains("请假") || normalized.contains("报销")) {
            addIfMissing(matched, "wf_business_process_link", snapshot);
        }
        if (matched.isEmpty()) {
            addIfMissing(matched, "wf_user", snapshot);
            addIfMissing(matched, "wf_role", snapshot);
            addIfMissing(matched, "wf_company", snapshot);
            addIfMissing(matched, "wf_department", snapshot);
            addIfMissing(matched, "wf_post", snapshot);
            addIfMissing(matched, "wf_menu", snapshot);
        }
        return matched;
    }

    private List<ExampleMeta> relevantExamples(String keyword, String domainHint) {
        String normalized = normalize(keyword, domainHint);
        List<ExampleMeta> matched = EXAMPLES.stream()
                .filter(example -> example.matchTokens().stream().anyMatch(normalized::contains))
                .toList();
        if (!matched.isEmpty()) {
            return matched.size() > 4 ? matched.subList(0, 4) : matched;
        }
        return EXAMPLES.subList(0, Math.min(3, EXAMPLES.size()));
    }

    private String normalize(String keyword, String domainHint) {
        return ((keyword == null ? "" : keyword) + " " + (domainHint == null ? "" : domainHint))
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private void addIfMissing(List<TableMeta> metas, String tableName, SchemaSnapshot snapshot) {
        if (metas.stream().noneMatch(meta -> meta.table().equals(tableName))) {
            snapshot.tables().stream()
                    .filter(meta -> meta.table().equals(tableName))
                    .findFirst()
                    .ifPresent(metas::add);
        }
    }

    private String inferColumnLabel(String columnName, String tableLabel) {
        if ("id".equals(columnName) || columnName.endsWith("_id") || columnName.endsWith("id_")) {
            return columnName.replace("_", " ").replace("id", "ID");
        }
        if (columnName.endsWith("_name") || columnName.endsWith("name_")) {
            return tableLabel + "名称";
        }
        if (columnName.endsWith("_code")) {
            return tableLabel + "编码";
        }
        if (columnName.endsWith("created_at")) {
            return "创建时间";
        }
        if (columnName.endsWith("updated_at")) {
            return "更新时间";
        }
        if (columnName.endsWith("sent_at")) {
            return "发送时间";
        }
        if (columnName.endsWith("executed_at")) {
            return "执行时间";
        }
        if (columnName.endsWith("enabled")) {
            return "是否启用";
        }
        return columnName;
    }

    record SchemaSnapshot(List<TableMeta> tables, List<JoinMeta> joins) {}

    private record TableConfig(String table, String label, List<String> aliases) {}

    private record TableColumnRow(String tableName, String columnName, String dataType, int ordinalPosition) {}

    private record SupplementalJoin(String expression, String label, Set<String> tables) {}

    record TableMeta(String table, String label, List<String> aliases, List<ColumnMeta> columns) {}

    record ColumnMeta(String name, String label, String type) {}

    record JoinMeta(String expression, String label, Set<String> tables) {}

    private record ExampleMeta(
            List<String> matchTokens,
            String question,
            String sql,
            String presentation,
            String note
    ) {}
}
