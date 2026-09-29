package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.FakeMailClient;
import com.codewalnut.ats.client.MailClient.Email;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Candidate chat, team chat and email from the ATS (MSG-09…MSG-18). Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class MessageFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final RequestPostProcessor APPROVER = user("approver@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeMailClient mail;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @AfterEach
    void resetMail() {
        mail.setConnected(true);
        mail.setFailing(false);
    }

    private ResultActions send(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** A new candidate in a new opening; returns the application id. */
    private String application(String email) throws Exception {
        String job = JsonPath.read(send(ADMIN, "/api/v1/jobs", "{\"title\":\"Intern " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String body = "{\"name\":\"Meera Iyer\",\"stage\":\"SCREENING\"" + (email != null ? ",\"email\":\"" + email + "\"" : "") + "}";
        return JsonPath.read(send(ADMIN, "/api/v1/jobs/" + job + "/applications", body)
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private MockHttpSession candidate(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    private static String candidateMessage(String text, boolean email, String subject) {
        return "{\"channel\":\"CANDIDATE\",\"body\":\"" + text + "\",\"sendEmail\":" + email
                + (subject != null ? ",\"subject\":\"" + subject + "\"" : "") + "}";
    }

    @Test
    void staffEmailTheCandidateFromTheirGmailAndItLandsInTheConversation() throws Exception {
        String email = "meera." + tag + "@gmail.com";
        String app = application(email);

        send(ADMIN, "/api/v1/applications/" + app + "/messages",
                candidateMessage("Hi Meera,\\n\\nYou have been shortlisted.", true, "You're shortlisted\\r\\nBcc: evil@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailed").value(true))
                .andExpect(jsonPath("$.subject").value("You're shortlisted\r\nBcc: evil@example.com"));

        Email sent = mail.sent().get(mail.sent().size() - 1);
        assertThat(sent.to()).isEqualTo(email);
        assertThat(sent.body()).startsWith("Hi Meera,\n\nYou have been shortlisted.")
                .contains("sign in at http://localhost/").contains(email);

        mockMvc.perform(get("/api/v1/applications/" + app + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].type").value("EMAIL_SENT"));
        mockMvc.perform(get("/api/v1/applications/" + app + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorType").value("STAFF"));
    }

    @Test
    void candidateSeesTheConversationRepliesAndStaffSeeItAwaitingReply() throws Exception {
        String email = "chat." + tag + "@gmail.com";
        String app = application(email);
        send(ADMIN, "/api/v1/applications/" + app + "/messages", candidateMessage("Can you share your notice period?", false, null))
                .andExpect(jsonPath("$.emailed").value(false));
        send(ADMIN, "/api/v1/applications/" + app + "/messages",
                "{\"channel\":\"TEAM\",\"body\":\"Internal: strong React skills\",\"sendEmail\":true}")
                .andExpect(jsonPath("$.emailed").value(false));
        MockHttpSession session = candidate(email);

        mockMvc.perform(get("/api/v1/candidate/applications").session(session))
                .andExpect(jsonPath("$[0].id").value(app))
                .andExpect(jsonPath("$[0].newMessages").value(1));
        String thread = mockMvc.perform(get("/api/v1/candidate/applications/" + app + "/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromMe").value(false))
                .andExpect(jsonPath("$[0].authorName").value("Dev Admin, CodeWalnut"))
                .andReturn().getResponse().getContentAsString();
        assertThat(thread).doesNotContain("Internal", "admin@codewalnut.test");
        mockMvc.perform(get("/api/v1/candidate/applications").session(session))
                .andExpect(jsonPath("$[0].newMessages").value(0));

        mockMvc.perform(post("/api/v1/candidate/applications/" + app + "/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"30 days, can join 1 Nov\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromMe").value(true));

        String inbox = mockMvc.perform(get("/api/v1/messages/inbox").with(HIRING_MANAGER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> mine = JsonPath.read(inbox, "$[?(@.applicationId == '" + app + "')]");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("awaitingReply", true).containsEntry("preview", "30 days, can join 1 Nov");
        mockMvc.perform(get("/api/v1/applications/" + app + "/messages").param("channel", "TEAM").with(HIRING_MANAGER))
                .andExpect(jsonPath("$[0].body").value("Internal: strong React skills"));
    }

    @Test
    void candidatesCannotReachOtherPeoplesConversations() throws Exception {
        String app = application("owner." + tag + "@gmail.com");
        MockHttpSession stranger = candidate("stranger." + tag + "@gmail.com");

        mockMvc.perform(get("/api/v1/candidate/applications/" + app + "/messages").session(stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/candidate/applications/" + app + "/messages").session(stranger).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/" + app + "/messages").param("channel", "TEAM").session(stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyPeopleWhoWorkWithCandidatesCanMessage() throws Exception {
        String app = application("perm." + tag + "@gmail.com");
        send(HIRING_MANAGER, "/api/v1/applications/" + app + "/messages", candidateMessage("Hello from the HM", false, null))
                .andExpect(status().isCreated());
        send(INTERVIEWER, "/api/v1/applications/" + app + "/messages", candidateMessage("hi", false, null))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/messages/inbox").with(APPROVER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/messages/inbox")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailNeedsAnAddressAConnectionAndAWorkingGmail() throws Exception {
        String noEmail = application(null);
        send(ADMIN, "/api/v1/applications/" + noEmail + "/messages", candidateMessage("Hi", true, null))
                .andExpect(status().isBadRequest());

        String app = application("gmail." + tag + "@gmail.com");
        mail.setConnected(false);
        send(ADMIN, "/api/v1/applications/" + app + "/messages", candidateMessage("Hi", true, null))
                .andExpect(status().isPreconditionRequired());
        mail.setConnected(true);
        mail.setFailing(true);
        send(ADMIN, "/api/v1/applications/" + app + "/messages", candidateMessage("Hi", true, null))
                .andExpect(status().isBadGateway());

        mockMvc.perform(get("/api/v1/applications/" + app + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void candidatesAreRateLimited() throws Exception {
        String email = "flood." + tag + "@gmail.com";
        String app = application(email);
        MockHttpSession session = candidate(email);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/candidate/applications/" + app + "/messages").session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"msg " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/candidate/applications/" + app + "/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"one more\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankOrOversizedMessagesAreRefused() throws Exception {
        String app = application("size." + tag + "@gmail.com");
        send(ADMIN, "/api/v1/applications/" + app + "/messages", "{\"channel\":\"TEAM\",\"body\":\"   \"}")
                .andExpect(status().isBadRequest());
        send(ADMIN, "/api/v1/applications/" + app + "/messages",
                "{\"channel\":\"TEAM\",\"body\":\"" + "x".repeat(10_001) + "\"}")
                .andExpect(status().isBadRequest());
    }
}
