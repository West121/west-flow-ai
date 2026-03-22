package com.westflow.system.dict.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.system.dict.type.mapper.SystemDictTypeMapper;
import com.westflow.system.dict.item.mapper.SystemDictItemMapper;
import org.junit.jupiter.api.BeforeEach;
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
class SystemDictTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemDictTypeMapper systemDictTypeMapper;

    @Autowired
    private SystemDictItemMapper systemDictItemMapper;

    @BeforeEach
    void resetStorage() {
        systemDictTypeMapper.clear();
        systemDictItemMapper.clear();
    }

    @Test
    void shouldManageDictTypesWithPageAndFilters() throws Exception {
        String token = login();

        String createResponse = mockMvc.perform(post("/api/v1/system/dict-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeCode": "SYS_TEST",
                                  "typeName": "测试字典类型",
                                  "description": "字典管理测试",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String dictTypeId = objectMapper.readTree(createResponse).path("data").path("dictTypeId").asText();
        assertThat(dictTypeId).startsWith("dict_type_");

        mockMvc.perform(post("/api/v1/system/dict-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeCode": "SYS_TEST",
                                  "typeName": "重复编码",
                                  "description": "不应创建",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict());

        String detailResponse = mockMvc.perform(get("/api/v1/system/dict-types/" + dictTypeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("typeCode").asText()).isEqualTo("SYS_TEST");
        assertThat(detailData.path("status").asText()).isEqualTo("ENABLED");

        mockMvc.perform(put("/api/v1/system/dict-types/" + dictTypeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeCode": "SYS_TEST",
                                  "typeName": "测试字典类型（更新）",
                                  "description": "更新后的描述",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        String pageResponse = mockMvc.perform(post("/api/v1/system/dict-types/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "测试",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "DISABLED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "status"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("status").asText()).isEqualTo("DISABLED");
        assertThat(pageData.path("groups").isArray()).isTrue();
        assertThat(pageData.path("groups").get(0).path("field").asText()).isEqualTo("status");

        String optionsResponse = mockMvc.perform(get("/api/v1/system/dict-types/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("statusOptions").isArray()).isTrue();
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wangwu",
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
