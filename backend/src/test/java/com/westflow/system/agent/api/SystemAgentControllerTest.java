package com.westflow.system.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldManageDelegationsAndReflectCurrentUserProxyContext() throws Exception {
        String adminToken = login("wangwu");

        // 代理关系管理页只允许流程管理员访问，先用管理员账号把完整闭环跑通。
        String pageResponse = mockMvc.perform(post("/api/v1/system/agents/page")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "张三",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "ACTIVE"
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
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(pageData.path("records").get(0).path("principalDisplayName").asText()).isNotBlank();

        String optionsResponse = mockMvc.perform(get("/api/v1/system/agents/options")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("principalUsers").isArray()).isTrue();
        assertThat(optionsData.path("delegateUsers").isArray()).isTrue();
        assertThat(optionsData.path("statusOptions")).extracting(item -> item.path("value").asText())
                .contains("ACTIVE", "DISABLED");

        String createResponse = mockMvc.perform(post("/api/v1/system/agents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "principalUserId": "usr_001",
                                  "delegateUserId": "usr_002",
                                  "status": "ACTIVE",
                                  "remark": "张三委托李四"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String agentId = objectMapper.readTree(createResponse).path("data").path("agentId").asText();
        assertThat(agentId).startsWith("agt_");

        String detailResponse = mockMvc.perform(get("/api/v1/system/agents/" + agentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("principalUserId").asText()).isEqualTo("usr_001");
        assertThat(detailData.path("delegateUserId").asText()).isEqualTo("usr_002");
        assertThat(detailData.path("status").asText()).isEqualTo("ACTIVE");

        String delegateToken = login("lisi");
        String currentUserResponse = mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", "Bearer " + delegateToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode currentUser = objectMapper.readTree(currentUserResponse).path("data");
        assertThat(currentUser.path("delegations").isArray()).isTrue();
        assertThat(currentUser.path("delegations")).extracting(item -> item.path("principalUserId").asText())
                .contains("usr_001");

        mockMvc.perform(put("/api/v1/system/agents/" + agentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "principalUserId": "usr_001",
                                  "delegateUserId": "usr_002",
                                  "status": "DISABLED",
                                  "remark": "张三代理关系已停用"
                                }
                                """))
                .andExpect(status().isOk());

        String disabledCurrentUserResponse = mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", "Bearer " + delegateToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(disabledCurrentUserResponse).path("data").path("delegations").size()).isEqualTo(0);
    }

    @Test
    void shouldRejectNonProcessAdminAccess() throws Exception {
        String token = login("zhangsan");

        mockMvc.perform(post("/api/v1/system/agents/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
