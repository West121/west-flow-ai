package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.agent.AiAgentDescriptor;
import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.mapper.AiAuditMapper;
import com.westflow.ai.mapper.AiConfirmationMapper;
import com.westflow.ai.mapper.AiConversationMapper;
import com.westflow.ai.mapper.AiMessageMapper;
import com.westflow.ai.mapper.AiToolCallMapper;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConversationDetailResponse;
import com.westflow.ai.model.AiConversationRecord;
import com.westflow.ai.model.AiMessageBlockResponse;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiMessageResponse;
import com.westflow.ai.model.AiMessageRecord;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallRecord;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.executor.AiActionExecutor;
import com.westflow.ai.executor.AiExecutionRouter;
import com.westflow.ai.executor.AiKnowledgeExecutor;
import com.westflow.ai.executor.AiMcpExecutor;
import com.westflow.ai.executor.AiStatsExecutor;
import com.westflow.ai.executor.AiWorkflowExecutor;
import com.westflow.ai.planner.AiPlanAgentService;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.runtime.SpringAiAlibabaCopilotRuntimeService;
import com.westflow.ai.skill.AiSkillDescriptor;
import com.westflow.ai.skill.AiSkillRegistry;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.common.error.ContractException;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

/**
 * AI Copilot 服务层测试。
 */
@ExtendWith(MockitoExtension.class)
class AiCopilotServiceTest {

    @Mock
    private AiConversationMapper aiConversationMapper;

    @Mock
    private AiMessageMapper aiMessageMapper;

    @Mock
    private AiToolCallMapper aiToolCallMapper;

    @Mock
    private AiConfirmationMapper aiConfirmationMapper;

    @Mock
    private AiAuditMapper aiAuditMapper;

    @Mock
    private ProcessDefinitionService processDefinitionService;

    private AiCopilotService aiCopilotService;
    private AtomicBoolean confirmedWriteExecuted;
    private ObjectMapper objectMapper;
    private AiRegistryCatalogService aiRegistryCatalogService;
    private AiGatewayService aiGatewayService;
    private AiToolExecutionService aiToolExecutionService;
    private AiCopilotRuntimeService runtimeService;
    private AiExecutionRouter aiExecutionRouter;
    private AiAgentRegistry aiAgentRegistry;
    private AiSkillRegistry aiSkillRegistry;
    private AiUserReferenceResolver aiUserReferenceResolver;

