package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Client contacts sign in and see only what was shared with their company (CLA-01…CLA-12),
 * including the cross-client deny tests AGENTS.md requires. Fake companies and people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientAccessFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions json(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var request = switch (method) {
            case "PUT" -> put(url);
            default -> post(url);
        };
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    /** A client with one contact, one hired candidate with documents. */
    private record Setup(String clientId, String contactEmail, String applicationId, String candidateId,
            String candidateEmail, String phone, String aadhaarId, String cwResumeId) {}

    private Setup setup(String name) throws Exception {
        String clientId = JsonPath.read(body(json(ADMIN, "POST", "/api/v1/clients", "{\"name\":\"" + name + " " + tag + "\"}")), "$.id");
        String contactEmail = "hm." + name.toLowerCase() + "." + tag + "@" + name.toLowerCase() + ".example";
        json(ADMIN, "POST", "/api/v1/clients/" + clientId + "/contacts", "{\"email\":\"" + contactEmail + "\",\"name\":\"Hiring Manager\"}")
                .andExpect(status().isCreated());
        String job = JsonPath.read(body(json(ADMIN, "POST", "/api/v1/jobs", "{\"title\":\"" + name + " Interns " + tag
                + "\",\"clientId\":\"" + clientId + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}")), "$.id");
        String candidateEmail = "hired." + name.toLowerCase() + "." + tag + "@gmail.com";
        String phone = "9" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L));
        String created = body(json(ADMIN, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Asha " + name + "\",\"email\":\"" + candidateEmail + "\",\"phone\":\"" + phone + "\",\"stage\":\"SELECTED\"}"));
        String applicationId = JsonPath.read(created, "$.id");
        String candidateId = JsonPath.read(created, "$.candidateId");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/candidates/" + candidateId)
                .with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"dateOfBirth\":\"2003-01-02\"}"));
        String aadhaar = upload(candidateId, "AADHAAR", "aadhaar.png", ProfileAndDocumentsFlowTest.PNG);
        String cw = upload(candidateId, "CODEWALNUT_RESUME", "cw.pdf", ProfileAndDocumentsFlowTest.PDF);
        return new Setup(clientId, contactEmail, applicationId, candidateId, candidateEmail, phone, aadhaar, cw);
    }

    private String upload(String candidateId, String kind, String name, byte[] bytes) throws Exception {
        return JsonPath.read(mockMvc.perform(multipart("/api/v1/candidates/" + candidateId + "/documents")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                        .param("kind", kind).with(ADMIN).with(csrf()))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions share(Setup s, boolean contact, boolean profile, String... documentIds) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(documentIds).map(id -> "\"" + id + "\"").toList());
        return json(ADMIN, "PUT", "/api/v1/applications/" + s.applicationId() + "/client-share",
                "{\"includeContact\":" + contact + ",\"includeProfile\":" + profile + ",\"documentIds\":[" + ids
                        + "],\"note\":\"For background verification\"}");
    }

    @Test
    void contactsAreManagedByAdminsAndAccountManagersOnly() throws Exception {
        Setup s = setup("Acme");
        mockMvc.perform(get("/api/v1/clients/" + s.clientId() + "/contacts").with(RECRUITER))
                .andExpect(jsonPath("$[0].email").value(s.contactEmail()));
        json(RECRUITER, "POST", "/api/v1/clients/" + s.clientId() + "/contacts", "{\"email\":\"x." + tag + "@acme.example\"}")
                .andExpect(status().isForbidden());
        json(ADMIN, "POST", "/api/v1/clients/" + s.clientId() + "/contacts", "{\"email\":\"someone@codewalnut.test\"}")
                .andExpect(status().isBadRequest());
        json(ADMIN, "POST", "/api/v1/clients/" + s.clientId() + "/contacts", "{\"email\":\"" + s.candidateEmail() + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void clientContactsSeeOnlyTheTickedParts() throws Exception {
        Setup s = setup("Blendo");
        share(s, false, true, s.aadhaarId()).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        MockHttpSession client = signIn(s.contactEmail());

        mockMvc.perform(get("/api/v1/auth/session").session(client)).andExpect(jsonPath("$.type").value("CLIENT"));
        mockMvc.perform(get("/api/v1/client/me").session(client)).andExpect(jsonPath("$.clientName").value("Blendo " + tag));
        String list = body(mockMvc.perform(get("/api/v1/client/candidates").session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Asha Blendo"))
                .andExpect(jsonPath("$[0].stageLabel").value("Offer sent"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].profile.dateOfBirth").value("2003-01-02"))
                .andExpect(jsonPath("$[0].documents.length()").value(1))
                .andExpect(jsonPath("$[0].documents[0].kind").value("AADHAAR")));
        assertThat(list).doesNotContain(s.candidateEmail(), s.phone(), s.cwResumeId());

        mockMvc.perform(get("/api/v1/client/documents/" + s.aadhaarId()).session(client))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ProfileAndDocumentsFlowTest.PNG));
        mockMvc.perform(get("/api/v1/client/documents/" + s.cwResumeId()).session(client)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/" + s.applicationId() + "/client-share").with(ADMIN))
                .andExpect(jsonPath("$.lastViewedAt").isNotEmpty());

        share(s, true, false, s.aadhaarId(), s.cwResumeId());
        mockMvc.perform(get("/api/v1/client/candidates").session(client))
                .andExpect(jsonPath("$[0].email").value(s.candidateEmail()))
                .andExpect(jsonPath("$[0].phone").value(s.phone()))
                .andExpect(jsonPath("$[0].profile").doesNotExist())
                .andExpect(jsonPath("$[0].documents.length()").value(2));
    }

    @Test
    void oneClientNeverSeesAnotherClientsCandidates() throws Exception {
        Setup a = setup("Alpha");
        Setup b = setup("Beta");
        share(a, true, true, a.aadhaarId());
        MockHttpSession betaContact = signIn(b.contactEmail());

        String list = body(mockMvc.perform(get("/api/v1/client/candidates").session(betaContact)));
        assertThat(list).doesNotContain("Asha Alpha", a.candidateEmail());
        mockMvc.perform(get("/api/v1/client/documents/" + a.aadhaarId()).session(betaContact)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/client/applications/" + a.applicationId() + "/messages").session(betaContact))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/client/applications/" + a.applicationId() + "/messages").session(betaContact).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokingSharingOrRemovingTheContactCutsAccessAtOnce() throws Exception {
        Setup s = setup("Gamma");
        share(s, true, true, s.aadhaarId());
        MockHttpSession client = signIn(s.contactEmail());

        mockMvc.perform(delete("/api/v1/applications/" + s.applicationId() + "/client-share").with(ADMIN).with(csrf()))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/client/candidates").session(client)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/client/documents/" + s.aadhaarId()).session(client)).andExpect(status().isNotFound());

        String contactId = JsonPath.read(body(mockMvc.perform(get("/api/v1/clients/" + s.clientId() + "/contacts").with(ADMIN))), "$[0].id");
        mockMvc.perform(delete("/api/v1/client-contacts/" + contactId).with(ADMIN).with(csrf())).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/client/me").session(client)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/session").session(signIn(s.contactEmail())))
                .andExpect(jsonPath("$.type").value("CANDIDATE"));
    }

    @Test
    void clientChatReachesStaffButNeverTheCandidate() throws Exception {
        Setup s = setup("Delta");
        share(s, false, false);
        MockHttpSession client = signIn(s.contactEmail());

        mockMvc.perform(post("/api/v1/client/applications/" + s.applicationId() + "/messages").session(client).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Please send the PAN card too\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromMe").value(true));
        json(ADMIN, "POST", "/api/v1/applications/" + s.applicationId() + "/messages",
                "{\"channel\":\"CLIENT\",\"body\":\"Will share by Friday\",\"sendEmail\":true}")
                .andExpect(jsonPath("$.emailed").value(false));

        mockMvc.perform(get("/api/v1/client/applications/" + s.applicationId() + "/messages").session(client))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].authorName").value("Dev Admin, CodeWalnut"));
        mockMvc.perform(get("/api/v1/applications/" + s.applicationId() + "/messages").param("channel", "CLIENT").with(HIRING_MANAGER))
                .andExpect(jsonPath("$[0].authorType").value("CLIENT"));
        String inbox = body(mockMvc.perform(get("/api/v1/messages/inbox").with(ADMIN)));
        List<Object> items = JsonPath.read(inbox, "$[?(@.applicationId == '" + s.applicationId() + "' && @.channel == 'CLIENT')]");
        assertThat(items).hasSize(1);

        MockHttpSession candidate = signIn(s.candidateEmail());
        mockMvc.perform(get("/api/v1/candidate/applications/" + s.applicationId() + "/messages").session(candidate))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/client/candidates").session(candidate)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs").session(client)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/candidate/applications").session(client)).andExpect(status().isForbidden());
    }

    @Test
    void onlyClientOpeningsCanBeSharedAndOnlyByTheRightRoles() throws Exception {
        Setup s = setup("Epsilon");
        json(HIRING_MANAGER, "PUT", "/api/v1/applications/" + s.applicationId() + "/client-share",
                "{\"includeContact\":true,\"includeProfile\":false,\"documentIds\":[]}").andExpect(status().isForbidden());

        String job = JsonPath.read(body(json(ADMIN, "POST", "/api/v1/jobs", "{\"title\":\"Internal " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(json(ADMIN, "POST", "/api/v1/jobs/" + job + "/applications", "{\"name\":\"In House\",\"stage\":\"SELECTED\"}")), "$.id");
        json(ADMIN, "PUT", "/api/v1/applications/" + app + "/client-share",
                "{\"includeContact\":true,\"includeProfile\":false,\"documentIds\":[]}").andExpect(status().isBadRequest());

        Setup other = setup("Zeta");
        share(s, false, false, other.aadhaarId()).andExpect(status().isBadRequest());
    }
}
