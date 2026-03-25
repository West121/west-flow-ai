package com.westflow.system.role.controller;

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
class SystemRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPageDetailCreateUpdateAndValidateRoles() throws Exception {
        String token = login();

        String pageResponse = mockMvc.perform(post("/api/v1/system/roles/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "流程",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "roleName",
                                      "direction": "asc"
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
        assertThat(pageData.path("records").get(0).path("roleName").asText()).isNotBlank();

        String detailResponse = mockMvc.perform(get("/api/v1/system/roles/role_dept_manager")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("roleCode").asText()).isEqualTo("DEPT_MANAGER");
        assertThat(detailData.path("menuIds").isArray()).isTrue();
        assertThat(detailData.path("menuIds")).extracting(JsonNode::asText)
                .contains("menu_org", "menu_system_user", "menu_system_permission_probe");
        assertThat(detailData.path("dataScopes").isArray()).isTrue();

        String createResponse = mockMvc.perform(post("/api/v1/system/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "财务管理员",
                                  "roleCode": "FINANCE_ADMIN",
                                  "roleCategory": "BUSINESS",
                                  "description": "负责财务流程和财务数据权限管理",
                                  "menuIds": ["menu_system_menu", "menu_system_user"],
                                  "dataScopes": [
                                    {
                                      "scopeType": "DEPARTMENT",
                                      "scopeValue": "dept_001"
                                    },
                                    {
                                      "scopeType": "SELF",
                                      "scopeValue": "usr_001"
                                    }
                                  ],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String roleId = objectMapper.readTree(createResponse).path("data").path("roleId").asText();
        assertThat(roleId).startsWith("role_");

        mockMvc.perform(post("/api/v1/system/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "财务管理员（重复编码）",
                                  "roleCode": "FINANCE_ADMIN",
                                  "roleCategory": "BUSINESS",
                                  "description": "",
                                  "menuIds": ["menu_system_menu"],
                                  "dataScopes": [],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/system/roles/" + roleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "财务管理员（已更新）",
                                  "roleCode": "FINANCE_ADMIN",
                                  "roleCategory": "BUSINESS",
                                  "description": "已切换到公司级数据权限",
                                  "menuIds": ["menu_system_menu", "menu_system_user", "menu_system_company"],
                                  "dataScopes": [
                                    {
                                      "scopeType": "COMPANY",
                                      "scopeValue": "cmp_001"
                                    }
                                  ],
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        String updatedResponse = mockMvc.perform(get("/api/v1/system/roles/" + roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updatedData = objectMapper.readTree(updatedResponse).path("data");
        assertThat(updatedData.path("roleName").asText()).isEqualTo("财务管理员（已更新）");
        assertThat(updatedData.path("enabled").asBoolean()).isFalse();
        assertThat(updatedData.path("menuIds")).extracting(JsonNode::asText)
                .contains("menu_system_company");
        assertThat(updatedData.path("dataScopes").get(0).path("scopeType").asText()).isEqualTo("COMPANY");

        String optionsResponse = mockMvc.perform(get("/api/v1/system/roles/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("menus").isArray()).isTrue();
        assertThat(optionsData.path("scopeTypes").isArray()).isTrue();
        assertThat(optionsData.path("companies").isArray()).isTrue();
        assertThat(optionsData.path("departments").isArray()).isTrue();
        assertThat(optionsData.path("users").isArray()).isTrue();

        String usersResponse = mockMvc.perform(get("/api/v1/system/roles/role_dept_manager/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode usersData = objectMapper.readTree(usersResponse).path("data");
        assertThat(usersData.isArray()).isTrue();
        assertThat(usersData).extracting((JsonNode node) -> node.path("userId").asText())
                .contains("usr_001");
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
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

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