    @BeforeEach
    void setUp() {
        confirmedWriteExecuted = new AtomicBoolean(false);
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        objectMapper = new ObjectMapper();
        aiAgentRegistry = new AiAgentRegistry(List.of(
                new AiAgentDescriptor("supervisor", "Supervisor", "SUPERVISOR", List.of("OA", "PLM", "GENERAL"), true, 100),
                new AiAgentDescriptor("routing", "Routing", "ROUTING", List.of("OA", "PLM", "GENERAL"), false, 80)
        ));
        aiSkillRegistry = new AiSkillRegistry(List.of(
                new AiSkillDescriptor("approval-trace", "审批轨迹解释", List.of("OA", "PLM"), List.of("轨迹", "路径"), false, 90),
                new AiSkillDescriptor("plm-change-summary", "PLM 变更摘要", List.of("PLM"), List.of("PLM", "ECR", "ECO"), false, 80)
        ));
        AiToolRegistry aiToolRegistry = new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "task.query",
                        AiToolSource.PLATFORM,
                        "已返回待办列表",
                        context -> {
                            String view = String.valueOf(context.request().arguments().getOrDefault("view", "TODO"));
                            if ("INITIATED".equalsIgnoreCase(view)) {
                                return Map.of(
                                        "count", 2,
                                        "items", List.of(
                                                Map.of(
                                                        "instanceId", "proc_001",
                                                        "businessTitle", "请假申请A",
                                                        "billNo", "LEAVE-001",
                                                        "currentNodeName", "部门经理审批",
                                                        "currentTaskId", "task_init_001",
                                                        "instanceStatus", "RUNNING",
                                                        "createdAt", today + "T09:00:00+08:00"
                                                ),
                                                Map.of(
                                                        "instanceId", "proc_002",
                                                        "businessTitle", "请假申请B",
                                                        "billNo", "LEAVE-002",
                                                        "currentNodeName", "负责人确认",
                                                        "currentTaskId", "task_init_002",
                                                        "instanceStatus", "RUNNING",
                                                        "createdAt", today + "T11:30:00+08:00"
                                                )
                                        )
                                );
                            }
                            return Map.of("count", 1, "items", List.of("task_001"));
                        }
                ),
                AiToolDefinition.read(
                        "workflow.todo.list",
                        AiToolSource.PLATFORM,
                        "已返回待办列表",
                        context -> Map.of("count", 1, "items", List.of("task_001"))
                ),
                AiToolDefinition.read(
                        "workflow.trace.summary",
                        AiToolSource.SKILL,
                        "已返回审批轨迹摘要",
                        context -> Map.of("summary", "当前审批轨迹正常")
                ),
                AiToolDefinition.read(
                        "stats.query",
                        AiToolSource.PLATFORM,
                        "已返回统计摘要",
                        context -> {
                            String keyword = String.valueOf(context.request().arguments().getOrDefault("keyword", ""));
                            if (keyword.contains("角色") && keyword.contains("用户") && keyword.contains("关联")) {
                                return Map.of(
                                        "scope", "role.user.association",
                                        "title", "角色关联概览",
                                        "summary", "系统当前共有 13 个角色，关联用户共 13 人。",
                                        "metrics", List.of(
                                                Map.of("label", "角色总数", "value", 13, "tone", "positive"),
                                                Map.of("label", "关联用户数", "value", 13, "tone", "neutral"),
                                                Map.of("label", "统计范围", "value", "全部角色", "tone", "neutral")
                                        ),
                                        "data", List.of(
                                                Map.of("label", "角色总数", "value", 13),
                                                Map.of("label", "关联用户数", "value", 13)
                                        )
                                );
                            }
                            if (keyword.contains("停用") || keyword.contains("禁用")) {
                                return Map.of(
                                        "scope", "user.disabled.count",
                                        "title", "停用用户统计",
                                        "summary", "系统当前共有 0 名停用用户。",
                                        "metrics", List.of(
                                                Map.of("label", "停用用户数", "value", 0, "tone", "neutral"),
                                                Map.of("label", "统计范围", "value", "全部用户", "tone", "neutral")
                                        ),
                                        "chart", Map.of(
                                                "type", "metric",
                                                "title", "停用用户统计",
                                                "description", "展示当前系统停用用户总数。",
                                                "metricLabel", "停用用户数",
                                                "valueLabel", "全部用户",
                                                "value", 0
                                        ),
                                        "data", List.of(Map.of("label", "全部用户", "value", 0))
                                );
                            }
                            if (keyword.contains("每个角色") || keyword.contains("角色对应")) {
                                return Map.of(
                                        "scope", "role.user.association.byRole",
                                        "title", "角色关联用户分布",
                                        "summary", "当前共 13 个角色，关联用户共 13 人。主要角色分布为 平台管理员（3）、部门经理（2）。",
                                        "metrics", List.of(
                                                Map.of("label", "角色总数", "value", 13, "tone", "positive"),
                                                Map.of("label", "关联用户数", "value", 13, "tone", "neutral"),
                                                Map.of("label", "最多用户角色", "value", "平台管理员", "tone", "warning")
                                        ),
                                        "chart", Map.of(
                                                "type", "bar",
                                                "title", "角色关联用户分布",
                                                "description", "展示每个角色当前关联的用户数量。",
                                                "xField", "roleName",
                                                "yField", "userCount",
                                                "series", List.of(Map.of("dataKey", "userCount", "name", "关联用户数"))
                                        ),
                                        "data", List.of(
                                                Map.of("roleName", "平台管理员", "userCount", 3),
                                                Map.of("roleName", "部门经理", "userCount", 2)
                                        )
                                );
                            }
                            if (keyword.contains("角色")) {
                                return Map.of(
                                        "scope", "role.count",
                                        "title", "角色数量统计",
                                        "summary", "系统当前共有 13 个角色。",
                                        "metrics", List.of(
                                                Map.of("label", "角色总数", "value", 13, "tone", "positive"),
                                                Map.of("label", "统计范围", "value", "全部角色", "tone", "neutral")
                                        ),
                                        "chart", Map.of(
                                                "type", "metric",
                                                "title", "角色数量统计",
                                                "description", "展示当前系统角色总数。",
                                                "metricLabel", "角色总数",
                                                "valueLabel", "全部角色",
                                                "value", 13
                                        ),
                                        "data", List.of(Map.of("label", "全部角色", "value", 13))
                                );
                            }
                            if (keyword.contains("菜单")) {
                                return Map.of(
                                        "scope", "menu.count",
                                        "title", "菜单数量统计",
                                        "summary", "系统当前共有 28 个菜单。",
                                        "metrics", List.of(
                                                Map.of("label", "菜单总数", "value", 28, "tone", "positive"),
                                                Map.of("label", "统计范围", "value", "全部菜单", "tone", "neutral")
                                        ),
                                        "chart", Map.of(
                                                "type", "metric",
                                                "title", "菜单数量统计",
                                                "description", "展示当前系统菜单总数。",
                                                "metricLabel", "菜单总数",
                                                "valueLabel", "全部菜单",
                                                "value", 28
                                        ),
                                        "data", List.of(Map.of("label", "全部菜单", "value", 28))
                                );
                            }
                            return Map.of(
                                    "scope", "user.department",
                                    "title", "按部门统计用户",
                                    "summary", "当前共 13 名启用用户，主要集中在 PLM产品组（4）和人力资源部（3）。",
                                    "metrics", List.of(
                                            Map.of("label", "用户总数", "value", 13, "tone", "positive"),
                                            Map.of("label", "部门数", "value", 5, "tone", "neutral"),
                                            Map.of("label", "最多用户部门", "value", "PLM产品组", "tone", "warning")
                                    ),
                                    "chart", Map.of(
                                            "type", "bar",
                                            "title", "部门用户分布",
                                            "description", "按部门统计当前启用用户数量。",
                                            "xField", "departmentName",
                                            "yField", "userCount",
                                            "series", List.of(Map.of("dataKey", "userCount", "name", "用户数"))
                                    ),
                                    "data", List.of(
                                            Map.of("departmentName", "PLM产品组", "userCount", 4),
                                            Map.of("departmentName", "人力资源部", "userCount", 3),
                                            Map.of("departmentName", "财务部", "userCount", 2)
                                    )
                            );
                        }
                ),
                AiToolDefinition.read(
                        "feature.catalog.query",
                        AiToolSource.PLATFORM,
                        "已返回功能目录",
                        context -> {
                            String keyword = String.valueOf(context.request().arguments().getOrDefault("keyword", ""));
                            String pageRoute = String.valueOf(context.request().arguments().getOrDefault("pageRoute", "/"));
                            List<Map<String, Object>> pageFeatures = pageRoute.startsWith("/workbench/")
                                    ? List.of(
                                            Map.of(
                                                    "code", "collaboration",
                                                    "title", "审批协同",
                                                    "summary", "支持会办、阅办、传阅、督办和批注提醒，适合多人共同处理审批事项。",
                                                    "keywords", List.of("协同", "会办", "阅办", "传阅", "督办")
                                            )
                                    )
                                    : List.of();
                            List<Map<String, Object>> highlights = keyword.contains("协同")
                                    ? pageFeatures
                                    : List.of();
                            return Map.of(
                                    "title", "当前页面功能目录",
                                    "summary", "当前页面可用 2 个工具、1 个技能、1 个 MCP。",
                                    "keyword", keyword,
                                    "domain", String.valueOf(context.request().arguments().getOrDefault("domain", "GENERAL")),
                                    "pageRoute", pageRoute,
                                    "pageFeatures", pageFeatures,
                                    "highlights", highlights,
                                    "tools", List.of(
                                            Map.of("toolCode", "stats.query", "toolName", "查询统计", "summary", "适合数量统计与图表分析。"),
                                            Map.of("toolCode", "task.query", "toolName", "查询待办", "summary", "适合查看待办、已发起和审批进度。")
                                    ),
                                    "skills", List.of(
                                            Map.of("skillCode", "system-feature-skill", "skillName", "系统功能说明", "summary", "负责解释当前页面功能用途和使用方式。")
                                    ),
                                    "mcps", List.of(
                                            Map.of("mcpCode", "westflow-internal-mcp", "mcpName", "内部 MCP")
                                    )
                            );
                        }
                ),
                AiToolDefinition.read(
                        "user.profile.query",
                        AiToolSource.PLATFORM,
                        "已返回用户资料",
                        context -> {
                            String requestedUserId = String.valueOf(context.request().arguments().getOrDefault("userId", ""));
                            String requestedUserDisplayName = String.valueOf(context.request().arguments().getOrDefault("targetUserDisplayName", ""));
                            if ("usr_001".equals(requestedUserId)) {
                                return Map.of(
                                        "found", true,
                                        "requestedUserId", "usr_001",
                                        "requestedUserDisplayName", requestedUserDisplayName,
                                        "displayName", "张三",
                                        "departmentName", "PLM 产品组",
                                        "postName", "产品经理",
                                        "companyName", "西流科技",
                                        "mobile", "13800138000",
                                        "email", "zhangsan@example.com",
                                        "primaryRoleNames", List.of("平台管理员")
                                );
                            }
                            if ("usr_002".equals(requestedUserId)) {
                                return Map.of(
                                        "found", true,
                                        "requestedUserId", "usr_002",
                                        "requestedUserDisplayName", requestedUserDisplayName,
                                        "displayName", "李四",
                                        "departmentName", "人力资源部",
                                        "postName", "人力资源专员",
                                        "companyName", "西流科技",
                                        "mobile", "13800138001",
                                        "email", "lisi@example.com",
                                        "primaryRoleNames", List.of("部门经理")
                                );
                            }
                            return Map.of(
                                    "found", false,
                                    "requestedUserId", "",
                                    "requestedUserDisplayName", requestedUserDisplayName
                            );
                        }
                ),
                AiToolDefinition.read(
                        "plm.bill.query",
                        AiToolSource.PLATFORM,
                        "已返回 PLM 单据列表",
                        context -> plmBillV4Result()
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "已返回 PLM 变更摘要",
                        context -> plmBillV4Result()
                ),
                AiToolDefinition.read(
                        "plm.project.query",
                        AiToolSource.PLATFORM,
                        "已返回 PLM 项目列表",
                        context -> plmProjectResult()
                ),
                AiToolDefinition.read(
                        "plm.project.summary",
                        AiToolSource.SKILL,
                        "已返回 PLM 项目摘要",
                        context -> plmProjectResult()
                ),
                AiToolDefinition.write(
                        "process.start",
                        AiToolSource.PLATFORM,
                        "请确认是否发起流程",
                        context -> Map.of(
                                "accepted", true,
                                "done", true,
                                "instanceId", "proc_start_001",
                                "billNo", "OA-LEAVE-20260323-001",
                                "status", "STARTED",
                                "activeTasks", List.of(
                                        Map.of("taskId", "task_start_001", "nodeName", "直属主管审批")
                                )
                        )
                ),
                AiToolDefinition.write(
                        "task.handle",
                        AiToolSource.PLATFORM,
                        "请确认是否处理当前待办",
                        context -> {
                            confirmedWriteExecuted.set(true);
                            return Map.of(
                                    "accepted", true,
                                    "done", true,
                                    "instanceId", "proc_001",
                                    "completedTaskId", "task_001",
                                    "status", "COMPLETED",
                                    "nextTasks", List.of(
                                            Map.of("taskId", "task_002", "nodeName", "研发负责人审批")
                                    )
                            );
                        }
                ),
                AiToolDefinition.write(
                        "workflow.task.complete",
                        AiToolSource.AGENT,
                        "请确认是否完成当前待办",
                        context -> Map.of("accepted", true, "done", true)
                ),
                AiToolDefinition.write(
                        "workflow.task.fail",
                        AiToolSource.PLATFORM,
                        "请确认是否执行失败示例",
                        context -> {
                            throw new IllegalStateException("执行失败");
                        }
                )
        ));
        ChatClient chatClient = org.mockito.Mockito.mock(ChatClient.class);
        runtimeService = new SpringAiAlibabaCopilotRuntimeService(
                chatClient,
                mockSupervisorAgent("supervisor-reply"),
                mockRoutingAgent("routing-reply")
        );
        aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
        lenient().when(aiRegistryCatalogService.matchReadTool(anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String content = invocation.getArgument(1, String.class);
                    if (content != null && content.contains("轨迹")) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "task.query",
                                "查询待办",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("OA", "PLM"),
                                List.of("轨迹"),
                                List.of("/workbench/"),
                                "westflow-internal-mcp",
                                "",
                                95,
                                Map.of()
                        ));
                    }
                    if (content != null && (content.contains("待办") || content.contains("路径"))) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "task.query",
                                "查询待办",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("OA", "PLM"),
                                List.of("待办", "路径"),
                                List.of("/workbench/"),
                                "westflow-internal-mcp",
                                "",
                                95,
                                Map.of()
                        ));
                    }
                    if (content != null && content.contains("统计")) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "stats.query",
                                "查询统计",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:stats:query",
                                List.of("OA", "PLM", "GENERAL"),
                                List.of("统计"),
                                List.of("/system/", "/workflow/"),
                                "westflow-internal-mcp",
                                "",
                                90,
                                Map.of()
                        ));
                    }
                    if (content != null && (content.contains("PLM") || content.contains("ECR") || content.contains("ECO") || content.contains("物料"))) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                content.contains("摘要") ? "plm.change.summary" : "plm.bill.query",
                                "查询 PLM 单据",
                                content.contains("摘要") ? AiToolSource.SKILL : AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("PLM"),
                                List.of("PLM", "ECR", "ECO", "物料"),
                                List.of("/plm/"),
                                "plm-change-summary",
                                "",
                                88,
                                Map.of()
                        ));
                    }
                    if (content != null && (content.contains("项目") || content.contains("里程碑") || content.contains("交付"))) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                content.contains("摘要") || content.contains("总结") ? "plm.project.summary" : "plm.project.query",
                                "查询 PLM 项目",
                                content.contains("摘要") || content.contains("总结") ? AiToolSource.SKILL : AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("PLM"),
                                List.of("项目", "里程碑", "交付"),
                                List.of("/plm/projects"),
                                "plm-project-summary",
                                "",
                                94,
                                Map.of()
                        ));
                    }
                    return java.util.Optional.empty();
                });
        lenient().when(aiRegistryCatalogService.findTool(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String toolKey = invocation.getArgument(1, String.class);
                    return switch (toolKey) {
                        case "task.query", "workflow.todo.list", "plm.bill.query", "plm.project.query" -> java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                toolKey,
                                toolKey,
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "",
                                List.of("OA", "PLM", "SYSTEM", "GENERAL"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                90,
                                Map.of()
                        ));
                        case "stats.query", "feature.catalog.query", "user.profile.query", "workflow.trace.summary", "plm.change.summary", "plm.project.summary" -> java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                toolKey,
                                toolKey,
                                "workflow.trace.summary".equals(toolKey) || "plm.change.summary".equals(toolKey) || "plm.project.summary".equals(toolKey)
                                        ? AiToolSource.SKILL
                                        : AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "",
                                List.of("OA", "PLM", "SYSTEM", "GENERAL"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                90,
                                Map.of()
                        ));
                        case "process.start", "task.handle", "workflow.task.complete", "workflow.task.reject", "workflow.task.fail" -> java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                toolKey,
                                toolKey,
                                switch (toolKey) {
                                    case "workflow.task.complete", "workflow.task.reject" -> AiToolSource.AGENT;
                                    case "workflow.task.fail" -> AiToolSource.PLATFORM;
                                    default -> AiToolSource.PLATFORM;
                                },
                                AiToolType.WRITE,
                                "",
                                List.of("OA", "PLM", "SYSTEM", "GENERAL"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                90,
                                Map.of()
                        ));
                        default -> java.util.Optional.empty();
                    };
                });
        lenient().when(aiRegistryCatalogService.matchSkills(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String content = invocation.getArgument(1, String.class);
                    if (content != null && (content.contains("功能") || content.contains("使用") || content.contains("怎么"))) {
                        return List.of(new AiRegistryCatalogService.AiSkillCatalogItem(
                                "system-feature-skill",
                                "系统功能说明",
                                "",
                                List.of("GENERAL", "SYSTEM"),
                                List.of("功能", "使用", "怎么"),
                                false,
                                90,
                                "/skills/system",
                                "系统功能说明内容",
                                Map.of()
                        ));
                    }
                    return List.of();
                });
        lenient().when(aiRegistryCatalogService.listSkillsForDomain(anyString(), anyString())).thenReturn(List.of());
        lenient().when(aiRegistryCatalogService.listReadableTools(anyString(), anyString())).thenReturn(List.of());
        lenient().when(aiRegistryCatalogService.listMcps(anyString(), anyString())).thenReturn(List.of());
        lenient().when(processDefinitionService.getLatestByProcessKey(anyString()))
                .thenAnswer(invocation -> publishedProcessDefinition(invocation.getArgument(0, String.class)));
        aiUserReferenceResolver = mock(AiUserReferenceResolver.class);
        lenient().when(aiUserReferenceResolver.resolveManagerUserId("帮我发起3天的事假给张三")).thenReturn("usr_001");
        lenient().when(aiUserReferenceResolver.resolveTodoTargetUserId("张三目前有几个待办")).thenReturn("usr_003");
        lenient().when(aiUserReferenceResolver.resolveTodoTargetDisplayName("张三目前有几个待办")).thenReturn("张三");
        lenient().when(aiUserReferenceResolver.resolveProfileTargetUserId("张三是什么部门 岗位", "usr_001")).thenReturn("usr_001");
        lenient().when(aiUserReferenceResolver.resolveProfileTargetDisplayName("张三是什么部门 岗位")).thenReturn("张三");
        lenient().when(aiUserReferenceResolver.resolveProfileTargetUserId("李四是什么部门 岗位", "usr_001")).thenReturn("usr_002");
        lenient().when(aiUserReferenceResolver.resolveProfileTargetDisplayName("李四是什么部门 岗位")).thenReturn("李四");
        lenient().when(aiUserReferenceResolver.resolveFollowUpDisplayName("李四呢")).thenReturn("李四");
        AiPlanAgentService planAgentService = new AiPlanAgentService(prompt -> "", objectMapper, aiRegistryCatalogService, aiUserReferenceResolver);
        aiExecutionRouter = new AiExecutionRouter(List.of(
                new AiKnowledgeExecutor(),
                new AiWorkflowExecutor(),
                new AiStatsExecutor(),
                new AiActionExecutor(),
                new AiMcpExecutor()
        ));
        aiGatewayService = new AiGatewayService(new AiOrchestrationPlanner(aiAgentRegistry, aiSkillRegistry));
        aiToolExecutionService = new AiToolExecutionService(aiToolRegistry, aiRegistryCatalogService);
        aiCopilotService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                objectMapper,
                aiGatewayService,
                aiToolExecutionService,
                runtimeService,
                aiRegistryCatalogService,
                processDefinitionService,
                planAgentService,
                aiExecutionRouter,
                null,
                aiUserReferenceResolver
        ) {
            @Override
            protected String currentUserId() {
                return "usr_001";
            }
        };
    }

    @Test
    void shouldDirectExecuteReadToolCall() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse result = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest("task.query", AiToolType.READ, AiToolSource.PLATFORM, Map.of("keyword", "请假"))
        );

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.requiresConfirmation()).isFalse();
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiAuditMapper).insertAudit(any());
    }

    @Test
    void shouldStageWriteToolCallForConfirmationAndConfirmIt() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        when(aiMessageMapper.countByConversationId("conv_001")).thenReturn(0L);

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "task.handle",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "taskId", "task_001",
                                "action", "COMPLETE",
                                "comment", "同意执行",
                                "domain", "PLM",
                                "routePath", "/plm/ecr/ecr_001",
                                "businessType", "PLM_ECR",
                                "businessId", "ecr_001",
                                "billNo", "ECR-20260323-001",
                                "businessTitle", "电机外壳 BOM 变更"
                        )
                )
        );
        assertThat(pending.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(pending.requiresConfirmation()).isTrue();

        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "task.handle",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"taskId\":\"task_001\",\"action\":\"COMPLETE\",\"comment\":\"同意执行\",\"domain\":\"PLM\",\"routePath\":\"/plm/ecr/ecr_001\",\"businessType\":\"PLM_ECR\",\"businessId\":\"ecr_001\",\"billNo\":\"ECR-20260323-001\",\"businessTitle\":\"电机外壳 BOM 变更\"}",
                "{}",
                "请确认是否处理当前待办",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, "确认执行", Map.of())
        );

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.result()).containsEntry("done", true);
        assertThat(confirmedWriteExecuted.get()).isTrue();
        assertThat(storedMessages).isNotEmpty();
        List<AiMessageBlockResponse> blocks = parseBlocks(storedMessages.get(storedMessages.size() - 1));
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "待办动作", "动作语义", "业务类型", "业务标识", "业务单据", "业务标题", "来源页面", "后续任务", "下一步建议", "处理意见");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("动作", "动作语义", "后续待办数", "执行状态", "业务域");
        AiMessageBlockResponse confirmBlock = blocks.stream()
                .filter(block -> "confirm".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(confirmBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("工具名称", "工具类型", "确认人", "确认意见", "待办动作", "动作语义", "业务类型", "业务标识", "业务单据", "业务标题");
        verify(aiConfirmationMapper).insertConfirmation(any());
        verify(aiAuditMapper, times(2)).insertAudit(any());
    }

    @Test
    void shouldUseFeatureCatalogToolForKnowledgeQuestions() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/users/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("当前页面适合做什么")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("当前页面共整理出 2 个可用工具、1 个可用技能");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .containsExactly("text", "trace", "result");
        AiMessageBlockResponse resultBlock = assistantMessage.blocks().stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.sourceKey()).isEqualTo("feature.catalog.query");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("工具数", "技能数", "MCP 数");
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldAppendWriteIntentMessageAndStageConfirmationCard() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "发起", "route:/oa/leave/create"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("请直接发起一个5天的请假")
        );

        assertThat(detail.conversationId()).isEqualTo("conv_001");
        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .containsExactly("form-preview", "confirm");
        AiMessageBlockResponse confirmBlock = blocks.stream()
                .filter(block -> "confirm".equals(block.type()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse previewBlock = blocks.stream()
                .filter(block -> "form-preview".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(confirmBlock.sourceType()).isEqualTo("PLATFORM");
        assertThat(confirmBlock.sourceKey()).isEqualTo("process.start");
        assertThat(confirmBlock.toolType()).isEqualTo("WRITE");
        assertThat(confirmBlock.result())
                .containsKeys("toolCallId", "confirmationId", "status", "requiresConfirmation", "domain", "routePath", "arguments");
        assertThat(confirmBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务域", "来源页面", "工具名称", "工具类型", "确认单编号");
        assertThat(previewBlock.fields()).isEmpty();
        assertThat(previewBlock.metrics()).isEmpty();
        assertThat(previewBlock.trace())
                .extracting(AiMessageBlockResponse.TraceStep::label)
                .contains("规划耗时", "执行器耗时", "生成耗时");
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiAuditMapper, times(2)).insertAudit(any());
    }

    @Test
    void shouldAppendTaskHandlePreviewInsteadOfProcessStartPreview() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "待办", "route:/workbench/todos/task_001"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我认领这个待办")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        AiMessageBlockResponse previewBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse confirmBlock = blocks.stream()
                .filter(block -> "confirm".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(blocks).extracting(AiMessageBlockResponse::type).doesNotContain("form-preview");
        assertThat(previewBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "拟执行动作", "工具调用编号", "确认单编号", "状态");
        assertThat(confirmBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "拟执行动作");
    }

    @Test
    void shouldAppendTaskHandlePreviewWithBusinessContextAndActionSemantic() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("PLM", "审批", "route:/plm/ecr/ecr_001"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("请通过这个 ECR 审批")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        AiMessageBlockResponse previewBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()) && "待办处理预览已生成，确认后才会执行真实审批动作。".equals(block.summary()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse confirmBlock = blocks.stream()
                .filter(block -> "confirm".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(previewBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务域", "来源页面", "待办编号", "拟执行动作", "业务类型", "业务标识", "动作语义");
        assertThat(confirmBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务类型", "业务标识", "拟执行动作", "动作语义");
        assertThat(confirmBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("确认状态", "动作类型", "动作语义");
    }

    @Test
    void shouldInferLeaveProcessStartFromListRoute() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/oa/leave/list"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我发起一个5天的请假，原因是家里有事，直属负责人选李四")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.role()).isEqualTo("assistant");
        assertThat(assistantMessage.content()).isEqualTo("我已经整理出建议动作，确认后才会真正执行写操作。");
        AiMessageBlockResponse previewBlock = assistantMessage.blocks().stream()
                .filter(block -> "form-preview".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(previewBlock.result())
                .containsEntry("processKey", "oa_leave")
                .containsEntry("businessType", "OA_LEAVE");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) previewBlock.result().get("formData");
        assertThat(formData)
                .containsEntry("days", 5)
                .containsEntry("reason", "家里有事")
                .containsEntry("managerUserId", "usr_002");
    }

    @Test
    void shouldPreferStructuredLeaveReasonFromAttachmentRecognitionContext() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/oa/leave/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("""
                        帮我发起对应申请

                        附件识别结果：
                        附件《leave-request-test-zh-clean.png》识别结果：
                        识别文本：
                        1. 请假申请单
                        2. 申请人：张三
                        - 请假类型：事假
                        - 请假天数：5 天
                        - 请假原因：家中有事，需要请 5 天事假处理个人事务。
                        """)
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        AiMessageBlockResponse previewBlock = assistantMessage.blocks().stream()
                .filter(block -> "form-preview".equals(block.type()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) previewBlock.result().get("formData");
        assertThat(formData)
                .containsEntry("days", 5)
                .containsEntry("leaveType", "PERSONAL")
                .containsEntry("reason", "家中有事，需要请 5 天事假处理个人事务。");
    }

    @Test
    void shouldInferLeaveProcessStartFromLeaveAliasKeywords() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我发起一个5天的事假")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        AiMessageBlockResponse previewBlock = assistantMessage.blocks().stream()
                .filter(block -> "form-preview".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(previewBlock.result())
                .containsEntry("processKey", "oa_leave")
                .containsEntry("businessType", "OA_LEAVE")
                .containsEntry("processFormKey", "oa-leave-start-form")
                .containsEntry("processFormVersion", "1.1.0");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) previewBlock.result().get("formData");
        assertThat(formData)
                .containsEntry("days", 5)
                .containsEntry("leaveType", "PERSONAL")
                .containsEntry("managerUserId", "usr_002");
    }

    @Test
    void shouldInferLeaveProcessStartFromNaturalLanguageRequest() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我请个5天的事假")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        AiMessageBlockResponse previewBlock = assistantMessage.blocks().stream()
                .filter(block -> "form-preview".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(previewBlock.result())
                .containsEntry("processKey", "oa_leave")
                .containsEntry("businessType", "OA_LEAVE");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) previewBlock.result().get("formData");
        assertThat(formData)
                .containsEntry("days", 5)
                .containsEntry("leaveType", "PERSONAL");
    }

    @Test
    void shouldRejectProcessStartPreviewWhenPublishedProcessOrFormIsMissing() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(processDefinitionService.getLatestByProcessKey("oa_leave"))
                .thenThrow(new ContractException("PROCESS_DEFINITION.NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, "missing"));

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我发起一个5天的请假")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("系统中未找到可直接发起的已发布流程");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .doesNotContain("form-preview", "confirm");
        verify(aiToolCallMapper, never()).insertToolCall(any());
    }

    @Test
    void shouldPreferReadAnalysisOverProcessStartWhenAskingAboutProgress() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我看看今天我发起了几个请假申请，目前进度都怎么样的")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .doesNotContain("form-preview", "confirm");
        assertThat(assistantMessage.content()).doesNotContain("确认后才会真正执行写操作");
        assertThat(assistantMessage.content()).contains("今天你共发起 2 条申请");
        assertThat(assistantMessage.content()).contains("其中进行中 2 条");
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiConfirmationMapper, never()).insertConfirmation(any());
    }

    @Test
    void shouldUseRoleStatsForRoleCountQuery() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/roles/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("系统有多少个角色")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("系统当前共有 13 个角色");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("chart")
                .doesNotContain("result", "stats");
        assertThat(assistantMessage.blocks().stream()
                .filter(block -> "chart".equals(block.type()))
                .findFirst()
                .orElseThrow()
                .result())
                .containsKey("chart")
                .containsKey("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) assistantMessage.blocks().stream()
                .filter(block -> "chart".equals(block.type()))
                .findFirst()
                .orElseThrow()
                .result()
                .get("chart");
        assertThat(chart)
                .containsEntry("type", "metric")
                .containsEntry("value", 13);
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldUseMenuStatsForMenuCountQuery() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/menus/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("系统有多少个菜单")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("系统当前共有 28 个菜单");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("chart")
                .doesNotContain("result", "stats");
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) assistantMessage.blocks().stream()
                .filter(block -> "chart".equals(block.type()))
                .findFirst()
                .orElseThrow()
                .result()
                .get("chart");
        assertThat(chart)
                .containsEntry("type", "metric")
                .containsEntry("value", 28);
    }

    @Test
    void shouldUseRoleUserAssociationStatsForCompositeCountQuery() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/roles/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我查询下系统角色有多少，关联的用户有多少")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("13 个角色", "13 人");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("stats")
                .doesNotContain("result");
        AiMessageBlockResponse statsBlock = assistantMessage.blocks().stream()
                .filter(block -> "stats".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(statsBlock.metrics())
                .extracting(metric -> metric.label() + ":" + metric.value())
                .contains("角色总数:13", "关联用户数:13");
    }

    @Test
    void shouldUseRoleUserAssociationChartForGroupedRoleQuery() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/roles/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("要列出来每个角色对应的用户数量")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("主要角色分布");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("chart")
                .doesNotContain("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> chart = (Map<String, Object>) assistantMessage.blocks().stream()
                .filter(block -> "chart".equals(block.type()))
                .findFirst()
                .orElseThrow()
                .result()
                .get("chart");
        assertThat(chart)
                .containsEntry("type", "bar")
                .containsEntry("xField", "roleName")
                .containsEntry("yField", "userCount");
    }

    @Test
    void shouldAppendReadIntentMessageThroughSkillRouting() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());
        List<AiMessageRecord> storedMessages = mockStoredMessages(
                new AiMessageRecord(
                        "msg_001",
                        "conv_001",
                        "assistant",
                        "AI Copilot",
                        "我已经汇总当前审批轨迹，可继续追问节点处理路径。",
                        "[]",
                        "usr_001",
                        LocalDateTime.of(2026, 3, 23, 10, 12),
                        LocalDateTime.of(2026, 3, 23, 10, 12)
                )
        );
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我总结一下审批轨迹")
        );

        assertThat(detail.conversationId()).isEqualTo("conv_001");
        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).isEqualTo("当前共有 1 条待办，重点包括：task_001。");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result");
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiConfirmationMapper, never()).insertConfirmation(any());
    }

    @Test
    void shouldAnswerKnowledgeQuestionsThroughFeatureCatalogTool() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("SYSTEM", "route:/system/roles/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("当前页面适合做什么，系统功能怎么用")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("当前页面共整理出 2 个可用工具、1 个可用技能");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result")
                .doesNotContain("chart", "confirm", "form-preview");
        AiMessageBlockResponse resultBlock = assistantMessage.blocks().stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.sourceKey()).isEqualTo("feature.catalog.query");
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务域", "来源页面", "功能摘要");
        assertThat(resultBlock.metrics())
                .extracting(metric -> metric.label() + ":" + metric.value())
                .contains("工具数:2", "技能数:1", "MCP 数:1");
    }

    @Test
    void shouldExplainMatchedFeatureCatalogQuestionWithoutModelFallbackText() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("WORKBENCH", "route:/workbench/todos/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("系统里审批协同是做什么的")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .contains("审批协同")
                .contains("会办", "阅办", "传阅")
                .doesNotContain("Routing 智能体");
        AiMessageBlockResponse resultBlock = assistantMessage.blocks().stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("推荐说明");
        AiMessageBlockResponse traceBlock = assistantMessage.blocks().stream()
                .filter(block -> "trace".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(traceBlock.trace())
                .extracting(AiMessageBlockResponse.TraceStep::label)
                .contains("规划耗时", "执行器耗时", "工具执行耗时", "生成耗时");
    }

    @Test
    void shouldReplyGreetingWithoutRoutingFallbackText() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/ai"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("你好")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).containsAnyOf("你好", "嗨");
        assertThat(assistantMessage.content()).doesNotContain("Routing 智能体");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("text")
                .doesNotContain("trace", "result");
        verify(aiToolCallMapper, never()).insertToolCall(any());
        verify(aiConfirmationMapper, never()).insertConfirmation(any());
    }

    @Test
    void shouldAnswerCapabilityQuestionWithoutRoutingFallbackText() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/oa/leave/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("你具备哪些功能")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .contains("普通问答", "系统功能说明", "统计分析", "审批与待办查询")
                .doesNotContain("Routing 智能体");
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldAnswerUserProfileQuestionWithoutRoutingFallbackText() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/ai"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("张三是什么部门 岗位")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .isEqualTo("张三当前在PLM 产品组，岗位是 产品经理。");
        assertThat(assistantMessage.content()).doesNotContain("Routing 智能体");
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldInheritProfileQueryIntentForShortFollowUpQuestion() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/ai"));
        mockStoredMessages(
                new AiMessageRecord(
                        "msg_prev_user",
                        "conv_001",
                        "user",
                        "你",
                        "张三是什么部门 岗位",
                        "[]",
                        "usr_001",
                        LocalDateTime.of(2026, 4, 1, 10, 48, 20),
                        LocalDateTime.of(2026, 4, 1, 10, 48, 20)
                ),
                new AiMessageRecord(
                        "msg_prev_assistant",
                        "conv_001",
                        "assistant",
                        "AI Copilot",
                        "张三当前在 PLM 产品组，岗位是 产品经理。",
                        "[]",
                        "usr_001",
                        LocalDateTime.of(2026, 4, 1, 10, 48, 21),
                        LocalDateTime.of(2026, 4, 1, 10, 48, 21)
                )
        );
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiCopilotService privilegedService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                objectMapper,
                aiGatewayService,
                aiToolExecutionService,
                runtimeService,
                aiRegistryCatalogService,
                processDefinitionService,
                new AiPlanAgentService(prompt -> "", objectMapper, aiRegistryCatalogService, aiUserReferenceResolver),
                aiExecutionRouter,
                null,
                aiUserReferenceResolver
        ) {
            @Override
            protected String currentUserId() {
                return "usr_001";
            }

            @Override
            protected boolean hasPermission(String permissionCode) {
                return true;
            }
        };

        AiConversationDetailResponse detail = privilegedService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("李四呢")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .isEqualTo("李四当前在人力资源部，岗位是 人力资源专员。");
        assertThat(assistantMessage.content()).doesNotContain("Routing 智能体");
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldAnswerOpenKnowledgeQuestionWithoutExecutorSummaryLeak() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/ai"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我概括一下这个系统对制造企业的价值")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .contains("业务价值", "流程协同", "效率提升")
                .doesNotContain("进入功能问答与使用说明链路")
                .doesNotContain("Routing 智能体");
        AiMessageBlockResponse traceBlock = assistantMessage.blocks().stream()
                .filter(block -> "trace".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(traceBlock.trace())
                .extracting(AiMessageBlockResponse.TraceStep::label)
                .contains("规划耗时", "执行器耗时", "生成耗时");
    }

    @Test
    void shouldBlockCrossUserTodoQueryWithoutPermission() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("OA", "route:/workbench/todos/list"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiCopilotService restrictedService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                objectMapper,
                aiGatewayService,
                aiToolExecutionService,
                runtimeService,
                aiRegistryCatalogService,
                processDefinitionService,
                new AiPlanAgentService(prompt -> "", objectMapper, aiRegistryCatalogService, aiUserReferenceResolver),
                aiExecutionRouter,
                null,
                aiUserReferenceResolver
        ) {
            @Override
            protected String currentUserId() {
                return "usr_001";
            }

            @Override
            protected boolean hasPermission(String permissionCode) {
                return false;
            }
        };

        AiConversationDetailResponse detail = restrictedService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("张三目前有几个待办")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("当前无权查询 张三 的待办");
        verify(aiToolCallMapper, never()).insertToolCall(any());
    }

    @Test
    void shouldFallbackToLegacyGatewayWhenPlannerMainlineFails() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        AiPlanAgentService failingPlanAgentService = new AiPlanAgentService(
                prompt -> {
                    throw new IllegalStateException("planner failed");
                },
                objectMapper,
                aiRegistryCatalogService
        );
        AiCopilotService fallbackService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                objectMapper,
                aiGatewayService,
                aiToolExecutionService,
                runtimeService,
                aiRegistryCatalogService,
                processDefinitionService,
                failingPlanAgentService,
                aiExecutionRouter
        ) {
            @Override
            protected String currentUserId() {
                return "usr_001";
            }
        };

        AiConversationDetailResponse detail = fallbackService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("梳理当前待办")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).isEqualTo("当前共有 1 条待办，重点包括：task_001。");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result");
    }

    @Test
    void shouldFallbackToTodoQueryForGeneralRouteSummaryRequests() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("GENERAL", "route:/"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("梳理当前待办")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).isEqualTo("当前共有 1 条待办，重点包括：task_001。");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result");
        verify(aiToolCallMapper).insertToolCall(any());
    }

    @Test
    void shouldAppendTodoExplanationWithStatsBlock() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("OA", "待办", "route:/workbench/todos/task_001"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我解释这个待办的处理路径")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).isEqualTo("待办 task_001 已命中当前上下文，Copilot 会优先围绕该事项解释处理路径。");
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result", "stats");
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse statsBlock = blocks.stream()
                .filter(block -> "stats".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("上下文命中", "当前待办", "待办摘要", "处理路径建议", "下一步建议");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("当前待办数", "上下文命中", "解释模式");
        assertThat(statsBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "来源页面", "视图");
        assertThat(statsBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("当前待办数", "待办编号", "视图");
    }

    @Test
    void shouldAppendStatsAnswerWithStatsBlock() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("OA", "统计", "route:/system/dashboard"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("按部门统计用户，图表展示")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("当前共 13 名启用用户");
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "chart")
                .doesNotContain("result");
        AiMessageBlockResponse chartBlock = blocks.stream()
                .filter(block -> "chart".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(chartBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("用户总数", "部门数", "最多用户部门");
        assertThat(chartBlock.result()).containsKeys("chart", "data");
    }

    @Test
    void shouldRouteDisabledUserCountToStatsInsteadOfTodoQuery() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("GENERAL", "route:/"));
        mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("停用用户有几个")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).contains("0 名停用用户");
        assertThat(assistantMessage.content()).doesNotContain("当前没有待办");
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "chart")
                .doesNotContain("result");
    }

    @Test
    void shouldAppendPlmSummaryBlocksForPlmAssistantQueries() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("PLM", "route:/plm/query"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我总结一下 ECR 变更")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .contains("对象类型：BOM、图纸、物料")
                .contains("版本差异：ATTRIBUTE · 关键参数调整")
                .contains("任务进度：共 6 条任务，已完成 4，进行中 1，阻塞 1，待处理 1，超期 1，必做未完 1")
                .contains("阻塞任务：TASK-003 · 评审图纸 · 等待图纸冻结")
                .contains("关闭准备度：不可关闭：存在阻塞任务")
                .contains("基线状态：BL-20260323-001 · 设计发布基线 · RELEASED")
                .contains("外部集成：ERP 主数据 · PENDING · 等待编码同步")
                .contains("待验收：待验收 1 项，已完成 2/3")
                .contains("连接器任务：ERP_SYNC · PENDING · 等待派发")
                .contains("卡点同步：ECR-20260323-001 · 电机外壳 BOM 变更（失败 1 / 待处理 1）")
                .contains("关闭阻塞：ECR-20260323-001 · 存在阻塞实施任务（不可关闭：存在阻塞任务）")
                .contains("失败热点：ERP 主数据（失败 1 / 待处理 1 / 影响 1 单）")
                .contains("ECR · ECR-20260323-001 · 电机外壳 BOM 变更")
                .contains("物料主数据变更 · MAT-20260323-003 · 主数据属性调整");
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result", "stats");
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse statsBlock = blocks.stream()
                .filter(block -> "stats".equals(block.type()) && "PLM 业务摘要".equals(block.title()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.sourceKey()).isEqualTo("plm.bill.query");
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("来源页面", "工具调用编号", "查询关键词", "命中业务类型", "首条单据", "类型分布", "状态分布", "对象类型", "版本差异", "任务进度", "阻塞任务", "关闭准备度", "基线状态", "外部集成", "验收状态", "连接器任务", "卡点同步", "关闭阻塞", "失败热点", "命中摘要");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("命中单据数", "类型数", "对象类型数", "版本差异数", "任务进度", "阻塞任务", "可关闭单据", "基线数", "同步风险", "待验收", "连接器待处理", "卡点同步", "关闭阻塞", "失败热点", "集成数", "业务域");
        assertThat(statsBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("命中单据数", "类型数", "对象类型数", "版本差异数", "任务进度", "阻塞任务", "可关闭单据", "基线数", "同步风险", "待验收", "连接器待处理", "卡点同步", "关闭阻塞", "失败热点", "集成数", "业务域");
        assertThat(statsBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("摘要", "类型分布", "状态分布", "对象类型", "版本差异", "任务进度", "阻塞任务", "关闭准备度", "基线状态", "外部集成", "验收状态", "连接器任务", "卡点同步", "关闭阻塞", "失败热点");
    }

    @Test
    void shouldAppendPlmProjectSummaryBlocksForProjectQueries() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("PLM", "route:/plm/projects/proj_001"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我总结一下当前项目的里程碑和交付风险")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content())
                .contains("PROJ-20260415-001 · 电驱平台升级")
                .contains("项目风险：样机试装 · RUNNING · 等待试制回签")
                .contains("里程碑：方案冻结 · COMPLETED")
                .contains("关联链路：ECR-20260415-001 · PLM_BILL");
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result", "stats");
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        AiMessageBlockResponse statsBlock = blocks.stream()
                .filter(block -> "stats".equals(block.type()) && "PLM 项目摘要".equals(block.title()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.sourceKey()).isEqualTo("plm.project.summary");
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("来源页面", "工具调用编号", "查询关键词", "首条项目", "当前阶段", "阶段分布", "状态分布", "业务域分布", "里程碑摘要", "关联链路", "项目风险", "命中摘要");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("命中项目数", "阶段数", "状态数", "项目成员", "里程碑总数", "开放里程碑", "逾期里程碑", "关联变更", "关联对象", "实施任务", "业务域");
        assertThat(statsBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("摘要", "阶段分布", "状态分布", "业务域分布", "里程碑摘要", "关联链路", "项目风险");
    }

    @Test
    void shouldRenderFailureBlockWhenConfirmedWriteExecutionFails() throws Exception {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        when(aiMessageMapper.countByConversationId("conv_001")).thenReturn(0L);

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "workflow.task.fail",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "taskId", "task_002",
                                "action", "REJECT",
                                "comment", "退回补充信息",
                                "domain", "PLM",
                                "routePath", "/plm/eco/eco_001",
                                "businessType", "PLM_ECO",
                                "businessId", "eco_001",
                                "billNo", "ECO-20260323-002",
                                "businessTitle", "产线装配工艺调整"
                        )
                )
        );
        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "workflow.task.fail",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"taskId\":\"task_002\",\"action\":\"REJECT\",\"comment\":\"退回补充信息\",\"domain\":\"PLM\",\"routePath\":\"/plm/eco/eco_001\",\"businessType\":\"PLM_ECO\",\"businessId\":\"eco_001\",\"billNo\":\"ECO-20260323-002\",\"businessTitle\":\"产线装配工艺调整\"}",
                "{}",
                "请确认是否执行失败示例",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, "确认执行", Map.of("taskId", "task_override_001"))
        );

        assertThat(confirmed.status()).isEqualTo("FAILED");
        assertThat(storedMessages).isNotEmpty();
        List<AiMessageBlockResponse> blocks = new ObjectMapper().readValue(
                storedMessages.get(storedMessages.size() - 1).blocksJson(),
                new com.fasterxml.jackson.core.type.TypeReference<List<AiMessageBlockResponse>>() { }
        );
        assertThat(blocks).extracting(AiMessageBlockResponse::type).contains("trace", "failure", "confirm");
        AiMessageBlockResponse failureBlock = blocks.stream()
                .filter(block -> "failure".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(failureBlock.failure()).isNotNull();
        assertThat(failureBlock.sourceType()).isEqualTo("PLATFORM");
        assertThat(failureBlock.sourceKey()).isEqualTo("workflow.task.fail");
        assertThat(failureBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "待办动作", "动作语义", "业务类型", "业务标识", "业务单据", "业务标题", "来源页面", "处理意见", "下一步建议");
        AiMessageBlockResponse confirmBlock = blocks.stream()
                .filter(block -> "confirm".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(confirmBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("工具名称", "工具类型", "确认人", "确认意见", "待办动作", "动作语义", "业务类型", "业务标识", "业务单据", "业务标题");
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("retry");
        AiMessageBlockResponse retryBlock = blocks.stream()
                .filter(block -> "retry".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(retryBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("可重试", "动作语义", "业务域");
        assertThat(retryBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("待办编号", "待办动作", "动作语义", "业务类型", "业务标识", "业务单据", "业务标题", "来源页面", "处理意见", "下一步建议");
    }

    @Test
    void shouldRenderStructuredProcessStartExecutionResultAfterConfirmation() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("OA", "发起", "route:/oa/leave/create"));
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        when(aiMessageMapper.countByConversationId("conv_001")).thenReturn(0L);

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "process.start",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "processKey", "oa_leave",
                                "businessType", "OA_LEAVE",
                                "sceneCode", "default",
                                "routePath", "/oa/leave/create",
                                "domain", "OA",
                                "formData", Map.of(
                                        "days", "2",
                                        "reason", "病假",
                                        "applicant", "张三"
                                )
                        )
                )
        );
        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "process.start",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"processKey\":\"oa_leave\",\"businessType\":\"OA_LEAVE\",\"sceneCode\":\"default\",\"routePath\":\"/oa/leave/create\",\"domain\":\"OA\",\"formData\":{\"days\":\"2\",\"reason\":\"病假\",\"applicant\":\"张三\"}}",
                "{}",
                "请确认是否发起流程",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, "确认发起", Map.of())
        );

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(storedMessages).isNotEmpty();
        List<AiMessageBlockResponse> blocks = parseBlocks(storedMessages.get(storedMessages.size() - 1));
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务类型", "流程编码", "来源页面", "业务摘要", "单据编号", "流程实例", "首个待办", "发起后动作", "执行状态");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("业务域", "表单字段数", "首个待办数", "执行状态");
        assertThat(resultBlock.trace())
                .extracting(AiMessageBlockResponse.TraceStep::label)
                .contains("来源页面", "业务摘要", "执行说明");
    }

    @Test
    void shouldRenderPlmBusinessSummaryWhenConfirmingStartToolCall() {
        when(aiConversationMapper.selectById("conv_001"))
                .thenReturn(conversationWithTags("PLM", "发起", "route:/plm/ecr/create"));
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        when(aiMessageMapper.countByConversationId("conv_001")).thenReturn(0L);

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "process.start",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "processKey", "plm_ecr_change",
                                "businessType", "PLM_ECR",
                                "sceneCode", "default",
                                "routePath", "/plm/ecr/create",
                                "domain", "PLM",
                                "formData", Map.of(
                                        "affectedProductCode", "P-1001",
                                        "changeTitle", "电机外壳 BOM 变更",
                                        "priorityLevel", "高",
                                        "changeReason", "替换原材料规格"
                                )
                        )
                )
        );
        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "process.start",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"processKey\":\"plm_ecr_change\",\"businessType\":\"PLM_ECR\",\"sceneCode\":\"default\",\"routePath\":\"/plm/ecr/create\",\"domain\":\"PLM\",\"formData\":{\"affectedProductCode\":\"P-1001\",\"changeTitle\":\"电机外壳 BOM 变更\",\"priorityLevel\":\"高\",\"changeReason\":\"替换原材料规格\"}}",
                "{}",
                "请确认是否发起流程",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, "确认发起", Map.of())
        );

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(storedMessages).isNotEmpty();
        List<AiMessageBlockResponse> blocks = parseBlocks(storedMessages.get(storedMessages.size() - 1));
        AiMessageBlockResponse resultBlock = blocks.stream()
                .filter(block -> "result".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(resultBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("业务类型", "流程编码", "来源页面", "业务摘要", "单据编号", "流程实例", "首个待办", "发起后动作", "执行状态");
        assertThat(resultBlock.trace())
                .extracting(AiMessageBlockResponse.TraceStep::label)
                .contains("来源页面", "业务摘要", "执行说明");
        assertThat(resultBlock.fields())
                .filteredOn(field -> "业务摘要".equals(field.label()))
                .extracting(AiMessageBlockResponse.Field::value)
                .anyMatch(value -> value != null && value.contains("影响产品 P-1001") && value.contains("优先级 高"));
    }

    @Test
    void shouldMergeArgumentsOverrideWhenConfirmingWriteToolCall() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "task.handle",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "taskId", "task_001",
                                "action", "COMPLETE",
                                "comment", "初始意见"
                        )
                )
        );

        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "task.handle",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"taskId\":\"task_001\",\"action\":\"COMPLETE\",\"comment\":\"初始意见\"}",
                "{}",
                "请确认是否处理当前待办",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(
                        true,
                        "改成新意见后执行",
                        Map.of(
                                "comment", "AI 改写后的审批意见",
                                "action", "COMPLETE"
                        )
                )
        );

        assertThat(confirmed.arguments())
                .containsEntry("taskId", "task_001")
                .containsEntry("action", "COMPLETE")
                .containsEntry("comment", "AI 改写后的审批意见");
    }

    @Test
    void shouldAllowConfirmWriteToolCallWithoutComment() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "process.start",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of(
                                "processKey", "oa_leave",
                                "businessType", "OA_LEAVE",
                                "sceneCode", "default",
                                "routePath", "/oa/leave/list",
                                "domain", "OA",
                                "formData", Map.of("days", "5", "reason", "家里有事")
                        )
                )
        );

        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "process.start",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"processKey\":\"oa_leave\",\"businessType\":\"OA_LEAVE\",\"sceneCode\":\"default\",\"routePath\":\"/oa/leave/list\",\"domain\":\"OA\",\"formData\":{\"days\":\"5\",\"reason\":\"家里有事\"}}",
                "{}",
                "请确认是否发起流程",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, null, Map.of())
        );

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    }

    private AiConversationRecord conversation() {
        return conversationWithTags("PLM", "审批");
    }

    private AiConversationRecord conversationWithTags(String... tags) {
        return new AiConversationRecord(
                "conv_001",
                "PLM 变更审批助手",
                "用户正在追问 ECR 审批建议",
                "active",
                toJson(tags),
                3,
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 0),
                LocalDateTime.of(2026, 3, 23, 10, 0)
        );
    }

    private Map<String, Object> plmBill(
            String businessType,
            String billId,
            String billNo,
            String title,
            String status,
            String detailSummary,
            String approvalSummary
    ) {
        java.util.LinkedHashMap<String, Object> bill = new java.util.LinkedHashMap<>();
        bill.put("businessType", businessType);
        bill.put("businessTypeLabel", "PLM_ECR".equals(businessType)
                ? "ECR"
                : ("PLM_ECO".equals(businessType) ? "ECO" : "物料主数据变更"));
        bill.put("billId", billId);
        bill.put("billNo", billNo);
        bill.put("title", title);
        bill.put("status", status);
        bill.put("statusLabel", switch (status) {
            case "DRAFT" -> "草稿";
            case "COMPLETED" -> "已完成";
            default -> status;
        });
        bill.put("detailSummary", detailSummary);
        bill.put("approvalSummary", approvalSummary);
        bill.put("sceneCode", "default");
        bill.put("createdAt", "2026-03-23T09:00:00+08:00");
        bill.put("updatedAt", "2026-03-23T10:00:00+08:00");
        bill.put("businessSummary", title + " · " + detailSummary + " · " + approvalSummary);
        return Map.copyOf(bill);
    }

    private Map<String, Object> plmBill(
            String businessType,
            String billId,
            String billNo,
            String title,
            String status,
            String detailSummary,
            String approvalSummary,
            Map<String, Object> extras
    ) {
        java.util.LinkedHashMap<String, Object> bill = new java.util.LinkedHashMap<>(plmBill(
                businessType,
                billId,
                billNo,
                title,
                status,
                detailSummary,
                approvalSummary
        ));
        if (extras != null && !extras.isEmpty()) {
            bill.putAll(extras);
        }
        return Map.copyOf(bill);
    }

    private Map<String, Object> plmBillV4DraftExtras() {
        java.util.LinkedHashMap<String, Object> extras = new java.util.LinkedHashMap<>();
        extras.put("objectTypeCount", 2);
        extras.put("objectTypesSummary", "BOM、图纸");
        extras.put("revisionDiffCount", 2);
        extras.put("revisionDiffSummary", "ATTRIBUTE · 关键参数调整；BOM_STRUCTURE · BOM 结构冻结");
        extras.put("taskTotalCount", 4);
        extras.put("taskCompletedCount", 2);
        extras.put("taskRunningCount", 1);
        extras.put("taskBlockedCount", 1);
        extras.put("taskPendingCount", 1);
        extras.put("taskOverdueCount", 1);
        extras.put("taskRequiredOpenCount", 1);
        extras.put("taskProgressSummary", "共 4 条任务，已完成 2，进行中 1，阻塞 1，待处理 1，超期 1，必做未完 1");
        extras.put("blockedTaskCount", 1);
        extras.put("blockedTaskSummary", "TASK-003 · 评审图纸 · 等待图纸冻结");
        extras.put("closeReadyCount", 0);
        extras.put("closeReadinessSummary", "不可关闭：存在阻塞任务");
        extras.put("baselineCount", 1);
        extras.put("baselineStatusSummary", "BL-20260323-001 · 设计发布基线 · RELEASED");
        extras.put("integrationCount", 2);
        extras.put("integrationRiskCount", 1);
        extras.put("integrationStatusSummary", "ERP 主数据 · PENDING · 等待编码同步；PDM 文档库 · SYNCED · 图纸已发布");
        extras.put("acceptanceTotalCount", 2);
        extras.put("acceptanceDueCount", 1);
        extras.put("acceptanceSummary", "待验收 1 项，已完成 1/2");
        extras.put("connectorJobCount", 2);
        extras.put("connectorPendingCount", 1);
        extras.put("connectorStatusSummary", "ERP_SYNC · PENDING · 等待派发；PDM_RELEASE · ACKED");
        extras.put("stuckSyncSummary", "ECR-20260323-001 · 电机外壳 BOM 变更（失败 1 / 待处理 1）");
        extras.put("closeBlockerSummary", "ECR-20260323-001 · 存在阻塞实施任务（不可关闭：存在阻塞任务）");
        extras.put("failedSystemHotspotSummary", "ERP 主数据（失败 1 / 待处理 1 / 影响 1 单）");
        return Map.copyOf(extras);
    }

    private Map<String, Object> plmBillV4ReadyExtras() {
        java.util.LinkedHashMap<String, Object> extras = new java.util.LinkedHashMap<>();
        extras.put("objectTypeCount", 1);
        extras.put("objectTypesSummary", "物料");
        extras.put("revisionDiffCount", 1);
        extras.put("revisionDiffSummary", "ATTRIBUTE · 物料编码与名称同步");
        extras.put("taskTotalCount", 2);
        extras.put("taskCompletedCount", 2);
        extras.put("taskRunningCount", 0);
        extras.put("taskBlockedCount", 0);
        extras.put("taskPendingCount", 0);
        extras.put("taskOverdueCount", 0);
        extras.put("taskRequiredOpenCount", 0);
        extras.put("taskProgressSummary", "共 2 条任务，已完成 2，进行中 0，阻塞 0，待处理 0，超期 0，必做未完 0");
        extras.put("blockedTaskCount", 0);
        extras.put("blockedTaskSummary", "");
        extras.put("closeReadyCount", 1);
        extras.put("closeReadinessSummary", "可关闭");
        extras.put("baselineCount", 1);
        extras.put("baselineStatusSummary", "BL-20260320-003 · 主数据发布基线 · RELEASED");
        extras.put("integrationCount", 1);
        extras.put("integrationRiskCount", 0);
        extras.put("integrationStatusSummary", "ERP 主数据 · SYNCED · 主数据已同步");
        extras.put("acceptanceTotalCount", 1);
        extras.put("acceptanceDueCount", 0);
        extras.put("acceptanceSummary", "验收已完成 1/1");
        extras.put("connectorJobCount", 1);
        extras.put("connectorPendingCount", 0);
        extras.put("connectorStatusSummary", "ERP_SYNC · ACKED");
        extras.put("stuckSyncSummary", "");
        extras.put("closeBlockerSummary", "");
        extras.put("failedSystemHotspotSummary", "");
        return Map.copyOf(extras);
    }

    private Map<String, Object> plmBillV4Result() {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("count", 2);
        result.put("items", List.of(
                plmBill(
                        "PLM_ECR",
                        "ecr_001",
                        "ECR-20260323-001",
                        "电机外壳 BOM 变更",
                        "DRAFT",
                        "影响产品 P-1001 · 优先级 高",
                        "草稿 · 当前节点 待同步",
                        plmBillV4DraftExtras()
                ),
                plmBill(
                        "PLM_MATERIAL",
                        "mat_001",
                        "MAT-20260323-003",
                        "主数据属性调整",
                        "COMPLETED",
                        "物料 MAT-7788 / 电机外壳",
                        "已完成 · 当前节点 proc_mat_001",
                        plmBillV4ReadyExtras()
                )
        ));
        result.put("typeBreakdown", List.of(
                Map.of("key", "PLM_ECR", "label", "ECR", "count", 1),
                Map.of("key", "PLM_MATERIAL", "label", "物料主数据变更", "count", 1)
        ));
        result.put("statusBreakdown", List.of(
                Map.of("key", "DRAFT", "label", "草稿", "count", 1),
                Map.of("key", "COMPLETED", "label", "已完成", "count", 1)
        ));
        result.put("objectTypeCount", 3);
        result.put("revisionDiffCount", 3);
        result.put("taskTotalCount", 6);
        result.put("taskCompletedCount", 4);
        result.put("taskBlockedCount", 1);
        result.put("closeReadyCount", 1);
        result.put("baselineCount", 2);
        result.put("baselineStatusSummary", "BL-20260323-001 · 设计发布基线 · RELEASED；BL-20260320-003 · 主数据发布基线 · RELEASED");
        result.put("integrationCount", 3);
        result.put("integrationRiskCount", 1);
        result.put("integrationStatusSummary", "ERP 主数据 · PENDING · 等待编码同步；PDM 文档库 · SYNCED · 图纸已发布；ERP 主数据 · SYNCED · 主数据已同步");
        result.put("acceptanceTotalCount", 3);
        result.put("acceptanceDueCount", 1);
        result.put("acceptanceSummary", "待验收 1 项，已完成 2/3");
        result.put("connectorJobCount", 3);
        result.put("connectorPendingCount", 1);
        result.put("connectorStatusSummary", "ERP_SYNC · PENDING · 等待派发；PDM_RELEASE · ACKED；ERP_SYNC · ACKED");
        result.put("stuckSyncItems", List.of(
                Map.of("billId", "ecr_001", "billNo", "ECR-20260323-001", "title", "电机外壳 BOM 变更", "businessTypeLabel", "ECR", "failedCount", 1, "pendingCount", 1, "summary", "ERP 主数据 · PENDING · 等待编码同步")
        ));
        result.put("stuckSyncSummary", "ECR-20260323-001 · 电机外壳 BOM 变更（失败 1 / 待处理 1）");
        result.put("closeBlockerItems", List.of(
                Map.of("billId", "ecr_001", "billNo", "ECR-20260323-001", "title", "电机外壳 BOM 变更", "businessTypeLabel", "ECR", "blockerTitle", "存在阻塞实施任务", "blockerCount", 2, "summary", "不可关闭：存在阻塞任务")
        ));
        result.put("closeBlockerSummary", "ECR-20260323-001 · 存在阻塞实施任务（不可关闭：存在阻塞任务）");
        result.put("failedSystemHotspots", List.of(
                Map.of("systemName", "ERP 主数据", "failedCount", 1, "pendingCount", 1, "blockedBillCount", 1, "summary", "ERP 主数据 失败 1 条、待处理 1 条，影响 1 张单据")
        ));
        result.put("failedSystemHotspotSummary", "ERP 主数据（失败 1 / 待处理 1 / 影响 1 单）");
        result.put("objectTypesSummary", "BOM、图纸、物料");
        result.put("revisionDiffSummary", "ATTRIBUTE · 关键参数调整；BOM_STRUCTURE · BOM 结构冻结；ATTRIBUTE · 物料编码与名称同步");
        result.put("taskProgressSummary", "共 6 条任务，已完成 4，进行中 1，阻塞 1，待处理 1，超期 1，必做未完 1");
        result.put("blockedTaskSummary", "TASK-003 · 评审图纸 · 等待图纸冻结");
        result.put("closeReadinessSummary", "不可关闭：存在阻塞任务");
        result.put("summary", "当前命中 2 条 PLM 单据，类型分布：ECR 1 条、物料主数据变更 1 条，状态分布：草稿 1 条、已完成 1 条，受影响对象共 3 个，对象类型：BOM、图纸、物料，版本差异：ATTRIBUTE · 关键参数调整；BOM_STRUCTURE · BOM 结构冻结；ATTRIBUTE · 物料编码与名称同步，任务进度：共 6 条任务，已完成 4，进行中 1，阻塞 1，待处理 1，超期 1，必做未完 1；阻塞任务：TASK-003 · 评审图纸 · 等待图纸冻结，关闭准备度：不可关闭：存在阻塞任务，基线状态：BL-20260323-001 · 设计发布基线 · RELEASED；BL-20260320-003 · 主数据发布基线 · RELEASED，外部集成：ERP 主数据 · PENDING · 等待编码同步；PDM 文档库 · SYNCED · 图纸已发布；ERP 主数据 · SYNCED · 主数据已同步，待验收：待验收 1 项，已完成 2/3，连接器任务：ERP_SYNC · PENDING · 等待派发；PDM_RELEASE · ACKED；ERP_SYNC · ACKED，卡点同步：ECR-20260323-001 · 电机外壳 BOM 变更（失败 1 / 待处理 1），关闭阻塞：ECR-20260323-001 · 存在阻塞实施任务（不可关闭：存在阻塞任务），失败热点：ERP 主数据（失败 1 / 待处理 1 / 影响 1 单）；重点包括：ECR · ECR-20260323-001 · 电机外壳 BOM 变更（影响产品 P-1001 · 优先级 高 · 草稿 · 当前节点 待同步）；物料主数据变更 · MAT-20260323-003 · 主数据属性调整（物料 MAT-7788 / 电机外壳 · 已完成 · 当前节点 proc_mat_001）。");
        return Map.copyOf(result);
    }

    private Map<String, Object> plmProjectResult() {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("keyword", "proj_001");
        result.put("count", 2);
        result.put("items", List.of(
                Map.<String, Object>ofEntries(
                        Map.entry("businessType", "PLM_PROJECT"),
                        Map.entry("businessTypeLabel", "PLM 项目"),
                        Map.entry("projectId", "proj_001"),
                        Map.entry("billNo", "PROJ-20260415-001"),
                        Map.entry("title", "电驱平台升级"),
                        Map.entry("status", "ACTIVE"),
                        Map.entry("statusLabel", "进行中"),
                        Map.entry("phaseCode", "VALIDATION"),
                        Map.entry("phaseLabel", "验证"),
                        Map.entry("domainCode", "PRODUCT"),
                        Map.entry("ownerDisplayName", "张三"),
                        Map.entry("businessSummary", "阶段 验证 · 里程碑 4 · 逾期 1"),
                        Map.entry("milestoneSummary", "方案冻结 · COMPLETED；样机试装 · RUNNING"),
                        Map.entry("linkSummary", "ECR-20260415-001 · PLM_BILL · 电驱系统 ECR；BASE-20260415-001 · BASELINE · 验证基线"),
                        Map.entry("recentRiskSummary", "样机试装 · RUNNING · 等待试制回签")
                ),
                Map.<String, Object>ofEntries(
                        Map.entry("businessType", "PLM_PROJECT"),
                        Map.entry("businessTypeLabel", "PLM 项目"),
                        Map.entry("projectId", "proj_002"),
                        Map.entry("billNo", "PROJ-20260415-002"),
                        Map.entry("title", "BMS 控制器换版"),
                        Map.entry("status", "PLANNING"),
                        Map.entry("statusLabel", "筹备中"),
                        Map.entry("phaseCode", "DESIGN"),
                        Map.entry("phaseLabel", "设计"),
                        Map.entry("domainCode", "ELECTRONICS"),
                        Map.entry("ownerDisplayName", "李四"),
                        Map.entry("businessSummary", "阶段 设计 · 里程碑 2 · 逾期 0"),
                        Map.entry("milestoneSummary", "详细设计评审 · PENDING"),
                        Map.entry("linkSummary", "ECO-20260415-011 · PLM_BILL · BMS 控制器 ECO"),
                        Map.entry("recentRiskSummary", "")
                )
        ));
        result.put("statusBreakdown", List.of(
                Map.of("code", "ACTIVE", "label", "进行中", "count", 1),
                Map.of("code", "PLANNING", "label", "筹备中", "count", 1)
        ));
        result.put("phaseBreakdown", List.of(
                Map.of("code", "VALIDATION", "label", "验证", "count", 1),
                Map.of("code", "DESIGN", "label", "设计", "count", 1)
        ));
        result.put("domainBreakdown", List.of(
                Map.of("code", "PRODUCT", "label", "PRODUCT", "count", 1),
                Map.of("code", "ELECTRONICS", "label", "ELECTRONICS", "count", 1)
        ));
        result.put("memberCount", 8);
        result.put("milestoneCount", 6);
        result.put("openMilestoneCount", 3);
        result.put("overdueMilestoneCount", 1);
        result.put("billLinkCount", 2);
        result.put("objectLinkCount", 3);
        result.put("taskLinkCount", 2);
        result.put("milestoneSummary", "方案冻结 · COMPLETED；样机试装 · RUNNING；详细设计评审 · PENDING");
        result.put("linkSummary", "ECR-20260415-001 · PLM_BILL · 电驱系统 ECR；BASE-20260415-001 · BASELINE · 验证基线；ECO-20260415-011 · PLM_BILL · BMS 控制器 ECO");
        result.put("riskSummary", "样机试装 · RUNNING · 等待试制回签");
        result.put("summary", "PROJ-20260415-001 · 电驱平台升级 · 验证（阶段 验证 · 里程碑 4 · 逾期 1） · 进行中；PROJ-20260415-002 · BMS 控制器换版 · 设计（阶段 设计 · 里程碑 2 · 逾期 0） · 筹备中；项目风险：样机试装 · RUNNING · 等待试制回签；里程碑：方案冻结 · COMPLETED；样机试装 · RUNNING；详细设计评审 · PENDING；关联链路：ECR-20260415-001 · PLM_BILL · 电驱系统 ECR；BASE-20260415-001 · BASELINE · 验证基线；ECO-20260415-011 · PLM_BILL · BMS 控制器 ECO");
        return Map.copyOf(result);
    }

    private String toJson(String... tags) {
        try {
            return new ObjectMapper().writeValueAsString(List.of(tags));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private PublishedProcessDefinition publishedProcessDefinition(String processKey) {
        return switch (processKey) {
            case "oa_leave" -> new PublishedProcessDefinition(
                    "oa_leave:1",
                    "oa_leave",
                    "请假审批",
                    "OA",
                    1,
                    "PUBLISHED",
                    LocalDateTime.of(2026, 3, 23, 9, 0).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),
                    new ProcessDslPayload(
                            "1.0.0",
                            "oa_leave",
                            "请假审批",
                            "OA",
                            "oa-leave-start-form",
                            "1.1.0",
                            List.of(),
                            Map.of(),
                            List.of(
                                    new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of(), Map.of(), Map.of()),
                                    new ProcessDslPayload.Node("end_1", "end", "结束", null, Map.of(), Map.of(), Map.of())
                            ),
                            List.of(new ProcessDslPayload.Edge("edge_1", "start_1", "end_1", 10, "提交", Map.of()))
                    ),
                    ""
            );
            case "oa_expense" -> simplePublishedProcessDefinition("oa_expense", "报销审批", "oa-expense-start-form");
            case "oa_common" -> simplePublishedProcessDefinition("oa_common", "通用申请审批", "oa-common-start-form");
            default -> throw new ContractException("PROCESS_DEFINITION.NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, "missing");
        };
    }

    private PublishedProcessDefinition simplePublishedProcessDefinition(
            String processKey,
            String processName,
            String processFormKey
    ) {
        return new PublishedProcessDefinition(
                processKey + ":1",
                processKey,
                processName,
                "OA",
                1,
                "PUBLISHED",
                LocalDateTime.of(2026, 3, 23, 9, 0).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),
                new ProcessDslPayload(
                        "1.0.0",
                        processKey,
                        processName,
                        "OA",
                        processFormKey,
                        "1.0.0",
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of(), Map.of(), Map.of()),
                                new ProcessDslPayload.Node("end_1", "end", "结束", null, Map.of(), Map.of(), Map.of())
                        ),
                        List.of(new ProcessDslPayload.Edge("edge_1", "start_1", "end_1", 10, "提交", Map.of()))
                ),
                ""
        );
    }

    private List<AiMessageRecord> mockStoredMessages(AiMessageRecord... initialMessages) {
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>(List.of(initialMessages));
        AtomicLong initialCount = new AtomicLong(storedMessages.size());
        when(aiMessageMapper.countByConversationId("conv_001")).thenAnswer(invocation -> {
            if (storedMessages.isEmpty()) {
                return initialCount.get();
            }
            return (long) storedMessages.size();
        });
        when(aiMessageMapper.selectByConversationId("conv_001")).thenAnswer(invocation -> List.copyOf(storedMessages));
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        return storedMessages;
    }

    private List<AiMessageBlockResponse> parseBlocks(AiMessageRecord messageRecord) {
        try {
            return new ObjectMapper().readValue(
                    messageRecord.blocksJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<AiMessageBlockResponse>>() { }
            );
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent mockSupervisorAgent(String reply) {
        try {
            com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent agent =
                    org.mockito.Mockito.mock(com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent.class);
            lenient().when(agent.invoke(anyString()))
                    .thenReturn(java.util.Optional.of(new com.alibaba.cloud.ai.graph.OverAllState(Map.of("output", reply))));
            return agent;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent mockRoutingAgent(String reply) {
        try {
            com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent agent =
                    org.mockito.Mockito.mock(com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent.class);
            lenient().when(agent.invoke(anyString()))
                    .thenReturn(java.util.Optional.of(new com.alibaba.cloud.ai.graph.OverAllState(Map.of("output", reply))));
            return agent;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
