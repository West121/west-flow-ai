package com.westflow.system.dict.item;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.system.dict.item.mapper.SystemDictItemMapper;
import com.westflow.system.dict.type.mapper.SystemDictTypeMapper;
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
class SystemDictItemControllerTest {

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
    void shouldManageDictItemsWithTypeReferenceAndFilters() throws Exception {
        String token = login();

        String typeResponse = mockMvc.perform(post("/api/v1/system/dict-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeCode": "DEPT_LEVEL",
                                  "typeName": "部门级别",
                                  "description": "测试用",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String dictTypeId = objectMapper.readTree(typeResponse).path("data").path("dictTypeId").asText();

        String createResponse = mockMvc.perform(post("/api/v1/system/dict-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictTypeId": "%s",
                                  "itemCode": "L1",
                                  "itemLabel": "一级",
                                  "itemValue": "LEVEL_1",
                                  "sortOrder": 1,
                                  "remark": "测试项",
                                  "enabled": true
                                }
                                """.formatted(dictTypeId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String dictItemId = objectMapper.readTree(createResponse).path("data").path("dictItemId").asText();
        assertThat(dictItemId).startsWith("dict_item_");

        mockMvc.perform(post("/api/v1/system/dict-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictTypeId": "%s",
                                  "itemCode": "L1",
                                  "itemLabel": "重复编码",
                                  "itemValue": "LEVEL_1_DUP",
                                  "sortOrder": 2,
                                  "remark": "重复项",
                                  "enabled": true
                                }
                                """.formatted(dictTypeId)))
                .andExpect(status().isConflict());

        String detailResponse = mockMvc.perform(get("/api/v1/system/dict-items/" + dictItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("itemCode").asText()).isEqualTo("L1");
        assertThat(detailData.path("dictTypeId").asText()).isEqualTo(dictTypeId);

        mockMvc.perform(put("/api/v1/system/dict-items/" + dictItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictTypeId": "%s",
                                  "itemCode": "L1",
                                  "itemLabel": "一级（更新）",
                                  "itemValue": "LEVEL_1",
                                  "sortOrder": 5,
                                  "remark": "更新备注",
                                  "enabled": false
                                }
                                """.formatted(dictTypeId)))
                .andExpect(status().isOk());

        String pageResponse = mockMvc.perform(post("/api/v1/system/dict-items/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "一级",
                                  "filters": [
                                    {
                                      "field": "dictTypeId",
                                      "operator": "eq",
                                      "value": "%s"
                                    },
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "DISABLED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "sortOrder",
                                      "direction": "asc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "dictTypeCode"
                                    }
                                  ]
                                }
                                """.formatted(dictTypeId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("status").asText()).isEqualTo("DISABLED");
        assertThat(pageData.path("groups").isArray()).isTrue();

        String optionsResponse = mockMvc.perform(get("/api/v1/system/dict-items/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("dictTypes").isArray()).isTrue();
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
