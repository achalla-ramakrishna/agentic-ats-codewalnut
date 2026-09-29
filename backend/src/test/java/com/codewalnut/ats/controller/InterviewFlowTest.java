package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.CalendarClient.Invite;
import com.codewalnut.ats.client.FakeCalendarClient;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/**
 * Scheduling interviews on Google Calendar (INT-12…INT-19), against the fake calendar the dev
 * profile uses. Fake people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InterviewFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeCalendarClient calendar;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @AfterEach
    void resetCalendar() {
        calendar.setConnected(true);
        calendar.setFailing(false);
    }

    private String send(RequestPostProcessor who, String method, String url, String body) throws Exception {
        return perform(who, method, url, body).andReturn().getResponse().getContentAsString();
    }

    private ResultActions perform(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var request = "PATCH".equals(method) ? patch(url) : post(url);
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Returns [applicationId, candidateId] for a new candidate in a new opening. */
    private String[] candidate(String email) throws Exception {
        String job = JsonPath.read(send(ADMIN, "POST", "/api/v1/jobs",
                "{\"title\":\"Intern " + tag + "\",\"hiringType\":\"INTERNAL\"}"), "$.id");
        String body = "{\"name\":\"Ravi Kumar\",\"stage\":\"SCREENING\""
                + (email != null ? ",\"email\":\"" + email + "\"" : "") + "}";
        String created = send(ADMIN, "POST", "/api/v1/jobs/" + job + "/applications", body);
        return new String[] {JsonPath.read(created, "$.id"), JsonPath.read(created, "$.candidateId")};
    }

    private static String scheduleBody(Instant start, String interviewers) {
        return "{\"title\":\"CodeWalnut interview\",\"startAt\":\"" + start + "\",\"durationMinutes\":45,"
                + "\"timeZone\":\"Asia/Kolkata\",\"interviewerEmails\":[" + interviewers + "],"
                + "\"message\":\"Please keep your camera on.\"}";
    }

    private static Instant tomorrow() {
        return Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void schedulingSendsACalendarInviteWithMeetLinkToCandidateAndPanel() throws Exception {
        String email = "ravi." + tag + "@gmail.com";
        String[] app = candidate(email);
        Instant start = tomorrow();

        perform(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews",
                scheduleBody(start, "\" Interviewer@CodeWalnut.test \",\"" + email + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meetLink").value(org.hamcrest.Matchers.startsWith("https://example.com/fake-meet/")))
                .andExpect(jsonPath("$.interviewers.length()").value(1))
                .andExpect(jsonPath("$.interviewers[0]").value("interviewer@codewalnut.test"))
                .andExpect(jsonPath("$.organizerEmail").value("admin@codewalnut.test"));

        Invite invite = calendar.created().get(calendar.created().size() - 1);
        assertThat(invite.attendees()).containsExactly(email, "interviewer@codewalnut.test");
        assertThat(invite.start()).isEqualTo(start);
        assertThat(invite.end()).isEqualTo(start.plus(Duration.ofMinutes(45)));
        assertThat(invite.timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(invite.description()).startsWith("Please keep your camera on.").contains("Google Meet");

        mockMvc.perform(get("/api/v1/applications/" + app[0] + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].type").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$[0].note").value(org.hamcrest.Matchers.containsString("IST")));
        mockMvc.perform(get("/api/v1/applications/" + app[0] + "/interviews").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void interviewersSeeOnlyInterviewsTheyAreOn() throws Exception {
        String[] mine = candidate("mine." + tag + "@gmail.com");
        String[] other = candidate("other." + tag + "@gmail.com");
        String onPanel = JsonPath.read(send(ADMIN, "POST", "/api/v1/applications/" + mine[0] + "/interviews",
                scheduleBody(tomorrow(), "\"interviewer@codewalnut.test\"")), "$.id");
        String notOnPanel = JsonPath.read(send(ADMIN, "POST", "/api/v1/applications/" + other[0] + "/interviews",
                scheduleBody(tomorrow(), "")), "$.id");

        String forInterviewer = mockMvc.perform(get("/api/v1/interviews").with(INTERVIEWER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(forInterviewer).contains(onPanel).doesNotContain(notOnPanel);
        String forAdmin = mockMvc.perform(get("/api/v1/interviews").with(ADMIN)).andReturn().getResponse().getContentAsString();
        assertThat(forAdmin).contains(onPanel, notOnPanel);

        mockMvc.perform(get("/api/v1/applications/" + mine[0] + "/interviews").with(INTERVIEWER))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateWithoutEmailMustGetOneFirst() throws Exception {
        String[] app = candidate(null);
        perform(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("email")));

        perform(ADMIN, "PATCH", "/api/v1/candidates/" + app[1], "{\"email\":\"Late." + tag + "@Gmail.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("late." + tag + "@gmail.com"));
        perform(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isCreated());
    }

    @Test
    void candidateEmailsStayUnique() throws Exception {
        String taken = "taken." + tag + "@gmail.com";
        candidate(taken);
        String[] app = candidate(null);
        perform(ADMIN, "PATCH", "/api/v1/candidates/" + app[1], "{\"email\":\"" + taken + "\"}")
                .andExpect(status().isConflict());
        perform(HIRING_MANAGER, "PATCH", "/api/v1/candidates/" + app[1], "{\"phone\":\"9000000000\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void notConnectedAsksToConnectAndFailuresSaveNothing() throws Exception {
        String[] app = candidate("fail." + tag + "@gmail.com");

        calendar.setConnected(false);
        perform(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isPreconditionRequired());
        mockMvc.perform(get("/api/v1/calendar/status").with(ADMIN))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.redirectUri").value("http://localhost/oauth2/callback/google-calendar"));

        calendar.setConnected(true);
        calendar.setFailing(true);
        perform(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Nothing was sent")));

        mockMvc.perform(get("/api/v1/applications/" + app[0] + "/interviews").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidRequestsAreRefused() throws Exception {
        String[] app = candidate("bad." + tag + "@gmail.com");
        String url = "/api/v1/applications/" + app[0] + "/interviews";
        perform(ADMIN, "POST", url, scheduleBody(Instant.now().minus(Duration.ofHours(2)), ""))
                .andExpect(status().isBadRequest());
        perform(ADMIN, "POST", url, scheduleBody(tomorrow(), "\"not an email\"")).andExpect(status().isBadRequest());
        perform(ADMIN, "POST", url, scheduleBody(tomorrow(), "").replace("Asia/Kolkata", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
        perform(ADMIN, "POST", url, scheduleBody(tomorrow(), "").replace("45", "5")).andExpect(status().isBadRequest());
        assertThat(calendar.created()).noneMatch(i -> i.attendees().contains("bad." + tag + "@gmail.com"));
    }

    @Test
    void onlyTheOrganiserCancelsAndEveryoneIsNotified() throws Exception {
        String[] app = candidate("cancel." + tag + "@gmail.com");
        String interview = JsonPath.read(send(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews",
                scheduleBody(tomorrow(), "")), "$.id");

        perform(RECRUITER, "POST", "/api/v1/interviews/" + interview + "/cancel", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("admin@codewalnut.test")));

        int before = calendar.cancelled().size();
        perform(ADMIN, "POST", "/api/v1/interviews/" + interview + "/cancel", "{\"reason\":\"Candidate asked to move it\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Candidate asked to move it"));
        assertThat(calendar.cancelled()).hasSize(before + 1);

        mockMvc.perform(get("/api/v1/applications/" + app[0] + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].type").value("INTERVIEW_CANCELLED"));
        String upcoming = mockMvc.perform(get("/api/v1/interviews").with(ADMIN)).andReturn().getResponse().getContentAsString();
        assertThat(upcoming).doesNotContain(interview);
    }

    @Test
    void onlyPeopleWhoManageCandidatesSchedule() throws Exception {
        String[] app = candidate("perm." + tag + "@gmail.com");
        perform(HIRING_MANAGER, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isForbidden());
        perform(INTERVIEWER, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), ""))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void candidatesSeeTheirOwnUpcomingInterviewsOnly() throws Exception {
        String email = "portal." + tag + "@gmail.com";
        String[] app = candidate(email);
        send(ADMIN, "POST", "/api/v1/applications/" + app[0] + "/interviews", scheduleBody(tomorrow(), "\"interviewer@codewalnut.test\""));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);

        String body = mockMvc.perform(get("/api/v1/candidate/interviews").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("CodeWalnut interview"))
                .andExpect(jsonPath("$[0].meetLink").exists())
                .andReturn().getResponse().getContentAsString();
        // No panel, organiser, internal notes or ids.
        assertThat(body).doesNotContain("interviewer@codewalnut.test", "admin@codewalnut.test", app[0]);

        MockHttpSession stranger = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"stranger." + tag + "@gmail.com\"}"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/candidate/interviews").session(stranger)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/interviews").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void connectSendsStaffToGoogleAndBackToTheSameScreen() throws Exception {
        calendar.setConnected(false);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/v1/calendar/connect").param("returnTo", "/jobs/123?candidate=abc").session(session).with(ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google-calendar"))
                .andExpect(request().sessionAttribute(InterviewController.RETURN_TO, "/jobs/123?candidate=abc"));

        mockMvc.perform(get("/oauth2/callback/google-calendar").session(session)
                        .with(user("admin@codewalnut.test").roles("STAFF")))
                .andExpect(redirectedUrl("/jobs/123?candidate=abc"));

        calendar.setConnected(true);
        mockMvc.perform(get("/api/v1/calendar/connect").param("returnTo", "https://evil.example").with(ADMIN))
                .andExpect(redirectedUrl("/interviews"));
    }

    @Test
    void returnToOnlyAllowsSameSitePaths() {
        for (String bad : List.of("//evil.example", "https://evil.example", "/\\evil", "evil", "/a\nb")) {
            assertThat(InterviewController.safeReturnTo(bad)).isEqualTo("/interviews");
        }
        assertThat(InterviewController.safeReturnTo("/candidates?open=1")).isEqualTo("/candidates?open=1");
    }
}
