package com.togafmate.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_returnUpStatus_when_healthEndpointIsRequested() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void should_serveOpenApiDocument_whenApiDocsAreRequested() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TOGAFmate Backend API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/users/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/users/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/knowledge-bases'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/knowledge-bases/{id}/pages'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/knowledge-bases'].post.summary").value("Create Knowledge Base"))
                .andExpect(jsonPath("$.paths['/api/knowledge-bases'].post.description")
                        .value(org.hamcrest.Matchers.containsString("generates the UUID")))
                .andExpect(jsonPath("$.paths['/api/knowledge-bases'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/knowledge-bases/{id}'].put.summary")
                        .value("Update Knowledge Base"))
                .andExpect(jsonPath("$.components.schemas.KnowledgeBaseRequest.required",
                        org.hamcrest.Matchers.hasItem("isActive")));
    }

    @Test
    void should_allowAngularOrigin_whenBrowserSendsPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsStringIgnoringCase("authorization")));
    }
}
