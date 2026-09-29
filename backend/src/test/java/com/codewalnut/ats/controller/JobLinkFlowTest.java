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

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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

/** Shareable job links: public job page and candidates applying through it. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class JobLinkFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final byte[] PDF = "%PDF-1.4\nfake".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private String json(String method, String url, String body) throws Exception {
        var request = "PATCH".equals(method) ? patch(url) : post(url);
        return mockMvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    /** Returns [jobId, slug] of a published client opening with details filled in. */
    private String[] publishedJob() throws Exception {
        String client = JsonPath.read(json("POST", "/api/v1/clients", "{\"name\":\"SecretClient " + tag + "\"}"), "$.id");
        String created = json("POST", "/api/v1/jobs", "{\"title\":\"React Intern " + tag
                + "\",\"clientId\":\"" + client + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}");
        String jobId = JsonPath.read(created, "$.id");
        String slug = JsonPath.read(created, "$.publicSlug");
        assertThat(slug).hasSize(12);
        json("PATCH", "/api/v1/jobs/" + jobId, "{\"description\":\"Build UIs with React.\\n- 6 months\",\"location\":\"Bengaluru\","
                + "\"workMode\":\"ONSITE\",\"employmentType\":\"Internship · 6 months\",\"published\":true}");
        return new String[] {jobId, slug};
    }

    private MockHttpSession candidateSession(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions apply(MockHttpSession session, String slug, boolean consent, byte[] resume) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/candidate/applications");
        request.param("slug", slug).param("name", "Asha  Rao").param("phone", "+91 90000 00001")
                .param("note", "Available from November").param("consent", String.valueOf(consent));
        if (resume != null) {
            request.file(new MockMultipartFile("resume", "Asha CV.pdf", "application/pdf", resume));
        }
        return mockMvc.perform(request.session(session).with(csrf()));
    }

    @Test
    void anyoneCanSeeAPublishedJobButNotTheClientName() throws Exception {
        String[] job = publishedJob();

        String body = mockMvc.perform(get("/api/v1/public/jobs/" + job[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("React Intern " + tag))
                .andExpect(jsonPath("$.company").value("CodeWalnut"))
                .andExpect(jsonPath("$.location").value("Bengaluru"))
                .andExpect(jsonPath("$.workMode").value("Office"))
                .andExpect(jsonPath("$.acceptingApplications").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SecretClient");
    }

    @Test
    void unpublishedOrUnknownLinksAreNotFound() throws Exception {
        String[] job = publishedJob();
        json("PATCH", "/api/v1/jobs/" + job[0], "{\"published\":false}");

        mockMvc.perform(get("/api/v1/public/jobs/" + job[1])).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/jobs/doesnotexist")).andExpect(status().isNotFound());
    }

    @Test
    void closedRolesShowButDoNotAcceptApplications() throws Exception {
        String[] job = publishedJob();
        json("PATCH", "/api/v1/jobs/" + job[0], "{\"status\":\"CLOSED\"}");

        mockMvc.perform(get("/api/v1/public/jobs/" + job[1])).andExpect(jsonPath("$.acceptingApplications").value(false));
        apply(candidateSession("closed." + tag + "@gmail.com"), job[1], true, PDF).andExpect(status().isBadRequest());
    }

    @Test
    void candidateAppliesWithResumeAndLandsInThePipeline() throws Exception {
        String[] job = publishedJob();
        String email = "asha." + tag + "@gmail.com";
        MockHttpSession session = candidateSession(email);

        apply(session, job[1], true, PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Applied"));

        String rows = mockMvc.perform(get("/api/v1/jobs/" + job[0] + "/applications").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Asha Rao"))
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].phone").value("+919000000001"))
                .andExpect(jsonPath("$[0].stage").value("SOURCED"))
                .andExpect(jsonPath("$[0].documents[0]").value("ORIGINAL_RESUME"))
                .andReturn().getResponse().getContentAsString();
        String appId = JsonPath.read(rows, "$[0].id");
        mockMvc.perform(get("/api/v1/applications/" + appId + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].note").value("Applied via job link: Available from November"))
                .andExpect(jsonPath("$[0].actorEmail").value(email));

        // Internal progress is shown to the candidate only as a coarse status.
        json("PATCH", "/api/v1/applications/" + appId + "/stage", "{\"stage\":\"SUBMITTED_TO_CLIENT\",\"note\":\"internal only\"}");
        String mine = mockMvc.perform(get("/api/v1/candidate/applications").session(session))
                .andExpect(jsonPath("$[0].status").value("Under review"))
                .andExpect(jsonPath("$[0].jobTitle").value("React Intern " + tag))
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).doesNotContain("internal only").doesNotContain("SUBMITTED").doesNotContain("SecretClient");

        // Applying again is refused.
        apply(session, job[1], true, PDF).andExpect(status().isConflict());
    }

    @Test
    void consentAndResumeAreRequired() throws Exception {
        String[] job = publishedJob();
        MockHttpSession session = candidateSession("nc." + tag + "@gmail.com");

        apply(session, job[1], false, PDF).andExpect(status().isBadRequest());
        apply(session, job[1], true, "not a pdf".getBytes()).andExpect(status().isBadRequest());
    }

    @Test
    void staffCannotUseTheCandidateApply() throws Exception {
        String[] job = publishedJob();
        mockMvc.perform(multipart("/api/v1/candidate/applications")
                        .file(new MockMultipartFile("resume", "cv.pdf", "application/pdf", PDF))
                        .param("slug", job[1]).param("name", "X").param("phone", "9000000000").param("consent", "true")
                        .with(ADMIN).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutVisitorsMustSignInToApply() throws Exception {
        String[] job = publishedJob();
        mockMvc.perform(multipart("/api/v1/candidate/applications")
                        .file(new MockMultipartFile("resume", "cv.pdf", "application/pdf", PDF))
                        .param("slug", job[1]).param("name", "X").param("phone", "9000000000").param("consent", "true")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
