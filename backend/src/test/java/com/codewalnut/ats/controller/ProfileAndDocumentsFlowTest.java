package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Candidate profile and background-verification documents (BGV-01…BGV-09). Fake people and files only. */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileAndDocumentsFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] PDF = "%PDF-1.4\nfake".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions json(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var request = "PATCH".equals(method) ? patch(url) : post(url);
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Returns [applicationId, candidateId, email]. */
    private String[] candidate() throws Exception {
        String email = "bgv." + tag + "@gmail.com";
        String job = JsonPath.read(json(ADMIN, "POST", "/api/v1/jobs", "{\"title\":\"BGV " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String created = json(ADMIN, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Kiran Rao\",\"email\":\"" + email + "\",\"stage\":\"SELECTED\"}").andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(created, "$.id"), JsonPath.read(created, "$.candidateId"), email};
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions candidateUpload(MockHttpSession session, String kind, String fileName, byte[] bytes) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/candidate/documents");
        request.file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes)).param("kind", kind);
        return mockMvc.perform(request.session(session).with(csrf()));
    }

    @Test
    void staffUpdateTheProfileAndTheAuditLogNamesFieldsNotValues() throws Exception {
        String[] c = candidate();
        json(ADMIN, "PATCH", "/api/v1/candidates/" + c[1], "{\"dateOfBirth\":\"2003-04-05\",\"currentAddress\":\"12 Fake Street, Bengaluru\","
                + "\"college\":\"Fake Institute\",\"graduationYear\":2025,\"linkedinUrl\":\"https://linkedin.com/in/fake\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("2003-04-05"))
                .andExpect(jsonPath("$.graduationYear").value(2025));

        mockMvc.perform(get("/api/v1/candidates/" + c[1] + "/profile").with(HIRING_MANAGER))
                .andExpect(jsonPath("$.currentAddress").value("12 Fake Street, Bengaluru"));
        String details = auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.CANDIDATE_UPDATED, "admin@codewalnut.test").get(0).getDetails();
        assertThat(details).contains("date of birth", "current address").doesNotContain("Fake Street", "2003");

        json(HIRING_MANAGER, "PATCH", "/api/v1/candidates/" + c[1], "{\"college\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void badProfileValuesAreRefused() throws Exception {
        String[] c = candidate();
        json(ADMIN, "PATCH", "/api/v1/candidates/" + c[1], "{\"dateOfBirth\":\"05/04/2003\"}").andExpect(status().isBadRequest());
        json(ADMIN, "PATCH", "/api/v1/candidates/" + c[1], "{\"dateOfBirth\":\"2024-01-01\"}").andExpect(status().isBadRequest());
        json(ADMIN, "PATCH", "/api/v1/candidates/" + c[1], "{\"linkedinUrl\":\"javascript:alert(1)\"}").andExpect(status().isBadRequest());
        json(ADMIN, "PATCH", "/api/v1/candidates/" + c[1], "{\"phone\":\"123\"}").andExpect(status().isBadRequest());
    }

    @Test
    void candidatesUpdateTheirOwnProfileButNotTheirEmail() throws Exception {
        String[] c = candidate();
        MockHttpSession session = signIn(c[2]);

        mockMvc.perform(patch("/api/v1/candidate/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other." + tag + "@gmail.com\",\"phone\":\"+91 90000 22222\",\"permanentAddress\":\"Fake Village\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(c[2]))
                .andExpect(jsonPath("$.phone").value("+919000022222"));
        mockMvc.perform(get("/api/v1/applications/" + c[0] + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].note").value("Candidate updated their profile: phone, permanent address"));

        mockMvc.perform(get("/api/v1/candidate/profile").session(signIn("never.applied." + tag + "@gmail.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffRequestDocumentsAndTheCandidateUploadsThem() throws Exception {
        String[] c = candidate();
        json(ADMIN, "POST", "/api/v1/candidates/" + c[1] + "/document-requests", "{\"kinds\":[\"AADHAAR\",\"PAN\",\"AADHAAR\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
        MockHttpSession session = signIn(c[2]);
        mockMvc.perform(get("/api/v1/candidate/documents").session(session))
                .andExpect(jsonPath("$.requested.length()").value(2));

        candidateUpload(session, "AADHAAR", "aadhaar-masked.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Aadhaar card (masked)"));

        mockMvc.perform(get("/api/v1/candidate/documents").session(session))
                .andExpect(jsonPath("$.requested.length()").value(1))
                .andExpect(jsonPath("$.requested[0].kind").value("PAN"))
                .andExpect(jsonPath("$.documents[0].kind").value("AADHAAR"));
        mockMvc.perform(get("/api/v1/candidates/" + c[1] + "/document-requests").with(ADMIN))
                .andExpect(jsonPath("$[?(@.kind == 'AADHAAR')].fulfilledAt").isNotEmpty());
        String history = mockMvc.perform(get("/api/v1/applications/" + c[0] + "/history").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        assertThat(history).contains("DOCS_REQUESTED", "Candidate uploaded: Aadhaar card (masked)");
    }

    @Test
    void governmentIdsAreHiddenFromPeopleWithoutIdAccess() throws Exception {
        String[] c = candidate();
        candidateUpload(signIn(c[2]), "AADHAAR", "aadhaar.png", PNG).andExpect(status().isCreated());
        String docs = mockMvc.perform(get("/api/v1/candidates/" + c[1] + "/documents").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        String aadhaar = ((List<String>) JsonPath.read(docs, "$[?(@.kind == 'AADHAAR')].id")).get(0);

        mockMvc.perform(get("/api/v1/candidates/" + c[1] + "/documents").with(HIRING_MANAGER))
                .andExpect(jsonPath("$[?(@.kind == 'AADHAAR')]").isEmpty());
        mockMvc.perform(get("/api/v1/documents/" + aadhaar).with(HIRING_MANAGER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/" + aadhaar).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));
    }

    @Test
    void candidatesUploadOnlyAllowedKindsAndRealFiles() throws Exception {
        String[] c = candidate();
        MockHttpSession session = signIn(c[2]);
        candidateUpload(session, "CODEWALNUT_RESUME", "cw.pdf", PDF).andExpect(status().isBadRequest());
        candidateUpload(session, "PAN", "pan.png", "MZ fake exe".getBytes(StandardCharsets.US_ASCII)).andExpect(status().isBadRequest());
        candidateUpload(session, "ORIGINAL_RESUME", "resume.png", PNG).andExpect(status().isBadRequest());
        candidateUpload(session, "DEGREE_CERTIFICATE", "degree.pdf", PDF).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/candidate/documents").with(ADMIN)).andExpect(status().isForbidden());
    }
}
