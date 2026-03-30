package com.westflow.ai.service;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 注册表启动同步服务，用于补齐默认注册数据。
 */
@Service
public class AiRegistryBootstrapService {

    private final JdbcTemplate jdbcTemplate;

    public AiRegistryBootstrapService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动后同步 AI 注册表默认种子。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        syncAgents();
        syncTools();
        syncMcps();
        syncSkills();
    }

    private void syncAgents() {
        upsertAgent(new AgentSeed(
                "ai_agent_supervisor",
                "supervisor-agent",
                "Supervisor 智能体",
                "ai:copilot:open",
                "负责协调写操作确认、技能调用和多智能体收敛。",
                "{\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"routeMode\":\"SUPERVISOR\",\"supervisor\":true,\"priority\":100}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_routing",
                "routing-agent",
                "Routing 智能体",
                "ai:copilot:open",
                "负责根据当前问题把请求路由给最合适的业务智能体。",
                "{\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"routeMode\":\"ROUTING\",\"priority\":90}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_001",
                "workflow-design-agent",
                "流程设计智能体",
                "ai:workflow:design",
                "负责根据业务意图生成流程定义、节点建议和发布前检查。",
                "{\"businessDomains\":[\"OA\",\"PLM\"],\"routeMode\":\"REACT\",\"priority\":80}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_002",
                "smart-form-agent",
                "智能填报智能体",
                "ai:process:start",
                "负责根据文本输入生成表单草稿和流程发起建议。",
                "{\"businessDomains\":[\"OA\",\"PLM\"],\"routeMode\":\"REACT\",\"priority\":70}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_003",
                "task-handle-agent",
                "待办处理智能体",
                "ai:task:handle",
                "负责解释待办、处理建议和受控动作确认。",
                "{\"businessDomains\":[\"OA\",\"PLM\"],\"routeMode\":\"REACT\",\"priority\":85}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_004",
                "stats-agent",
                "统计问答智能体",
                "ai:stats:query",
                "负责查询流程、OA、PLM 的指标、趋势和统计摘要。",
                "{\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"routeMode\":\"REACT\",\"priority\":60}"
        ));
        upsertAgent(new AgentSeed(
                "ai_agent_005",
                "plm-assistant-agent",
                "PLM 助手",
                "ai:plm:assist",
                "负责解释 PLM 变更流程、ECR/ECO 和物料变更相关问题。",
                "{\"businessDomains\":[\"PLM\"],\"routeMode\":\"REACT\",\"priority\":75}"
        ));
    }

    private void syncTools() {
        upsertTool(new ToolSeed("ai_tool_001", "workflow.definition.list", "查询流程定义", "PLATFORM", "READ", "ai:copilot:open", "{\"resource\":\"process-definition\",\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"triggerKeywords\":[\"流程定义\",\"流程列表\",\"发布记录\",\"流程版本\"],\"routePrefixes\":[\"/workflow/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":70}"));
        upsertTool(new ToolSeed("ai_tool_002", "process.start", "发起流程", "PLATFORM", "WRITE", "ai:process:start", "{\"resource\":\"process-instance\",\"confirmRequired\":true,\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"发起\",\"提交\",\"创建流程\"],\"routePrefixes\":[\"/oa/\",\"/plm/\",\"/workflow/\"],\"priority\":90}"));
        upsertTool(new ToolSeed("ai_tool_003", "task.query", "查询待办", "PLATFORM", "READ", "ai:copilot:open", "{\"resource\":\"task\",\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"triggerKeywords\":[\"待办\",\"轨迹\",\"路径\",\"会签\",\"审批\"],\"routePrefixes\":[\"/workbench/\",\"/oa/\",\"/plm/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":95}"));
        upsertTool(new ToolSeed("ai_tool_004", "task.handle", "处理待办", "PLATFORM", "WRITE", "ai:task:handle", "{\"resource\":\"task-action\",\"confirmRequired\":true,\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"处理\",\"通过\",\"拒绝\",\"驳回\",\"退回\",\"认领\",\"完成\"],\"routePrefixes\":[\"/workbench/\",\"/oa/\",\"/plm/\"],\"priority\":100}"));
        upsertTool(new ToolSeed("ai_tool_005", "stats.query", "查询统计", "PLATFORM", "READ", "ai:stats:query", "{\"resource\":\"stats\",\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"triggerKeywords\":[\"统计\",\"报表\",\"指标\",\"趋势\"],\"routePrefixes\":[\"/workflow/\",\"/system/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":80}"));
        upsertTool(new ToolSeed("ai_tool_006", "plm.bill.query", "查询 PLM 单据", "PLATFORM", "READ", "ai:plm:assist", "{\"resource\":\"plm-bill\",\"businessDomains\":[\"PLM\"],\"triggerKeywords\":[\"PLM\",\"ECR\",\"ECO\",\"物料\",\"变更\"],\"routePrefixes\":[\"/plm/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":90}"));
        upsertTool(new ToolSeed("ai_tool_legacy_001", "workflow.todo.list", "查询待办", "PLATFORM", "READ", "ai:copilot:open", "{\"aliasOf\":\"task.query\",\"businessDomains\":[\"OA\",\"PLM\",\"GENERAL\"],\"triggerKeywords\":[\"待办\"],\"routePrefixes\":[\"/workbench/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":60}"));
        upsertTool(new ToolSeed("ai_tool_legacy_002", "workflow.trace.summary", "查询轨迹摘要", "SKILL", "READ", "ai:copilot:open", "{\"aliasOf\":\"task.query\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"轨迹\",\"路径\",\"流程图\",\"会签\"],\"routePrefixes\":[\"/workbench/\",\"/oa/\",\"/plm/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":92}"));
        upsertTool(new ToolSeed("ai_tool_legacy_003", "plm.change.summary", "查询 PLM 单据", "SKILL", "READ", "ai:plm:assist", "{\"aliasOf\":\"plm.bill.query\",\"businessDomains\":[\"PLM\"],\"triggerKeywords\":[\"PLM\",\"ECR\",\"ECO\",\"物料\",\"变更\"],\"routePrefixes\":[\"/plm/\"],\"mcpCode\":\"westflow-internal-mcp\",\"priority\":91}"));
        upsertTool(new ToolSeed("ai_tool_legacy_004", "workflow.task.complete", "完成待办", "AGENT", "WRITE", "ai:task:handle", "{\"aliasOf\":\"task.handle\",\"action\":\"COMPLETE\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"完成\",\"通过\"],\"routePrefixes\":[\"/workbench/\",\"/oa/\",\"/plm/\"],\"priority\":85}"));
        upsertTool(new ToolSeed("ai_tool_legacy_005", "workflow.task.reject", "驳回待办", "AGENT", "WRITE", "ai:task:handle", "{\"aliasOf\":\"task.handle\",\"action\":\"REJECT\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"驳回\",\"拒绝\"],\"routePrefixes\":[\"/workbench/\",\"/oa/\",\"/plm/\"],\"priority\":85}"));
    }

    private void syncMcps() {
        upsertMcp(new McpSeed(
                "ai_mcp_001",
                "westflow-internal-mcp",
                "平台内置 MCP 桥",
                null,
                "INTERNAL",
                "ai:copilot:open",
                "{\"description\":\"统一桥接平台内置能力和外部 MCP 能力\"}"
        ));
    }

    private void syncSkills() {
        upsertSkill(new SkillSeed(
                "ai_skill_001",
                "workflow-design-skill",
                "流程设计技能",
                "classpath:ai/skills/workflow-design-skill.md",
                "ai:workflow:design",
                "{\"type\":\"local-skill\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"流程\",\"设计\",\"节点\",\"发布\"],\"priority\":90}"
        ));
        upsertSkill(new SkillSeed(
                "ai_skill_002",
                "approval-trace",
                "审批轨迹技能",
                "classpath:ai/skills/approval-trace-skill.md",
                "ai:copilot:open",
                "{\"type\":\"local-skill\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"轨迹\",\"路径\",\"流程图\",\"会签\"],\"priority\":85}"
        ));
        upsertSkill(new SkillSeed(
                "ai_skill_003",
                "plm-change-summary",
                "PLM 变更摘要技能",
                "classpath:ai/skills/plm-change-summary-skill.md",
                "ai:plm:assist",
                "{\"type\":\"local-skill\",\"businessDomains\":[\"PLM\"],\"triggerKeywords\":[\"PLM\",\"ECR\",\"ECO\",\"物料\"],\"priority\":88}"
        ));
        upsertSkill(new SkillSeed(
                "ai_skill_004",
                "submission-advice",
                "发起建议技能",
                "classpath:ai/skills/submission-advice-skill.md",
                "ai:process:start",
                "{\"type\":\"local-skill\",\"businessDomains\":[\"OA\",\"PLM\"],\"triggerKeywords\":[\"发起\",\"提交\",\"填写\",\"表单\"],\"priority\":80}"
        ));
    }

    private void upsertAgent(AgentSeed seed) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_ai_agent_registry WHERE agent_code = ?",
                Long.class,
                seed.agentCode()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                            UPDATE wf_ai_agent_registry
                            SET agent_name = ?, capability_code = ?, enabled = TRUE, system_prompt = ?, metadata_json = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE agent_code = ?
                            """,
                    seed.agentName(),
                    seed.capabilityCode(),
                    seed.systemPrompt(),
                    seed.metadataJson(),
                    seed.agentCode()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO wf_ai_agent_registry (id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                seed.id(),
                seed.agentCode(),
                seed.agentName(),
                seed.capabilityCode(),
                seed.systemPrompt(),
                seed.metadataJson()
        );
    }

    private void upsertTool(ToolSeed seed) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_ai_tool_registry WHERE tool_code = ?",
                Long.class,
                seed.toolCode()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                            UPDATE wf_ai_tool_registry
                            SET tool_name = ?, tool_category = ?, action_mode = ?, required_capability_code = ?, enabled = TRUE, metadata_json = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE tool_code = ?
                            """,
                    seed.toolName(),
                    seed.toolCategory(),
                    seed.actionMode(),
                    seed.requiredCapabilityCode(),
                    seed.metadataJson(),
                    seed.toolCode()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO wf_ai_tool_registry (id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                seed.id(),
                seed.toolCode(),
                seed.toolName(),
                seed.toolCategory(),
                seed.actionMode(),
                seed.requiredCapabilityCode(),
                seed.metadataJson()
        );
    }

    private void upsertMcp(McpSeed seed) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_ai_mcp_registry WHERE mcp_code = ?",
                Long.class,
                seed.mcpCode()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                            UPDATE wf_ai_mcp_registry
                            SET mcp_name = ?, endpoint_url = ?, transport_type = ?, required_capability_code = ?, enabled = TRUE, metadata_json = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE mcp_code = ?
                            """,
                    seed.mcpName(),
                    seed.endpointUrl(),
                    seed.transportType(),
                    seed.requiredCapabilityCode(),
                    seed.metadataJson(),
                    seed.mcpCode()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO wf_ai_mcp_registry (id, mcp_code, mcp_name, endpoint_url, transport_type, required_capability_code, enabled, metadata_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                seed.id(),
                seed.mcpCode(),
                seed.mcpName(),
                seed.endpointUrl(),
                seed.transportType(),
                seed.requiredCapabilityCode(),
                seed.metadataJson()
        );
    }

    private void upsertSkill(SkillSeed seed) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_ai_skill_registry WHERE skill_code = ?",
                Long.class,
                seed.skillCode()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                            UPDATE wf_ai_skill_registry
                            SET skill_name = ?, skill_path = ?, required_capability_code = ?, enabled = TRUE, metadata_json = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE skill_code = ?
                            """,
                    seed.skillName(),
                    seed.skillPath(),
                    seed.requiredCapabilityCode(),
                    seed.metadataJson(),
                    seed.skillCode()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO wf_ai_skill_registry (id, skill_code, skill_name, skill_path, required_capability_code, enabled, metadata_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                seed.id(),
                seed.skillCode(),
                seed.skillName(),
                seed.skillPath(),
                seed.requiredCapabilityCode(),
                seed.metadataJson()
        );
    }

    private record AgentSeed(
            String id,
            String agentCode,
            String agentName,
            String capabilityCode,
            String systemPrompt,
            String metadataJson
    ) {
    }

    private record ToolSeed(
            String id,
            String toolCode,
            String toolName,
            String toolCategory,
            String actionMode,
            String requiredCapabilityCode,
            String metadataJson
    ) {
    }

    private record McpSeed(
            String id,
            String mcpCode,
            String mcpName,
            String endpointUrl,
            String transportType,
            String requiredCapabilityCode,
            String metadataJson
    ) {
    }

    private record SkillSeed(
            String id,
            String skillCode,
            String skillName,
            String skillPath,
            String requiredCapabilityCode,
            String metadataJson
    ) {
    }
}
