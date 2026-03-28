package com.westflow.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.westflow.common.error.ContractException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiStatsText2SqlServiceTest {

    private static final AiStatsSchemaCatalogService.SchemaSnapshot SNAPSHOT =
            new AiStatsSchemaCatalogService.SchemaSnapshot(
                    List.of(
                            new AiStatsSchemaCatalogService.TableMeta(
                                    "wf_role",
                                    "角色",
                                    List.of("角色"),
                                    List.of(
                                            new AiStatsSchemaCatalogService.ColumnMeta("id", "角色ID", "text"),
                                            new AiStatsSchemaCatalogService.ColumnMeta("role_name", "角色名称", "text")
                                    )
                            ),
                            new AiStatsSchemaCatalogService.TableMeta(
                                    "wf_user_role",
                                    "用户角色关系",
                                    List.of("用户角色"),
                                    List.of(
                                            new AiStatsSchemaCatalogService.ColumnMeta("user_id", "用户ID", "text"),
                                            new AiStatsSchemaCatalogService.ColumnMeta("role_id", "角色ID", "text")
                                    )
                            ),
                            new AiStatsSchemaCatalogService.TableMeta(
                                    "wf_user_post_role",
                                    "任职角色关系",
                                    List.of("任职角色"),
                                    List.of(
                                            new AiStatsSchemaCatalogService.ColumnMeta("user_post_id", "任职ID", "text"),
                                            new AiStatsSchemaCatalogService.ColumnMeta("role_id", "角色ID", "text")
                                    )
                            ),
                            new AiStatsSchemaCatalogService.TableMeta(
                                    "wf_user_post",
                                    "用户任职",
                                    List.of("任职"),
                                    List.of(
                                            new AiStatsSchemaCatalogService.ColumnMeta("id", "任职ID", "text"),
                                            new AiStatsSchemaCatalogService.ColumnMeta("user_id", "用户ID", "text")
                                    )
                            )
                    ),
                    List.of()
            );

    @Test
    void shouldReturnMetricForRoleCountQuery() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        doReturn(new AiStatsSqlPlan(
                "角色数量统计",
                "SELECT COUNT(*) AS role_count FROM wf_role",
                "metric",
                null,
                null,
                "角色总数",
                "统计系统角色总数"
        )).when(generator).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doReturn(List.of(Map.of("role_count", 11L))).when(jdbcTemplate).queryForList("SELECT COUNT(*) AS role_count FROM wf_role");

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "系统有多少个角色"));
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) result.get("chart");

        assertThat(result).containsEntry("scope", "text2sql.stats");
        assertThat(result.get("title")).isEqualTo("角色数量统计");
        assertThat(chart).containsEntry("type", "metric");
        verify(jdbcTemplate).queryForList("SELECT COUNT(*) AS role_count FROM wf_role");
    }

    @Test
    void shouldReturnBarForRoleUserDistributionQuery() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        doReturn(new AiStatsSqlPlan(
                "角色关联用户分布",
                """
                SELECT r.role_name AS roleName, COUNT(DISTINCT COALESCE(ur.user_id, up.user_id)) AS userCount
                FROM wf_role r
                LEFT JOIN wf_user_role ur ON ur.role_id = r.id
                LEFT JOIN wf_user_post_role upr ON upr.role_id = r.id
                LEFT JOIN wf_user_post up ON up.id = upr.user_post_id
                GROUP BY r.role_name
                ORDER BY userCount DESC
                LIMIT 20
                """,
                "bar",
                "roleName",
                "userCount",
                "关联用户数",
                "展示每个角色关联的用户数量"
        )).when(generator).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doReturn(List.of(
                Map.of("roleName", "平台管理员", "userCount", 3L),
                Map.of("roleName", "部门经理", "userCount", 2L)
        )).when(jdbcTemplate).queryForList(org.mockito.ArgumentMatchers.anyString());

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "每个角色对应的用户数量"));
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) result.get("chart");

        assertThat(result.get("title")).isEqualTo("角色关联用户分布");
        assertThat(chart).containsEntry("type", "bar");
        assertThat(chart).containsEntry("xField", "roleName");
        assertThat(chart).containsEntry("yField", "userCount");
    }

    @Test
    void shouldReturnStatsForSingleRowMultiMetricQuery() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        doReturn(new AiStatsSqlPlan(
                "角色关联概览",
                """
                SELECT
                  COUNT(DISTINCT r.id) AS roleCount,
                  COUNT(DISTINCT COALESCE(ur.user_id, up.user_id)) AS userCount
                FROM wf_role r
                LEFT JOIN wf_user_role ur ON ur.role_id = r.id
                LEFT JOIN wf_user_post_role upr ON upr.role_id = r.id
                LEFT JOIN wf_user_post up ON up.id = upr.user_post_id
                """,
                "stats",
                null,
                null,
                null,
                "角色总数与关联用户数"
        )).when(generator).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doReturn(List.of(Map.of("roleCount", 11L, "userCount", 13L)))
                .when(jdbcTemplate)
                .queryForList(org.mockito.ArgumentMatchers.anyString());

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "系统角色有多少，关联的用户有多少"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) result.get("metrics");
        assertThat(result.get("summary")).isEqualTo("角色总数与关联用户数");
        assertThat(metrics)
                .extracting(metric -> metric.get("label") + ":" + metric.get("value"))
                .contains("角色总数:11", "用户总数:13");
    }

    @Test
    void shouldReturnFailureResultWhenSqlGenerationFails() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        org.mockito.Mockito.doThrow(new RuntimeException("EOF"))
                .when(generator)
                .generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "按部门统计用户"));

        assertThat(result).containsEntry("scope", "text2sql.stats");
        assertThat(result).containsEntry("error", true);
        assertThat(String.valueOf(result.get("summary"))).contains("统计查询");
    }

    @Test
    void shouldRejectNonSelectSql() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        doReturn(new AiStatsSqlPlan(
                "非法",
                "UPDATE wf_role SET enabled = FALSE",
                "table",
                null,
                null,
                null,
                null
        )).when(generator).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "系统有多少个角色"));

        assertThat(result).containsEntry("error", true);
        assertThat(String.valueOf(result.get("summary"))).contains("只允许只读查询");
    }

    @Test
    void shouldResolveChartFieldCaseInsensitive() {
        AiStatsSqlGenerator generator = mock(AiStatsSqlGenerator.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiStatsSchemaCatalogService catalogService = new AiStatsSchemaCatalogService(SNAPSHOT);
        doReturn(new AiStatsSqlPlan(
                "角色关联用户分布",
                "SELECT r.role_name AS roleName, COUNT(*) AS userCount FROM wf_role r GROUP BY r.role_name LIMIT 20",
                "bar",
                "roleName",
                "userCount",
                "关联用户数",
                "展示每个角色关联的用户数量"
        )).when(generator).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doReturn(List.of(
                Map.of("rolename", "平台管理员", "usercount", 3L)
        )).when(jdbcTemplate).queryForList(org.mockito.ArgumentMatchers.anyString());

        AiStatsText2SqlService service = new AiStatsText2SqlService(generator, catalogService, jdbcTemplate);

        Map<String, Object> result = service.query(Map.of("keyword", "每个角色对应的用户数量"));
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) result.get("chart");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) result.get("metrics");

        assertThat(chart).containsEntry("xField", "rolename");
        assertThat(chart).containsEntry("yField", "usercount");
        assertThat(metrics).isEmpty();
    }
}
