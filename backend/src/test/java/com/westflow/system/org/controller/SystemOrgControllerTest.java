package com.westflow.system.org.controller;

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
class SystemOrgControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPageDetailCreateUpdateAndDuplicateValidateCompanies() throws Exception {
        String token = login();

        String pageResponse = mockMvc.perform(post("/api/v1/system/companies/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "西流",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "companyName",
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
        assertThat(pageData.path("records").get(0).path("companyName").asText()).contains("西流");

        String detailResponse = mockMvc.perform(get("/api/v1/system/companies/cmp_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(detailResponse).path("data").path("companyName").asText()).isEqualTo("西流科技");

        String createResponse = mockMvc.perform(post("/api/v1/system/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "西流制造",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String companyId = objectMapper.readTree(createResponse).path("data").path("companyId").asText();
        assertThat(companyId).startsWith("cmp_");

        mockMvc.perform(put("/api/v1/system/companies/" + companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "西流制造（已更新）",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/companies/" + companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/system/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "西流制造（已更新）",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        String optionsResponse = mockMvc.perform(get("/api/v1/system/companies/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(optionsResponse).path("data").path("companies").isArray()).isTrue();
    }

    @Test
    void shouldPageDetailCreateUpdateAndValidateDepartments() throws Exception {
        String token = login();

        String createCompanyResponse = mockMvc.perform(post("/api/v1/system/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "西流教育",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String companyId = objectMapper.readTree(createCompanyResponse).path("data").path("companyId").asText();

        String pageResponse = mockMvc.perform(post("/api/v1/system/departments/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "财务",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "departmentName",
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
        assertThat(objectMapper.readTree(pageResponse).path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);

        String detailResponse = mockMvc.perform(get("/api/v1/system/departments/dept_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode seededDepartment = objectMapper.readTree(detailResponse).path("data");
        assertThat(seededDepartment.path("departmentName").asText()).isEqualTo("财务部");
        assertThat(seededDepartment.path("rootDepartmentId").asText()).isEqualTo("dept_001");
        assertThat(seededDepartment.path("treeLevel").asInt()).isEqualTo(1);
        assertThat(seededDepartment.path("treePath").asText()).isEqualTo("dept_001");

        String createResponse = mockMvc.perform(post("/api/v1/system/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "cmp_001",
                                  "parentDepartmentId": "",
                                  "departmentName": "运营中心",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String departmentId = objectMapper.readTree(createResponse).path("data").path("departmentId").asText();

        mockMvc.perform(post("/api/v1/system/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "cmp_001",
                                  "parentDepartmentId": "",
                                  "departmentName": "运营中心",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        String childCreateResponse = mockMvc.perform(post("/api/v1/system/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "cmp_001",
                                  "parentDepartmentId": "%s",
                                  "departmentName": "运营一组",
                                  "enabled": true
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String childDepartmentId = objectMapper.readTree(childCreateResponse).path("data").path("departmentId").asText();

        String supportCreateResponse = mockMvc.perform(post("/api/v1/system/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "cmp_001",
                                  "parentDepartmentId": "dept_001",
                                  "departmentName": "支持中心",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String supportDepartmentId = objectMapper.readTree(supportCreateResponse).path("data").path("departmentId").asText();

        mockMvc.perform(put("/api/v1/system/departments/" + departmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "cmp_001",
                                  "parentDepartmentId": "%s",
                                  "departmentName": "运营中心（已更新）",
                                  "enabled": false
                                }
                                """.formatted(supportDepartmentId)))
                .andExpect(status().isOk());

        String updatedResponse = mockMvc.perform(get("/api/v1/system/departments/" + departmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updatedData = objectMapper.readTree(updatedResponse).path("data");
        assertThat(updatedData.path("departmentName").asText()).isEqualTo("运营中心（已更新）");
        assertThat(updatedData.path("parentDepartmentId").asText()).isEqualTo(supportDepartmentId);
        assertThat(updatedData.path("rootDepartmentId").asText()).isEqualTo("dept_001");
        assertThat(updatedData.path("treeLevel").asInt()).isEqualTo(3);
        assertThat(updatedData.path("treePath").asText()).isEqualTo("dept_001/" + supportDepartmentId + "/" + departmentId);

        String updatedChildResponse = mockMvc.perform(get("/api/v1/system/departments/" + childDepartmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updatedChild = objectMapper.readTree(updatedChildResponse).path("data");
        assertThat(updatedChild.path("parentDepartmentId").asText()).isEqualTo(departmentId);
        assertThat(updatedChild.path("rootDepartmentId").asText()).isEqualTo("dept_001");
        assertThat(updatedChild.path("treeLevel").asInt()).isEqualTo(4);
        assertThat(updatedChild.path("treePath").asText()).isEqualTo("dept_001/" + supportDepartmentId + "/" + departmentId + "/" + childDepartmentId);

        String ancestorsResponse = mockMvc.perform(get("/api/v1/system/departments/" + childDepartmentId + "/ancestors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ancestors = objectMapper.readTree(ancestorsResponse).path("data");
        assertThat(ancestors.isArray()).isTrue();
        assertThat(ancestors).extracting((JsonNode node) -> node.path("departmentId").asText())
                .containsExactly("dept_001", supportDepartmentId, departmentId, childDepartmentId);

        String subtreeResponse = mockMvc.perform(get("/api/v1/system/departments/" + supportDepartmentId + "/subtree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode subtree = objectMapper.readTree(subtreeResponse).path("data");
        assertThat(subtree.path("departmentId").asText()).isEqualTo(supportDepartmentId);
        assertThat(subtree.path("children").isArray()).isTrue();
        assertThat(subtree.path("children").get(0).path("departmentId").asText()).isEqualTo(departmentId);
        assertThat(subtree.path("children").get(0).path("children").get(0).path("departmentId").asText()).isEqualTo(childDepartmentId);

        String treeResponse = mockMvc.perform(get("/api/v1/system/departments/tree")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", "cmp_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tree = objectMapper.readTree(treeResponse).path("data");
        assertThat(tree.isArray()).isTrue();
        assertThat(tree.findValuesAsText("departmentId")).contains(supportDepartmentId, departmentId, childDepartmentId, "dept_001");

        mockMvc.perform(post("/api/v1/system/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "parentDepartmentId": "dept_001",
                                  "departmentName": "教务中心",
                                  "enabled": true
                                }
                                """.formatted(companyId)))
                .andExpect(status().isBadRequest());

        String optionsResponse = mockMvc.perform(get("/api/v1/system/departments/options")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", "cmp_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("companies").isArray()).isTrue();
        assertThat(optionsData.path("parentDepartments").isArray()).isTrue();
        JsonNode nestedDepartment = null;
        for (JsonNode item : optionsData.path("parentDepartments")) {
            if (departmentId.equals(item.path("id").asText())) {
                nestedDepartment = item;
                break;
            }
        }
        assertThat(nestedDepartment).isNotNull();
        assertThat(nestedDepartment.path("parentDepartmentId").asText()).isEqualTo(supportDepartmentId);
    }

    @Test
    void shouldListAssociatedUsersForDepartmentAndPost() throws Exception {
        String token = login();

        String departmentUsersResponse = mockMvc.perform(get("/api/v1/system/departments/dept_001/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode departmentUsers = objectMapper.readTree(departmentUsersResponse).path("data");
        assertThat(departmentUsers.isArray()).isTrue();
        assertThat(departmentUsers).extracting((JsonNode node) -> node.path("userId").asText())
                .contains("usr_001");

        String postUsersResponse = mockMvc.perform(get("/api/v1/system/posts/post_001/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode postUsers = objectMapper.readTree(postUsersResponse).path("data");
        assertThat(postUsers.isArray()).isTrue();
        assertThat(postUsers).extracting((JsonNode node) -> node.path("userId").asText())
                .contains("usr_001");
    }

    @Test
    void shouldRestrictCompanyDepartmentAndPostViewsByDataScope() throws Exception {
        String token = login();

        String createCompanyResponse = mockMvc.perform(post("/api/v1/system/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "权限隔离公司",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String companyId = objectMapper.readTree(createCompanyResponse).path("data").path("companyId").asText();

        String companyPageResponse = mockMvc.perform(post("/api/v1/system/companies/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "权限隔离公司",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(companyPageResponse).path("data").path("total").asLong()).isEqualTo(0);

        mockMvc.perform(get("/api/v1/system/companies/" + companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String departmentPageResponse = mockMvc.perform(post("/api/v1/system/departments/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "信息管理部",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(departmentPageResponse).path("data").path("total").asLong()).isEqualTo(0);

        mockMvc.perform(get("/api/v1/system/departments/dept_003")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String postPageResponse = mockMvc.perform(post("/api/v1/system/posts/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "流程管理员",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(postPageResponse).path("data").path("total").asLong()).isEqualTo(0);

        mockMvc.perform(get("/api/v1/system/posts/post_003")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPageDetailCreateUpdateAndValidatePosts() throws Exception {
        String token = login();

        String pageResponse = mockMvc.perform(post("/api/v1/system/posts/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "报销",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "postName",
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
        assertThat(objectMapper.readTree(pageResponse).path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);

        String detailResponse = mockMvc.perform(get("/api/v1/system/posts/post_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(detailResponse).path("data").path("postName").asText()).isEqualTo("报销审核岗");

        String createResponse = mockMvc.perform(post("/api/v1/system/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId": "dept_001",
                                  "postName": "付款复核岗",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String postId = objectMapper.readTree(createResponse).path("data").path("postId").asText();

        mockMvc.perform(put("/api/v1/system/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId": "dept_002",
                                  "postName": "付款复核岗（已更新）",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/system/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId": "dept_001",
                                  "postName": "报销审核岗",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        String optionsResponse = mockMvc.perform(get("/api/v1/system/posts/options")
                        .header("Authorization", "Bearer " + token)
                        .param("companyId", "cmp_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(optionsResponse).path("data").path("departments").isArray()).isTrue();
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
