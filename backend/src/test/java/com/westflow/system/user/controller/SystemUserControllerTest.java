package com.westflow.system.user.controller;

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
class SystemUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPageDetailCreateAndUpdateUsers() throws Exception {
        String token = login("admin", "admin123");

        String pageResponse = mockMvc.perform(post("/api/v1/system/users/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "张",
                                  "filters": [],
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

        JsonNode pageBody = objectMapper.readTree(pageResponse);
        assertThat(pageBody.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(pageBody.path("data").path("records").get(0).path("userId").asText()).isEqualTo("usr_001");
        assertThat(pageBody.path("data").path("records").get(0).path("displayName").asText()).contains("张");

        String detailResponse = mockMvc.perform(get("/api/v1/system/users/usr_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(detailResponse).path("data").path("departmentName").asText()).isEqualTo("财务部");
        assertThat(objectMapper.readTree(detailResponse).path("data").path("roleIds").isArray()).isTrue();

        String createResponse = mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "赵六",
                                  "username": "zhaoliu",
                                  "mobile": "13600000000",
                                  "email": "zhaoliu@example.com",
                                  "companyId": "cmp_001",
                                  "primaryPostId": "post_001",
                                  "roleIds": ["role_oa_user", "role_dept_manager"],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userId = objectMapper.readTree(createResponse).path("data").path("userId").asText();
        assertThat(userId).startsWith("usr_");

        String createdDetail = mockMvc.perform(get("/api/v1/system/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(createdDetail).path("data").path("roleIds").size()).isEqualTo(2);

        mockMvc.perform(put("/api/v1/system/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "赵六（已更新）",
                                  "username": "zhaoliu",
                                  "mobile": "13600000001",
                                  "email": "zhaoliu-updated@example.com",
                                  "companyId": "cmp_001",
                                  "primaryPostId": "post_001",
                                  "roleIds": ["role_process_admin"],
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        String updatedDetail = mockMvc.perform(get("/api/v1/system/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updatedBody = objectMapper.readTree(updatedDetail).path("data");
        assertThat(updatedBody.path("displayName").asText()).isEqualTo("赵六（已更新）");
        assertThat(updatedBody.path("postName").asText()).isEqualTo("报销审核岗");
        assertThat(updatedBody.path("enabled").asBoolean()).isFalse();
        assertThat(updatedBody.path("roleIds").size()).isEqualTo(1);
        assertThat(updatedBody.path("roleIds").get(0).asText()).isEqualTo("role_process_admin");
    }

    @Test
    void shouldEnforceDataScopeOnDetailAndCreate() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/v1/system/users/usr_002")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "跨部门人员",
                                  "username": "crossdept",
                                  "mobile": "13600000010",
                                  "email": "crossdept@example.com",
                                  "companyId": "cmp_001",
                                  "primaryPostId": "post_003",
                                  "roleIds": ["role_oa_user"],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnFormOptions() throws Exception {
        String token = login();

        String response = mockMvc.perform(get("/api/v1/system/users/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("companies").isArray()).isTrue();
        assertThat(data.path("companies").size()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("posts").isArray()).isTrue();
        assertThat(data.path("posts").size()).isGreaterThanOrEqualTo(3);
        assertThat(data.path("roles").isArray()).isTrue();
        assertThat(data.path("roles").size()).isGreaterThanOrEqualTo(1);
    }

    private String login() throws Exception {
        return login("zhangsan", "password123");
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
