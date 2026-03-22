package com.westflow.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldExposeLoginCurrentUserAndContextSwitchContracts() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginBody = objectMapper.readTree(loginResponse);
        assertThat(loginBody.path("code").asText()).isEqualTo("OK");
        assertThat(loginBody.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(loginBody.path("data").path("tokenType").asText()).isEqualTo("Bearer");

        String token = loginBody.path("data").path("accessToken").asText();

        String currentUserResponse = mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode currentUser = objectMapper.readTree(currentUserResponse).path("data");
        assertThat(currentUser.path("userId").asText()).isEqualTo("usr_001");
        assertThat(currentUser.path("mobile").asText()).isEqualTo("13800000000");
        assertThat(currentUser.path("email").asText()).isEqualTo("zhangsan@example.com");
        assertThat(currentUser.path("avatar").asText()).isEmpty();
        assertThat(currentUser.path("roles").isArray()).isTrue();
        assertThat(currentUser.path("roles")).extracting(JsonNode::asText)
                .contains("OA_USER", "DEPT_MANAGER");
        assertThat(currentUser.path("permissions").isArray()).isTrue();
        assertThat(currentUser.path("permissions")).extracting(JsonNode::asText)
                .contains("system:permission-probe", "system:user:view");
        assertThat(currentUser.path("dataScopes").isArray()).isTrue();
        assertThat(currentUser.path("dataScopes").get(0).path("scopeType").asText())
                .isEqualTo("DEPARTMENT_AND_CHILDREN");
        assertThat(currentUser.path("dataScopes").get(0).path("scopeValue").asText())
                .isEqualTo("dept_001");
        assertThat(currentUser.path("partTimePosts").isArray()).isTrue();
        assertThat(currentUser.path("delegations").isArray()).isTrue();
        assertThat(currentUser.path("menus").isArray()).isTrue();
        assertThat(currentUser.path("menus")).extracting(item -> item.path("path").asText())
                .contains("/workbench/todos/list", "/system/users/list");
        assertThat(currentUser.path("aiCapabilities").isArray()).isTrue();
        assertThat(currentUser.path("activePostId").asText()).isEqualTo("post_001");

        mockMvc.perform(post("/api/v1/auth/switch-context")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activePostId": "post_002"
                                }
                                """))
                .andExpect(status().isOk());

        String switchedCurrentUserResponse = mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode switchedCurrentUser = objectMapper.readTree(switchedCurrentUserResponse).path("data");
        assertThat(switchedCurrentUser.path("activePostId").asText()).isEqualTo("post_002");
        assertThat(switchedCurrentUser.path("activeDepartmentId").asText()).isEqualTo("dept_002");
    }

    @Test
    void shouldEnforcePermissionProbeEndpoint() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String adminToken = objectMapper.readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/auth/permission-probe")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectDisabledUserFromDatabase() throws Exception {
        jdbcTemplate.update("UPDATE wf_user SET enabled = FALSE WHERE id = ?", "usr_003");
        try {
            String response = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "wangwu",
                                      "password": "password123"
                                    }
                                    """))
                    .andExpect(status().isForbidden())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode body = objectMapper.readTree(response);
            assertThat(body.path("code").asText()).isEqualTo("AUTH.USER_DISABLED");
        } finally {
            jdbcTemplate.update("UPDATE wf_user SET enabled = TRUE WHERE id = ?", "usr_003");
        }
    }
}
