package com.westflow.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.runtime.SpringAiAlibabaCopilotRuntimeService;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.service.AiRuntimeToolCallbackProvider;
import com.westflow.ai.service.AiToolExecutionService;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.oa.api.CreateOACommonRequestBillRequest;
import com.westflow.oa.api.CreateOAExpenseBillRequest;
import com.westflow.oa.api.CreateOALeaveBillRequest;
import com.westflow.oa.service.OALaunchService;
import com.westflow.plm.api.CreatePLMEcoBillRequest;
import com.westflow.plm.api.CreatePLMEcrBillRequest;
import com.westflow.plm.api.CreatePLMMaterialChangeBillRequest;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.plm.api.PlmLaunchResponse;
import com.westflow.plm.service.PlmLaunchService;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.request.ApprovalSheetListView;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.request.ClaimTaskRequest;
import com.westflow.processruntime.api.request.CompleteTaskRequest;
import com.westflow.processruntime.api.request.RejectTaskRequest;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.FlowableRuntimeStartService;
import com.westflow.oa.api.OALaunchResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI Copilot 默认组件装配。
 */
@Configuration
public class AiCopilotConfiguration {

    /**
     * 装配 Spring AI ChatClient。
     */
    @Bean
    public ChatClient aiCopilotChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 West Flow AI Copilot，请始终使用简洁中文回答。")
                .build();
    }

    /**
     * 注册真实平台工具处理器，元数据和权限由数据库注册表控制。
     */
    @Bean
    public AiToolRegistry aiToolRegistry(
            ProcessDefinitionService processDefinitionService,
            FlowableProcessRuntimeService flowableProcessRuntimeService,
            FlowableRuntimeStartService flowableRuntimeStartService,
            OALaunchService oaLaunchService,
            PlmLaunchService plmLaunchService,
            JdbcTemplate jdbcTemplate
    ) {
        return new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "workflow.definition.list",
                        AiToolSource.PLATFORM,
                        "查询流程定义",
                        context -> Map.of(
                                "page", processDefinitionService.page(new PageRequest(
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().get("keyword")),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ))
                        )
                ),
                AiToolDefinition.read(
                        "task.query",
                        AiToolSource.PLATFORM,
                        "查询待办",
                        context -> Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.valueOf(stringValue(context.request().arguments().getOrDefault("view", "TODO")).toUpperCase()),
                                        List.of(),
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().get("keyword")),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ))
                        )
                ),
                AiToolDefinition.read(
                        "stats.query",
                        AiToolSource.PLATFORM,
                        "查询统计",
                        context -> Map.of(
                                "publishedDefinitionCount", scalarCount(jdbcTemplate, "SELECT COUNT(*) FROM wf_process_definition WHERE status = 'PUBLISHED'"),
                                "activeConversationCount", scalarCount(jdbcTemplate, "SELECT COUNT(*) FROM wf_ai_conversation WHERE status = 'active'"),
                                "runtimeTaskCount", scalarCount(jdbcTemplate, "SELECT COUNT(*) FROM ACT_RU_TASK"),
                                "runtimeInstanceCount", scalarCount(jdbcTemplate, "SELECT COUNT(*) FROM ACT_RU_EXECUTION WHERE PARENT_ID_ IS NULL")
                        )
                ),
                AiToolDefinition.read(
                        "plm.bill.query",
                        AiToolSource.PLATFORM,
                        "查询 PLM 单据",
                        context -> queryPlmBills(jdbcTemplate, stringValue(context.request().arguments().get("keyword")))
                ),
                AiToolDefinition.write(
                        "process.start",
                        AiToolSource.PLATFORM,
                        "发起流程",
                        context -> handleProcessStart(
                                flowableRuntimeStartService,
                                oaLaunchService,
                                plmLaunchService,
                                context.request().arguments()
                        )
                ),
                AiToolDefinition.write(
                        "task.handle",
                        AiToolSource.PLATFORM,
                        "处理待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, context.request().arguments())
                ),
                AiToolDefinition.write(
                        "workflow.task.complete",
                        AiToolSource.AGENT,
                        "完成待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, mergeAction(context.request().arguments(), "COMPLETE"))
                ),
                AiToolDefinition.write(
                        "workflow.task.reject",
                        AiToolSource.AGENT,
                        "驳回待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, mergeAction(context.request().arguments(), "REJECT"))
                ),
                AiToolDefinition.read(
                        "workflow.todo.list",
                        AiToolSource.PLATFORM,
                        "查询待办",
                        context -> Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.TODO,
                                        List.of(),
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ))
                        )
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "查询 PLM 单据",
                        context -> queryPlmBills(jdbcTemplate, stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))))
                ),
                AiToolDefinition.read(
                        "workflow.trace.summary",
                        AiToolSource.SKILL,
                        "查询待办",
                        context -> Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.TODO,
                                        List.of(),
                                        1,
                                        5,
                                        stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ))
                        )
                )
        ));
    }

    /**
     * 装配 AI 编排规划器。
     */
    @Bean
    public AiOrchestrationPlanner aiOrchestrationPlanner(AiRegistryCatalogService aiRegistryCatalogService) {
        return new AiOrchestrationPlanner(aiRegistryCatalogService);
    }

    /**
     * 装配 AI 网关。
     */
    @Bean
    public AiGatewayService aiGatewayService(AiOrchestrationPlanner aiOrchestrationPlanner) {
        return new AiGatewayService(aiOrchestrationPlanner);
    }

    /**
     * 装配工具执行服务。
     */
    @Bean
    public AiToolExecutionService aiToolExecutionService(
            AiToolRegistry aiToolRegistry,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        return new AiToolExecutionService(aiToolRegistry, aiRegistryCatalogService);
    }

    /**
     * 流程设计智能体。
     */
    @Bean
    public ReactAgent workflowDesignAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("workflow-design-agent")
                .description("负责流程定义、节点建议和发布前检查")
                .model(chatModel)
                .instruction("你是流程设计智能体，请结合输入生成流程设计建议。")
                .build();
    }

    /**
     * 待办处理智能体。
     */
    @Bean
    public ReactAgent taskHandleAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("task-handle-agent")
                .description("负责解释待办、审批动作与处理建议")
                .model(chatModel)
                .instruction("你是待办处理智能体，请结合任务上下文生成处理建议。")
                .build();
    }

    /**
     * 统计问答智能体。
     */
    @Bean
    public ReactAgent statsAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("stats-agent")
                .description("负责流程、OA、PLM 统计问答")
                .model(chatModel)
                .instruction("你是统计问答智能体，请根据业务域回答统计问题。")
                .build();
    }

    /**
     * PLM 助手。
     */
    @Bean
    public ReactAgent plmAssistantAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("plm-assistant-agent")
                .description("负责 PLM 变更、ECR/ECO 和物料变更问题")
                .model(chatModel)
                .instruction("你是 PLM 助手，请回答 ECR、ECO 和物料变更相关问题。")
                .build();
    }

    /**
     * Routing Agent。
     */
    @Bean
    public LlmRoutingAgent routingAgent(
            ChatModel chatModel,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return LlmRoutingAgent.builder()
                .name("copilot-routing-agent")
                .description("根据用户意图把问题路由到具体业务智能体")
                .model(chatModel)
                .systemPrompt("""
                        你是 West Flow AI Routing Agent。
                        请根据输入把请求路由到最合适的子智能体。
                        - workflow-design-agent: 流程设计、流程定义、节点配置
                        - task-handle-agent: 待办、审批、轨迹、动作建议
                        - stats-agent: 统计、报表、趋势、指标
                        - plm-assistant-agent: ECR、ECO、物料变更、PLM 流程
                        """)
                .fallbackAgent("task-handle-agent")
                .subAgents(List.of(workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * Supervisor Agent。
     */
    @Bean
    public SupervisorAgent supervisorAgent(
            ChatModel chatModel,
            LlmRoutingAgent routingAgent,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return SupervisorAgent.builder()
                .name("copilot-supervisor-agent")
                .description("负责协调 Copilot 多智能体和写操作确认前的监督")
                .model(chatModel)
                .mainAgent(taskHandleAgent)
                .systemPrompt("""
                        你是 West Flow AI Supervisor。
                        你需要协调 Routing Agent 与各业务智能体，并确保写操作遵守“必须确认”策略。
                        如果请求是查询或分析，允许直接交给 Routing Agent。
                        如果请求涉及发起、审批、退回、驳回等写动作，只输出确认前说明。
                        """)
                .subAgents(List.of(routingAgent, workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * 装配 Spring AI Alibaba 运行时服务。
     */
    @Bean
    public AiCopilotRuntimeService aiCopilotRuntimeService(
            ChatClient aiCopilotChatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider
    ) {
        return new SpringAiAlibabaCopilotRuntimeService(
                aiCopilotChatClient,
                supervisorAgent,
                routingAgent,
                aiRegistryCatalogService,
                aiRuntimeToolCallbackProvider
        );
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
            return normalized;
        }
        return Map.of();
    }

    private static long scalarCount(JdbcTemplate jdbcTemplate, String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private static Map<String, Object> handleProcessStart(
            FlowableRuntimeStartService flowableRuntimeStartService,
            OALaunchService oaLaunchService,
            PlmLaunchService plmLaunchService,
            Map<String, Object> arguments
    ) {
        String businessType = stringValue(arguments.get("businessType")).toUpperCase();
        String sceneCode = normalizeSceneCode(arguments.get("sceneCode"));
        Map<String, Object> formData = mapValue(arguments.get("formData"));

        return switch (businessType) {
            case "OA_LEAVE" -> toLaunchResult(
                    oaLaunchService.createLeaveBill(new CreateOALeaveBillRequest(
                            sceneCode,
                            parseInt(formData.get("days"), 0),
                            stringValue(formData.get("reason"))
                    ))
            );
            case "OA_EXPENSE" -> toLaunchResult(
                    oaLaunchService.createExpenseBill(new CreateOAExpenseBillRequest(
                            sceneCode,
                            parseDecimal(formData.get("amount")),
                            stringValue(formData.get("reason"))
                    ))
            );
            case "OA_COMMON" -> toLaunchResult(
                    oaLaunchService.createCommonRequestBill(new CreateOACommonRequestBillRequest(
                            sceneCode,
                            stringValue(formData.get("title")),
                            stringValue(formData.get("content"))
                    ))
            );
            case "PLM_ECR" -> toLaunchResult(
                    plmLaunchService.createEcrBill(new CreatePLMEcrBillRequest(
                            sceneCode,
                            stringValue(formData.get("changeTitle")),
                            stringValue(formData.get("changeReason")),
                            nullableString(formData.get("affectedProductCode")),
                            nullableString(formData.get("priorityLevel"))
                    ))
            );
            case "PLM_ECO" -> toLaunchResult(
                    plmLaunchService.createEcoBill(new CreatePLMEcoBillRequest(
                            sceneCode,
                            stringValue(formData.get("executionTitle")),
                            stringValue(formData.get("executionPlan")),
                            parseDate(formData.get("effectiveDate")),
                            stringValue(formData.get("changeReason"))
                    ))
            );
            case "PLM_MATERIAL" -> toLaunchResult(
                    plmLaunchService.createMaterialChangeBill(new CreatePLMMaterialChangeBillRequest(
                            sceneCode,
                            stringValue(formData.get("materialCode")),
                            stringValue(formData.get("materialName")),
                            stringValue(formData.get("changeReason")),
                            nullableString(formData.get("changeType"))
                    ))
            );
            default -> {
                String processKey = stringValue(arguments.get("processKey"));
                if (processKey.isBlank()) {
                    throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "缺少 processKey");
                }
                var response = flowableRuntimeStartService.start(new StartProcessRequest(
                        processKey,
                        stringValue(arguments.get("businessKey")),
                        stringValue(arguments.get("businessType")),
                        formData
                ));
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "processDefinitionId", response.processDefinitionId(),
                        "status", response.status(),
                        "activeTasks", response.activeTasks()
                );
            }
        };
    }

    private static Map<String, Object> queryPlmBills(JdbcTemplate jdbcTemplate, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String likeKeyword = "%" + normalizedKeyword + "%";
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                        SELECT business_type, bill_id, bill_no, title
                        FROM (
                          SELECT 'PLM_ECR' AS business_type, id AS bill_id, bill_no, change_title AS title FROM plm_ecr_change
                          UNION ALL
                          SELECT 'PLM_ECO' AS business_type, id AS bill_id, bill_no, execution_title AS title FROM plm_eco_execution
                          UNION ALL
                          SELECT 'PLM_MATERIAL' AS business_type, id AS bill_id, bill_no, material_name AS title FROM plm_material_change
                        ) bills
                        WHERE ? = '' OR bill_no ILIKE ? OR title ILIKE ?
                        ORDER BY bill_no DESC
                        LIMIT 10
                        """,
                (resultSet, rowNum) -> Map.of(
                        "businessType", resultSet.getString("business_type"),
                        "billId", resultSet.getString("bill_id"),
                        "billNo", resultSet.getString("bill_no"),
                        "title", resultSet.getString("title")
                ),
                normalizedKeyword,
                likeKeyword,
                likeKeyword
        );
        return Map.of("count", items.size(), "items", items);
    }

    private static Map<String, Object> mergeAction(Map<String, Object> arguments, String action) {
        Map<String, Object> merged = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        merged.put("action", action);
        return merged;
    }

    private static Map<String, Object> toLaunchResult(OALaunchResponse response) {
        return Map.of(
                "billId", response.billId(),
                "billNo", response.billNo(),
                "instanceId", response.processInstanceId(),
                "activeTasks", response.activeTasks()
        );
    }

    private static Map<String, Object> toLaunchResult(PlmLaunchResponse response) {
        return Map.of(
                "billId", response.billId(),
                "billNo", response.billNo(),
                "instanceId", response.processInstanceId(),
                "activeTasks", response.activeTasks()
        );
    }

    private static String normalizeSceneCode(Object value) {
        String sceneCode = stringValue(value);
        return sceneCode.isBlank() ? "default" : sceneCode;
    }

    private static String nullableString(Object value) {
        String text = stringValue(value);
        return text.isBlank() ? null : text;
    }

    private static BigDecimal parseDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = stringValue(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "金额格式不正确");
        }
    }

    private static LocalDate parseDate(Object value) {
        String text = stringValue(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException exception) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "日期格式不正确");
        }
    }

    private static Map<String, Object> handleTaskAction(
            FlowableProcessRuntimeService flowableProcessRuntimeService,
            Map<String, Object> arguments
    ) {
        String taskId = stringValue(arguments.get("taskId"));
        if (taskId.isBlank()) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "缺少 taskId");
        }
        String action = stringValue(arguments.get("action")).toUpperCase();
        String comment = stringValue(arguments.get("comment"));
        return switch (action) {
            case "CLAIM" -> {
                var response = flowableProcessRuntimeService.claim(taskId, new ClaimTaskRequest(comment));
                yield Map.of(
                        "taskId", response.taskId(),
                        "instanceId", response.instanceId(),
                        "status", response.status(),
                        "assigneeUserId", response.assigneeUserId()
                );
            }
            case "REJECT" -> {
                var response = flowableProcessRuntimeService.reject(
                        taskId,
                        new RejectTaskRequest(
                                stringValue(arguments.getOrDefault("targetStrategy", "PREVIOUS")),
                                stringValue(arguments.get("targetTaskId")),
                                stringValue(arguments.get("targetNodeId")),
                                stringValue(arguments.getOrDefault("reapproveStrategy", "CONTINUE")),
                                comment
                        )
                );
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            case "READ" -> {
                var response = flowableProcessRuntimeService.read(taskId);
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            case "COMPLETE", "APPROVE", "" -> {
                var response = flowableProcessRuntimeService.complete(
                        taskId,
                        new CompleteTaskRequest(
                                "APPROVE",
                                null,
                                comment,
                                mapValue(arguments.get("taskFormData"))
                        )
                );
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            default -> throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "暂不支持的任务动作");
        };
    }
}
