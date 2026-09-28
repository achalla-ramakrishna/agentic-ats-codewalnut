package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Original and CodeWalnut résumés per candidate. Fake files only. */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final byte[] PDF = "%PDF-1.4\nfake resume".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    /** Returns [jobId, candidateId]. */
    private String[] candidate() throws Exception {
        String client = JsonPath.read(mockMvc.perform(post("/api/v1/clients").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docs " + tag + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String job = JsonPath.read(mockMvc.perform(post("/api/v1/jobs").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Docs " + tag + "\",\"clientId\":\"" + client + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String candidate = JsonPath.read(mockMvc.perform(post("/api/v1/jobs/" + job + "/applications").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Doc Person\",\"email\":\"doc." + tag + "@gmail.com\",\"stage\":\"INTERVIEWED\"}"))
                .andReturn().getResponse().getContentAsString(), "$.candidateId");
        return new String[] {job, candidate};
    }

    private org.springframework.test.web.servlet.ResultActions upload(String candidateId, String kind,
            MockMultipartFile file, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(multipart("/api/v1/candidates/" + candidateId + "/documents")
                .file(file).param("kind", kind).with(who).with(csrf()));
    }

    @Test
    void uploadBothResumesListThemAndDownload() throws Exception {
        String[] ids = candidate();
        String documentId = JsonPath.read(upload(ids[1], "ORIGINAL_RESUME",
                        new MockMultipartFile("file", "C:\\Users\\me\\Doc Person CV.pdf", "application/pdf", PDF), ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Doc Person CV.pdf"))
                .andExpect(jsonPath("$.kind").value("ORIGINAL_RESUME"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        upload(ids[1], "CODEWALNUT_RESUME",
                new MockMultipartFile("file", "cw.docx", "application/octet-stream", new byte[] {'P', 'K', 3, 4, 1, 2}), ADMIN)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/candidates/" + ids[1] + "/documents").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].data").doesNotExist());

        mockMvc.perform(get("/api/v1/jobs/" + ids[0] + "/applications").with(ADMIN))
                .andExpect(jsonPath("$[0].documents", Matchers.containsInAnyOrder("ORIGINAL_RESUME", "CODEWALNUT_RESUME")));

        byte[] body = mockMvc.perform(get("/api/v1/documents/" + documentId).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(PDF);
        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.DOCUMENT_DOWNLOADED, "admin@codewalnut.test")).isNotEmpty();
    }

    @Test
    void aRenamedNonPdfIsRefused() throws Exception {
        String[] ids = candidate();
        upload(ids[1], "ORIGINAL_RESUME",
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "MZ this is not a pdf".getBytes()), ADMIN)
                .andExpect(status().isBadRequest());
        upload(ids[1], "ORIGINAL_RESUME",
                new MockMultipartFile("file", "cv.exe", "application/octet-stream", PDF), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyPeopleWhoManageOpeningsCanUploadAndInterviewersCannotDownload() throws Exception {
        String[] ids = candidate();
        upload(ids[1], "ORIGINAL_RESUME", new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF),
                user("hiring.manager@codewalnut.test"))
                .andExpect(status().isForbidden());
        String documentId = JsonPath.read(upload(ids[1], "ORIGINAL_RESUME",
                        new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF), ADMIN)
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/documents/" + documentId).with(user("interviewer@codewalnut.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/" + documentId).with(user("hiring.manager@codewalnut.test")))
                .andExpect(status().isOk());
    }
}
