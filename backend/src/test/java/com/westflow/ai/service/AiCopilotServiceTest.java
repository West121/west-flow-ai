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

    @BeforeEach
    void setUp() {
        confirmedWriteExecuted = new AtomicBoolean(false);
        AiAgentRegistry aiAgentRegistry = new AiAgentRegistry(List.of(
                new AiAgentDescriptor("supervisor", "Supervisor", "SUPERVISOR", List.of("OA", "PLM", "GENERAL"), true, 100),
                new AiAgentDescriptor("routing", "Routing", "ROUTING", List.of("OA", "PLM", "GENERAL"), false, 80)
        ));
        AiSkillRegistry aiSkillRegistry = new AiSkillRegistry(List.of(
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
                                                        "createdAt", "2026-03-26T09:00:00+08:00"
                                                ),
                                                Map.of(
                                                        "instanceId", "proc_002",
                                                        "businessTitle", "请假申请B",
                                                        "billNo", "LEAVE-002",
                                                        "currentNodeName", "负责人确认",
                                                        "currentTaskId", "task_init_002",
                                                        "instanceStatus", "RUNNING",
                                                        "createdAt", "2026-03-26T11:30:00+08:00"
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
                        context -> Map.of(
                                "total", 12,
                                "completed", 9,
                                "pending", 3,
                                "completionRate", "75%"
                        )
                ),
                AiToolDefinition.read(
                        "plm.bill.query",
                        AiToolSource.PLATFORM,
                        "已返回 PLM 单据列表",
                        context -> Map.of(
                                "count", 2,
                                "items", List.of(
                                        Map.of("businessType", "PLM_ECR", "billId", "ecr_001", "billNo", "ECR-20260323-001", "title", "电机外壳 BOM 变更"),
                                        Map.of("businessType", "PLM_MATERIAL", "billId", "mat_001", "billNo", "MAT-20260323-003", "title", "主数据属性调整")
                                )
                        )
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "已返回 PLM 变更摘要",
                        context -> Map.of(
                                "count", 2,
                                "items", List.of(
                                        Map.of("businessType", "PLM_ECR", "billId", "ecr_001", "billNo", "ECR-20260323-001", "title", "电机外壳 BOM 变更"),
                                        Map.of("businessType", "PLM_MATERIAL", "billId", "mat_001", "billNo", "MAT-20260323-003", "title", "主数据属性调整")
                                )
                        )
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
        AiCopilotRuntimeService runtimeService = new SpringAiAlibabaCopilotRuntimeService(
                chatClient,
                mockSupervisorAgent("supervisor-reply"),
                mockRoutingAgent("routing-reply")
        );
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
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
                    return java.util.Optional.empty();
                });
        lenient().when(processDefinitionService.getLatestByProcessKey(anyString()))
                .thenAnswer(invocation -> publishedProcessDefinition(invocation.getArgument(0, String.class)));
        aiCopilotService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                new ObjectMapper(),
                new AiGatewayService(new AiOrchestrationPlanner(aiAgentRegistry, aiSkillRegistry)),
                new AiToolExecutionService(aiToolRegistry),
                runtimeService,
                aiRegistryCatalogService,
                processDefinitionService
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
        assertThat(previewBlock.trace()).isNullOrEmpty();
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
                new AiMessageAppendRequest("给我看一下本周流程统计")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.content()).isEqualTo("当前统计：总量 12，已完成 9，待处理 3，完成率 75%。");
        List<AiMessageBlockResponse> blocks = assistantMessage.blocks();
        assertThat(blocks)
                .extracting(AiMessageBlockResponse::type)
                .contains("trace", "result", "stats");
        AiMessageBlockResponse statsBlock = blocks.stream()
                .filter(block -> "stats".equals(block.type()))
                .findFirst()
                .orElseThrow();
        assertThat(statsBlock.fields())
                .extracting(AiMessageBlockResponse.Field::label)
                .contains("统计口径", "统计主题", "摘要");
        assertThat(statsBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("总量", "已完成", "待处理", "完成率");
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
        assertThat(assistantMessage.content()).isEqualTo(
                "当前命中 2 条 PLM 单据，重点包括：PLM_ECR · ECR-20260323-001 · 电机外壳 BOM 变更；PLM_MATERIAL · MAT-20260323-003 · 主数据属性调整。"
        );
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
                .contains("来源页面", "工具调用编号", "命中业务类型", "首条单据", "命中摘要");
        assertThat(resultBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("命中单据数", "业务域");
        assertThat(statsBlock.metrics())
                .extracting(AiMessageBlockResponse.Metric::label)
                .contains("命中单据数", "业务域");
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
