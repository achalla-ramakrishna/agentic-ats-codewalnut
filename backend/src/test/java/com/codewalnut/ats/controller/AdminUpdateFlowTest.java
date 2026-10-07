package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.FakeMailClient;
import com.codewalnut.ats.client.MailClient.Email;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.repository.InterviewRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

/** ADM-14…ADM-17: admins get a candidate summary when feedback comes in or a key stage is reached. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUpdateFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private FakeMailClient mail;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);
    private final String phone = "+91 9" + (100000000L + (long) (Math.random() * 800000000L));

    @AfterEach
    void reconnect() {
        mail.setConnected(true);
        mail.setFailing(false);
    }

    private ResultActions perform(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var request = switch (method) {
            case "PATCH" -> patch(url);
            case "PUT" -> put(url);
            default -> post(url);
        };
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String send(RequestPostProcessor who, String url, String body) throws Exception {
        return perform(who, "POST", url, body).andReturn().getResponse().getContentAsString();
    }

    /** A new candidate in a new opening; returns the application id. */
    private String candidate(String name) throws Exception {
        String job = JsonPath.read(send(ADMIN, "/api/v1/jobs", "{\"title\":\"Java Intern " + tag + "\",\"hiringType\":\"INTERNAL\"}"), "$.id");
        return JsonPath.read(send(ADMIN, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"" + name + "\",\"stage\":\"SCREENING\",\"email\":\"" + tag + "@example.com\",\"phone\":\"" + phone + "\"}"), "$.id");
    }

    private List<Map<String, Object>> updates() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/v1/admin-updates").with(ADMIN)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.updates");
    }

    private List<Map<String, Object>> updatesFor(String applicationId) throws Exception {
        return updates().stream().filter(u -> applicationId.equals(u.get("applicationId"))).toList();
    }

    private List<Email> mailTo(String address, String containing) {
        return mail.sent().stream().filter(e -> e.to().equals(address) && e.subject().contains(containing)).toList();
    }

    @Test
    void feedbackSendsAdminsACandidateSummaryOnce() throws Exception {
        String name = "Asha Rao " + tag;
        String app = candidate(name);
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String id = JsonPath.read(send(ADMIN, "/api/v1/applications/" + app + "/interviews",
                "{\"title\":\"CodeWalnut interview\",\"startAt\":\"" + start + "\",\"durationMinutes\":45,"
                        + "\"timeZone\":\"Asia/Kolkata\",\"interviewerEmails\":[\"interviewer@codewalnut.test\"]}"), "$.id");
        Interview i = interviewRepository.findById(UUID.fromString(id)).orElseThrow();
        i.setStartAt(Instant.now().minus(Duration.ofHours(1)));
        i.setEndAt(Instant.now().minus(Duration.ofMinutes(15)));
        interviewRepository.save(i);

        String feedback = "{\"attendance\":\"HELD\",\"recommendation\":\"YES\",\"strengths\":\"Clear about HashMap internals\","
                + "\"ratings\":[{\"competency\":\"Problem solving\",\"rating\":3},{\"competency\":\"Communication\",\"rating\":4}]}";
        perform(INTERVIEWER, "PUT", "/api/v1/interviews/" + id + "/feedback", feedback).andExpect(status().isOk());

        List<Map<String, Object>> mine = updatesFor(app);
        assertThat(mine).hasSize(1);
        Map<String, Object> u = mine.get(0);
        assertThat(u.get("kind")).isEqualTo("FEEDBACK_SUBMITTED");
        assertThat((String) u.get("title")).startsWith(name + ": Hire from ").contains("Java Intern");
        assertThat((String) u.get("body")).contains("Stage: Applied / Sourced", tag + "@example.com",
                "Recommendation: Hire", "Average 3.5 / 4", "Problem solving 3, Communication 4",
                "Strengths: Clear about HashMap internals", "Panel so far: 1 of 2 have given feedback (Hire)");
        assertThat(u.get("emailStatus")).isEqualTo("SENT");
        assertThat((String) u.get("emailedTo")).contains("admin@codewalnut.test").doesNotContain("interviewer@");
        List<Email> emails = mailTo("admin@codewalnut.test", name);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).subject()).startsWith("[CodeWalnut ATS] " + name);
        assertThat(emails.get(0).body()).contains("Recommendation: Hire", "you're an admin");

        // Editing the feedback doesn't send another update.
        perform(INTERVIEWER, "PUT", "/api/v1/interviews/" + id + "/feedback", feedback.replace("\"YES\"", "\"STRONG_YES\""))
                .andExpect(status().isOk());
        assertThat(updatesFor(app)).hasSize(1);
        assertThat(mailTo("admin@codewalnut.test", name)).hasSize(1);
    }

    @Test
    void onlyKeyStagesSendAnUpdate() throws Exception {
        String name = "Ravi Kumar " + tag;
        String app = candidate(name);
        perform(RECRUITER, "PATCH", "/api/v1/applications/" + app + "/stage", "{\"stage\":\"INTERVIEWED\"}").andExpect(status().isOk());
        assertThat(updatesFor(app)).isEmpty();

        perform(RECRUITER, "PATCH", "/api/v1/applications/" + app + "/stage", "{\"stage\":\"SHORTLISTED\",\"note\":\"Strong DSA round\"}")
                .andExpect(status().isOk());
        List<Map<String, Object>> mine = updatesFor(app);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("kind")).isEqualTo("STAGE_REACHED");
        assertThat(mine.get(0).get("title")).isEqualTo(name + ": Shortlisted (Java Intern " + tag + ")");
        assertThat((String) mine.get(0).get("body")).contains("from Interviewed to Shortlisted", "Note: Strong DSA round",
                "Stage: Shortlisted", tag + "@example.com");
        assertThat(mailTo("admin@codewalnut.test", name + ": Shortlisted")).hasSize(1);

        perform(RECRUITER, "PATCH", "/api/v1/applications/" + app + "/stage", "{\"stage\":\"REJECTED\",\"note\":\"Took another offer\"}")
                .andExpect(status().isOk());
        assertThat(updatesFor(app)).hasSize(1);

        String keyStages = mockMvc.perform(get("/api/v1/admin-updates").with(ADMIN)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(keyStages, "$.keyStages")).containsExactly("Shortlisted", "Offer sent", "Joined");
    }

    @Test
    void withoutGmailTheUpdateIsKeptAndTheMoveStillWorks() throws Exception {
        String name = "Meera Iyer " + tag;
        String app = candidate(name);
        mail.setConnected(false);
        perform(RECRUITER, "PATCH", "/api/v1/applications/" + app + "/stage", "{\"stage\":\"SELECTED\"}").andExpect(status().isOk());
        List<Map<String, Object>> mine = updatesFor(app);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("emailStatus")).isEqualTo("SKIPPED");
        assertThat(mailTo("admin@codewalnut.test", name)).isEmpty();

        // An admin making the change themselves has nobody else to email.
        String other = candidate("Kiran Shah " + tag);
        mail.setConnected(true);
        perform(ADMIN, "PATCH", "/api/v1/applications/" + other + "/stage", "{\"stage\":\"JOINED\"}").andExpect(status().isOk());
        assertThat(updatesFor(other).get(0).get("emailStatus")).isIn("NONE", "SENT");
    }

    @Test
    void onlyAdminsCanReadUpdates() throws Exception {
        for (RequestPostProcessor who : List.of(RECRUITER, HIRING_MANAGER, INTERVIEWER, user("approver@codewalnut.test"))) {
            mockMvc.perform(get("/api/v1/admin-updates").with(who)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin-updates")).andExpect(status().is4xxClientError());
        MockHttpSession candidate = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"cand." + tag + "@gmail.com\"}"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin-updates").session(candidate)).andExpect(status().is4xxClientError());
    }
}
