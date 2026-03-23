package com.westflow.system.menu.api;

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
class SystemMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPageDetailCreateUpdateAndValidateMenus() throws Exception {
        String token = login();

        String pageResponse = mockMvc.perform(post("/api/v1/system/menus/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "系统",
                                  "filters": [
                                    {
                                      "field": "menuType",
                                      "operator": "eq",
                                      "value": "DIRECTORY"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "sortOrder",
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
        assertThat(pageData.path("records").get(0).path("menuType").asText()).isEqualTo("DIRECTORY");

        String detailResponse = mockMvc.perform(get("/api/v1/system/menus/menu_system_user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(detailResponse).path("data").path("menuName").asText()).isEqualTo("用户管理");

        String createResponse = mockMvc.perform(post("/api/v1/system/menus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentMenuId": "menu_system",
                                  "menuName": "字典管理",
                                  "menuType": "MENU",
                                  "routePath": "/system/dicts/list",
                                  "componentPath": "system/dicts/list",
                                  "permissionCode": "system:dict:view",
                                  "iconName": "BookMarked",
                                  "sortOrder": 70,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String menuId = objectMapper.readTree(createResponse).path("data").path("menuId").asText();
        assertThat(menuId).startsWith("menu_");

        mockMvc.perform(post("/api/v1/system/menus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentMenuId": "menu_system",
                                  "menuName": "字典管理",
                                  "menuType": "MENU",
                                  "routePath": "/system/dicts/list",
                                  "componentPath": "system/dicts/list",
                                  "permissionCode": "system:dict:view",
                                  "iconName": "BookMarked",
                                  "sortOrder": 80,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/system/menus/" + menuId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentMenuId": "menu_workflow",
                                  "menuName": "字典管理（已更新）",
                                  "menuType": "MENU",
                                  "routePath": "/system/dicts/list",
                                  "componentPath": "system/dicts/list",
                                  "permissionCode": "system:dict:view",
                                  "iconName": "BookMarked",
                                  "sortOrder": 15,
                                  "visible": false,
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        String updatedResponse = mockMvc.perform(get("/api/v1/system/menus/" + menuId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updatedData = objectMapper.readTree(updatedResponse).path("data");
        assertThat(updatedData.path("menuName").asText()).isEqualTo("字典管理（已更新）");
        assertThat(updatedData.path("parentMenuId").asText()).isEqualTo("menu_workflow");
        assertThat(updatedData.path("visible").asBoolean()).isFalse();
        assertThat(updatedData.path("enabled").asBoolean()).isFalse();

        mockMvc.perform(put("/api/v1/system/menus/menu_system")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentMenuId": "menu_system_user_create",
                                  "menuName": "系统管理",
                                  "menuType": "DIRECTORY",
                                  "routePath": "/system",
                                  "componentPath": "",
                                  "permissionCode": "",
                                  "iconName": "ShieldCheck",
                                  "sortOrder": 20,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/system/menus/menu_system")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentMenuId": "menu_system",
                                  "menuName": "系统管理",
                                  "menuType": "DIRECTORY",
                                  "routePath": "/system",
                                  "componentPath": "",
                                  "permissionCode": "",
                                  "iconName": "ShieldCheck",
                                  "sortOrder": 20,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        String optionsResponse = mockMvc.perform(get("/api/v1/system/menus/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("menuTypes").isArray()).isTrue();
        assertThat(optionsData.path("parentMenus").isArray()).isTrue();
    }

    @Test
    void shouldReturnMenuTreeAndSidebarTree() throws Exception {
        String token = login();

        String treeResponse = mockMvc.perform(get("/api/v1/system/menus/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode treeData = objectMapper.readTree(treeResponse).path("data");
        assertThat(treeData.isArray()).isTrue();
        assertThat(treeData.findValuesAsText("menuId")).contains("menu_workbench", "menu_system", "menu_org", "menu_system_role");

        String sidebarResponse = mockMvc.perform(get("/api/v1/system/menus/sidebar-tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode sidebarData = objectMapper.readTree(sidebarResponse).path("data");
        assertThat(sidebarData.isArray()).isTrue();
        assertThat(sidebarData.findValuesAsText("menuId")).contains(
                "menu_workbench",
                "menu_workbench_dashboard",
                "menu_org"
        );
        assertThat(sidebarData.findValuesAsText("menuType")).doesNotContain("PERMISSION");
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
