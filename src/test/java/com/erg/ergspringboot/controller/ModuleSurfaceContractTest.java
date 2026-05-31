package com.erg.ergspringboot.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class ModuleSurfaceContractTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void hocLieuResourceAndAssetRoutesUseVersionedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/hoclieu/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hero.title").value("HocLieu"));

        MvcResult resourceResult = mockMvc.perform(post("/api/v1/admin/hoclieu/resources")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "Lesson resource",
                                  "subjectId": "subject-demo",
                                  "type": "pdf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileType").value("pdf"))
                .andReturn();
        String resourceId = dataId(resourceResult);

        MvcResult assetResult = mockMvc.perform(post("/api/v1/admin/hoclieu/resources/" + resourceId + "/assets")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "Lesson asset",
                                  "selectedFileType": "PDF",
                                  "launchMode": "pdf_reader",
                                  "storageUrl": "https://example.com/lesson.pdf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.launchMode").value("pdf_reader"))
                .andReturn();
        String assetId = dataId(assetResult);

        mockMvc.perform(get("/api/v1/hoclieu/assets/" + assetId + "/launch")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("launch"));
    }

    @Test
    void hocLieuReadsOnlyReturnManagedEducationUnitData() throws Exception {
        mockMvc.perform(get("/api/v1/hoclieu/resources")
                        .header("X-User-ID", "teacher")
                        .header("X-Permissions", "hoclieu.resource.read")
                        .header("X-Managed-EducationUnit-IDs", "sch_binh_an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.scope.managedEducationUnitIds", hasSize(1)))
                .andExpect(jsonPath("$.data.scope.managedEducationUnitIds[0]").value("sch_binh_an"));
    }

    @Test
    void courseAndElearningRoutesUseCanonicalApiVersion() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Courses loaded"))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(patch("/api/v1/courses/course-demo/theme")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "theme": "blue"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("course-demo"));

        mockMvc.perform(get("/api/v1/elearning/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Categories loaded"))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/v1/admin/elearning/units")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "Unit 1"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Elearning item created"));
    }

    @Test
    void cmsPostPageAndMenuRoutesAreMounted() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Posts loaded"))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/v1/posts")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content("""
                                {
                                  "title": "New post",
                                  "status": "draft"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"));

        mockMvc.perform(get("/api/v1/pages/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("home"));

        mockMvc.perform(get("/api/v1/menus/structure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void documentStorageWorkflowValidatesUploadPolicyAndSupportsLifecycle() throws Exception {
        String documentBody = """
                {
                  "filename": "policy.pdf",
                  "contentType": "application/pdf",
                  "size": 1024,
                  "scope": "internal"
                }
                """;

        mockMvc.perform(post("/api/v1/documents")
                        .contentType("application/json")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*")
                        .content(documentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("policy.pdf"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.storageKey").exists());

        mockMvc.perform(get("/api/v1/documents/document-demo/file")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").exists());

        mockMvc.perform(delete("/api/v1/documents/document-demo")
                        .header("X-User-ID", "admin")
                        .header("X-Permissions", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    private String dataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }

}
