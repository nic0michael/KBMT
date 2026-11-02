package com.togafmate.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.togafmate.backend.entity.KnowledgeBase;
import com.togafmate.backend.entity.KnowledgeBasePage;
import com.togafmate.backend.repository.KnowledgeBaseRepository;
import com.togafmate.backend.repository.KnowledgeBasePageRepository;
import com.togafmate.backend.repository.KnowledgeBaseFileRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ADMINISTRATOR_USER_ACCESS")
class KnowledgeBaseControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private KnowledgeBasePageRepository pageRepository;
    @Autowired
    private KnowledgeBaseFileRepository fileRepository;

    @Test
    void should_storeMarkdownFile_whenAdministratorUploadsLogicalPath() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("File Guide", "1.0",
                "Reference material", "Free", "file-guide-v1"));
        MockMultipartFile file = new MockMultipartFile("file", "intro.md", "text/markdown",
                "# Introduction".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/{id}/files", knowledgeBase.getId())
                        .file(file).param("path", "chapters/intro.md"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("chapters/intro.md"))
                .andExpect(jsonPath("$.contentType").value("text/markdown"));
        org.assertj.core.api.Assertions.assertThat(fileRepository.findAllByKnowledgeBaseIdOrderByPathAsc(knowledgeBase.getId()))
                .hasSize(1);
    }

    @Test
    void should_importAndExportKnowledgeBaseZip_whenArchiveIsValid() throws Exception {
        byte[] archive = zip(Map.of(
                "README.md", "# Imported guide\n",
                "meta.json", "{\"title\":\"Imported guide\",\"version\":\"1.0\","
                        + "\"folder_name_version\":\"imported-guide-v1\",\"content_type\":\"Free\"}",
                "chapters/start.md", "# Start"));
        MockMultipartFile file = new MockMultipartFile("file", "guide.zip", "application/zip", archive);

        String response = mockMvc.perform(multipart("/api/knowledge-bases/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Imported guide"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        org.springframework.test.web.servlet.MvcResult exportResult = mockMvc
                .perform(get("/api/knowledge-bases/{id}/export", id))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] exported = mockMvc.perform(asyncDispatch(exportResult))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(unzip(exported).get("chapters/start.md"))
                .isEqualTo("# Start");
    }

    @Test
    void should_rejectDuplicateTitle_whenOverwriteIsNotSelected() throws Exception {
        knowledgeBaseRepository.save(new KnowledgeBase("Imported guide", "1.0", "Existing", "Free",
                "imported-guide-v1"));
        byte[] archive = zip(Map.of("README.md", "# Imported guide",
                "meta.json", "{\"title\":\"Imported guide\",\"version\":\"2.0\","
                        + "\"folder_name_version\":\"imported-guide-v2\",\"content_type\":\"Free\"}"));
        MockMultipartFile file = new MockMultipartFile("file", "guide.zip", "application/zip", archive);

        mockMvc.perform(multipart("/api/knowledge-bases/import").file(file))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(knowledgeBaseRepository.findAll()).hasSize(1);
    }

    @Test
    void should_replaceExistingKnowledgeBase_whenOverwriteAndDifferentVersionAreSupplied() throws Exception {
        KnowledgeBase existing = knowledgeBaseRepository.save(new KnowledgeBase("Replace guide", "1.0",
                "Old description", "Free", "replace-guide-v1"));
        pageRepository.save(new KnowledgeBasePage(existing, "old-page", "old content"));
        byte[] archive = zip(Map.of(
                "README.md", "# Replace guide",
                "meta.json", "{\"title\":\"Replace guide\",\"version\":\"2.0\","
                        + "\"folder_name_version\":\"replace-guide-v2\",\"content_type\":\"Free\"}",
                "new-page.md", "new content"));
        MockMultipartFile file = new MockMultipartFile("file", "guide.zip", "application/zip", archive);

        mockMvc.perform(multipart("/api/knowledge-bases/import").file(file).param("overwrite", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.version").value("2.0"));

        org.assertj.core.api.Assertions.assertThat(pageRepository.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(fileRepository.findAllByKnowledgeBaseIdOrderByPathAsc(existing.getId()))
                .extracting(fileEntry -> fileEntry.getPath())
                .containsExactlyInAnyOrder("README.md", "meta.json", "new-page.md");
    }

    @Test
    void should_rejectZipSlipPath_withoutWritingKnowledgeBase_whenImportingArchive() throws Exception {
        byte[] archive = zip(Map.of(
                "README.md", "# Unsafe",
                "meta.json", "{\"title\":\"Unsafe\",\"version\":\"1.0\","
                        + "\"folder_name_version\":\"unsafe-v1\",\"content_type\":\"Free\"}",
                "../escape.md", "unsafe"));
        MockMultipartFile file = new MockMultipartFile("file", "unsafe.zip", "application/zip", archive);

        mockMvc.perform(multipart("/api/knowledge-bases/import").file(file))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(knowledgeBaseRepository.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(fileRepository.findAll()).isEmpty();
    }

    @Test
    void shouldSandboxHtmlResponse_whenReaderLoadsStoredHtml() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("HTML Guide", "1.0",
                "Reference material", "Free", "html-guide-v1"));
        MockMultipartFile file = new MockMultipartFile("file", "chapter.html", "text/html",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/knowledge-bases/{id}/files", knowledgeBase.getId())
                        .file(file).param("path", "chapter.html"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/knowledge-bases/{id}/files/content", knowledgeBase.getId())
                        .param("path", "chapter.html")
                        .with(user("reader").authorities(() -> "USER_ACCESS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "sandbox; default-src 'none'; style-src 'unsafe-inline'"))
                .andExpect(content().string("<script>alert(1)</script>"));
    }

    @Test
    void should_activateInactiveKnowledgeBase_whenAdministratorAddsValidFile() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Inactive Guide",
                "inactive-guide-v1", "1.0", null, null, null, null, "Free", false));
        MockMultipartFile file = new MockMultipartFile("file", "intro.md", "text/markdown",
                "# Introduction".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/knowledge-bases/{id}/files", knowledgeBase.getId())
                        .file(file).param("path", "intro.md"))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(knowledgeBaseRepository.findById(knowledgeBase.getId()).orElseThrow()
                .getIsActive()).isTrue();
    }

    @Test
    void should_uploadAndRetrieveMarkdownPage_whenAdministratorSelectsActiveKnowledgeBase() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0",
                "Reference material", "Free", "architecture-v1"));
        MockMultipartFile file = new MockMultipartFile("file", "overview.md", "text/markdown",
                "# Overview\nArchitecture content".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/{id}/pages", knowledgeBase.getId()).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("overview"));
        mockMvc.perform(get("/api/knowledge-bases/{id}/pages", knowledgeBase.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("overview"));
    }

    @Test
    void should_returnMarkdownPageContent_whenReaderRequestsPage() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0",
                "Reference material", "Free", "architecture-v1"));
        MockMultipartFile file = new MockMultipartFile("file", "overview.md", "text/markdown",
                "# Overview".getBytes());
        String response = mockMvc.perform(multipart("/api/knowledge-bases/{id}/pages", knowledgeBase.getId()).file(file))
                .andReturn().getResponse().getContentAsString();
        String pageId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/knowledge-bases/{id}/pages", knowledgeBase.getId())
                        .with(user("reader").authorities(() -> "USER_ACCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("overview"));
        mockMvc.perform(get("/api/knowledge-bases/{id}/pages/{pageId}", knowledgeBase.getId(), pageId)
                        .with(user("reader").authorities(() -> "USER_ACCESS")))
                .andExpect(status().isOk())
                .andExpect(content().string("# Overview"));
    }

    @Test
    void should_forbidPageUpload_whenRequesterIsReader() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0",
                "Reference material", "Free", "architecture-v1"));
        MockMultipartFile file = new MockMultipartFile("file", "overview.md", "text/markdown",
                "# Overview".getBytes());
        mockMvc.perform(multipart("/api/knowledge-bases/{id}/pages", knowledgeBase.getId()).file(file)
                        .with(user("reader").authorities(() -> "USER_ACCESS")))
                .andExpect(status().isForbidden());
    }

    @BeforeEach
    void clearKnowledgeBases() {
        fileRepository.deleteAll();
        pageRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private Map<String, String> unzip(byte[] archive) throws Exception {
        java.util.Map<String, String> entries = new java.util.HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void should_returnEmptyList_whenNoKnowledgeBasesExist() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void should_returnStoredKnowledgeBase_whenKnowledgeBaseExists() throws Exception {
        knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0", "Reference material",
                "Free", "architecture-v1"));

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Architecture"))
                .andExpect(jsonPath("$[0].version").value("1.0"))
                .andExpect(jsonPath("$[0].contentType").value("Free"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void should_notListInactiveKnowledgeBases_whenListingKnowledgeBases() throws Exception {
        knowledgeBaseRepository.save(new KnowledgeBase("Active Guide", "Active_Guide_v1", "1.0", null, null,
                null, null, "Free", true));
        knowledgeBaseRepository.save(new KnowledgeBase("Inactive Guide", "Inactive_Guide_v1", "1.0", null,
                null, null, null, "Free", false));

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("Active Guide")))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void should_listActiveAndInactiveKnowledgeBases_whenAdministratorRequestsManagementList() throws Exception {
        knowledgeBaseRepository.save(new KnowledgeBase("Active Guide", "Active_Guide_v1", "1.0", null, null,
                null, null, "Free", true));
        knowledgeBaseRepository.save(new KnowledgeBase("Inactive Guide", "Inactive_Guide_v1", "1.0", null,
                null, null, null, "Free", false));

        mockMvc.perform(get("/api/knowledge-bases/management"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Active Guide", "Inactive Guide")))
                .andExpect(jsonPath("$[?(@.title == 'Inactive Guide')].isActive").value(false));
    }

    @Test
    void should_rejectInvalidKnowledgeBase_beforeCallingService_whenMetadataIsInvalid() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType("application/json")
                        .content("""
                                {"title":" ","folderNameVersion":"../outside","version":"1.0",
                                 "description":null,"contentType":"Free"}
                                """))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(knowledgeBaseRepository.findAll()).isEmpty();
    }

    @Test
    void should_validateThenCreateKnowledgeBase_whenMetadataIsValid() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType("application/json")
                        .content("""
                                {"title":"Architecture","folderNameVersion":"Architecture_Guide_v1.0",
                                 "version":"1.0","contentType":"Free","isActive":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Architecture"))
                .andExpect(jsonPath("$.folderNameVersion").value("Architecture_Guide_v1.0"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_rejectKnowledgeBase_whenIsActiveIsMissing() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType("application/json")
                        .content("""
                                {"title":"Architecture","folderNameVersion":"Architecture_Guide_v1.0",
                                 "version":"1.0","contentType":"Free"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_updateKnowledgeBaseMetadata_whenAdministratorRequestsUpdate() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0",
                "Old description", "Free", "architecture-v1"));

        mockMvc.perform(put("/api/knowledge-bases/" + knowledgeBase.getId())
                        .contentType("application/json")
                        .content("""
                                {"title":"Updated Architecture","folderNameVersion":"architecture-v2",
                                 "version":"2.0","description":"Updated description","contentType":"Open-source",
                                 "isActive":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knowledgeBase.getId().toString()))
                .andExpect(jsonPath("$.title").value("Updated Architecture"))
                .andExpect(jsonPath("$.folderNameVersion").value("architecture-v2"))
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void should_forbidKnowledgeBaseUpdate_whenRequesterIsNotAdministrator() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0",
                "Old description", "Free", "architecture-v1"));

        mockMvc.perform(put("/api/knowledge-bases/" + knowledgeBase.getId())
                        .with(user("reader").authorities(() -> "USER_ACCESS"))
                        .contentType("application/json")
                        .content("""
                                {"title":"Updated Architecture","folderNameVersion":"architecture-v2",
                                 "version":"2.0","contentType":"Open-source","isActive":true}
                                """))
                .andExpect(status().isForbidden());
    }
}
