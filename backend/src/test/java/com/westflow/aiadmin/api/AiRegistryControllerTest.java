package com.westflow.aiadmin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.AiAdminTestSupport;
import com.westflow.aiadmin.AiAdminTestApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 注册表后台接口测试。
 */
@SpringBootTest(classes = AiAdminTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearRegistries() {
        jdbcTemplate.update("DELETE FROM wf_ai_agent_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_mcp_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_skill_registry");
    }

    /**
     * 构造带元数据的请求体，避免手写 JSON 时出现转义错误。
     */
    private String buildRequestBody(ObjectMapper objectMapper, java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> consumer) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode();
        consumer.accept(body);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void shouldManageAgentRegistry() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);
        String code = "agent_" + UUID.randomUUID().toString().substring(0, 8);

        String createResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody(objectMapper, body -> {
                            body.put("agentCode", code);
                            body.put("agentName", "流程设计智能体");
                            body.put("capabilityCode", "ai:workflow:design");
                            body.put("enabled", true);
                            body.put("systemPrompt", "负责生成流程建议");
                            body.put("metadataJson", "{\"scene\":\"oa\"}");
                        })), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String agentId = objectMapper.readTree(createResponse).path("data").path("agentId").asText();
        assertThat(agentId).startsWith("ai_agent_");

        String detailResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/agents/" + agentId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(detailResponse).path("data").path("agentCode").asText()).isEqualTo(code);

        String pageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/agents/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "%s",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "ENABLED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """.formatted(code)), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("agentCode").asText()).isEqualTo(code);

        String updateResponse = mockMvc.perform(AiAdminTestSupport.withBearer(put("/api/v1/system/ai/agents/" + agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody(objectMapper, body -> {
                            body.put("agentCode", code);
                            body.put("agentName", "流程设计智能体-更新");
                            body.put("capabilityCode", "ai:workflow:design");
                            body.put("enabled", false);
                            body.put("systemPrompt", "更新后的提示词");
                            body.put("metadataJson", "{\"scene\":\"plm\"}");
                        })), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(updateResponse).path("data").path("agentId").asText()).isEqualTo(agentId);

        String optionsResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/agents/options"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("capabilityOptions").isArray()).isTrue();
        assertThat(optionsData.path("statusOptions").size()).isEqualTo(2);
    }

    @Test
    void shouldManageToolMcpAndSkillRegistries() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String toolCode = "tool_" + UUID.randomUUID().toString().substring(0, 8);
        String toolCreateResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody(objectMapper, body -> {
                            body.put("toolCode", toolCode);
                            body.put("toolName", "流程发起工具");
                            body.put("toolCategory", "PLATFORM");
                            body.put("actionMode", "WRITE");
                            body.put("requiredCapabilityCode", "ai:process:start");
                            body.put("enabled", true);
                            body.put("metadataJson", "{\"scope\":\"oa\"}");
                        })), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String toolId = objectMapper.readTree(toolCreateResponse).path("data").path("toolId").asText();
        assertThat(toolId).startsWith("ai_tool_");
        assertThat(mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/tools/" + toolId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).contains(toolCode);

        String toolPageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/tools/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "%s",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "ENABLED"
                                    }
                                  ],
                                  "sorts": [],
                                  "groups": []
                                }
                                """.formatted(toolCode)), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(toolPageResponse).path("data").path("total").asInt()).isEqualTo(1);

        String mcpCode = "mcp_" + UUID.randomUUID().toString().substring(0, 8);
        String mcpCreateResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/mcps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody(objectMapper, body -> {
                            body.put("mcpCode", mcpCode);
                            body.put("mcpName", "外部知识库");
                            body.put("endpointUrl", "http://localhost:18080/mcp");
                            body.put("transportType", "STREAMABLE_HTTP");
                            body.put("requiredCapabilityCode", "ai:copilot:open");
                            body.put("enabled", true);
                            body.put("metadataJson", "{\"vendor\":\"demo\"}");
                        })), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String mcpId = objectMapper.readTree(mcpCreateResponse).path("data").path("mcpId").asText();
        assertThat(mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/mcps/" + mcpId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).contains(mcpCode);

        String mcpOptionsResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/mcps/options"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(mcpOptionsResponse).path("data").path("transportTypeOptions").size()).isEqualTo(3);

        String skillCode = "skill_" + UUID.randomUUID().toString().substring(0, 8);
        String skillCreateResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody(objectMapper, body -> {
                            body.put("skillCode", skillCode);
                            body.put("skillName", "流程设计技能");
                            body.put("skillPath", "/Users/west/.agents/skills/using-superpowers/SKILL.md");
                            body.put("requiredCapabilityCode", "ai:workflow:design");
                            body.put("enabled", true);
                            body.put("metadataJson", "{\"type\":\"local\"}");
                        })), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String skillId = objectMapper.readTree(skillCreateResponse).path("data").path("skillId").asText();
        assertThat(mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/skills/" + skillId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).contains(skillCode);

        String skillPageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/skills/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "%s",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """.formatted(skillCode)), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(skillPageResponse).path("data").path("total").asInt()).isEqualTo(1);
    }
}
