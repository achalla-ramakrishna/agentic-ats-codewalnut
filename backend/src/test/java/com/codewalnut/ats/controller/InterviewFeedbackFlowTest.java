package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.repository.InterviewRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** INT-24…INT-27: feedback after an interview. Staff on the panel give it; candidates and clients never see it. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class InterviewFeedbackFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final RequestPostProcessor APPROVER = user("approver@codewalnut.test");

    private static final String HELD_YES = "{\"attendance\":\"HELD\",\"recommendation\":\"YES\","
            + "\"ratings\":[{\"competency\":\"Problem solving\",\"rating\":3,\"note\":\"Clear approach\"},"
            + "{\"competency\":\"Communication\",\"rating\":4},{\"competency\":\"Role fit\",\"rating\":null}],"
            + "\"strengths\":\"Explained the HashMap internals well\",\"concerns\":\"Slow on recursion\","
            + "\"questionsAsked\":\"Reverse a linked list\",\"notes\":\"Good camera and audio\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewRepository interviewRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private String send(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions feedback(RequestPostProcessor who, String interviewId, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/interviews/" + interviewId + "/feedback").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    /** Returns [applicationId, interviewId] for an interview the admin schedules, with the given panel. */
    private String[] interview(String interviewers) throws Exception {
        String job = JsonPath.read(send(ADMIN, "/api/v1/jobs", "{\"title\":\"Intern " + tag + "\",\"hiringType\":\"INTERNAL\"}"), "$.id");
        String app = JsonPath.read(send(ADMIN, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Ravi Kumar\",\"stage\":\"SCREENING\",\"email\":\"ravi." + tag + "@gmail.com\"}"), "$.id");
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String id = JsonPath.read(send(ADMIN, "/api/v1/applications/" + app + "/interviews",
                "{\"title\":\"CodeWalnut interview\",\"startAt\":\"" + start + "\",\"durationMinutes\":45,"
                        + "\"timeZone\":\"Asia/Kolkata\",\"interviewerEmails\":[" + interviewers + "]}"), "$.id");
        return new String[] {app, id};
    }

    /** The interview happened an hour ago. */
    private void moveToPast(String interviewId) {
        Interview i = interviewRepository.findById(UUID.fromString(interviewId)).orElseThrow();
        i.setStartAt(Instant.now().minus(Duration.ofHours(1)));
        i.setEndAt(Instant.now().minus(Duration.ofMinutes(15)));
        interviewRepository.save(i);
    }

    @Test
    void panelGivesFeedbackIndependentlyAndHiringStaffSeeItAll() throws Exception {
        String[] iv = interview("\"Interviewer@CodeWalnut.test\"");
        String id = iv[1];

        // Feedback opens when the interview starts.
        feedback(ADMIN, id, HELD_YES).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("starts")));
        moveToPast(id);

        String due = mockMvc.perform(get("/api/v1/interviews/feedback-due").with(INTERVIEWER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(due).contains(id);
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").with(INTERVIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onPanel").value(true))
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.mine").doesNotExist())
                .andExpect(jsonPath("$.competencies.length()").value(6))
                .andExpect(jsonPath("$.interview.meetLink").value(Matchers.startsWith("https://")));

        // The organiser submits first.
        feedback(ADMIN, id, HELD_YES).andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.recommendation").value("YES"))
                .andExpect(jsonPath("$.mine.averageRating").value(3.5))
                .andExpect(jsonPath("$.mine.ratings.length()").value(3));

        // The interviewer can't see it until they've given their own.
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").with(INTERVIEWER))
                .andExpect(jsonPath("$.others.length()").value(0))
                .andExpect(jsonPath("$.hiddenCount").value(1));
        feedback(INTERVIEWER, id, "{\"attendance\":\"HELD\",\"ratings\":[{\"competency\":\"Communication\",\"rating\":2}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("recommendation")));
        feedback(INTERVIEWER, id, "{\"attendance\":\"HELD\",\"recommendation\":\"NO\",\"ratings\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("rate at least one")));
        feedback(INTERVIEWER, id, "{\"attendance\":\"HELD\",\"recommendation\":\"NO\","
                + "\"ratings\":[{\"competency\":\"Coding / hands-on\",\"rating\":5}]}")
                .andExpect(status().isBadRequest());
        feedback(INTERVIEWER, id, "{\"attendance\":\"HELD\",\"recommendation\":\"NO\","
                + "\"ratings\":[{\"competency\":\"Coding / hands-on\",\"rating\":2}],\"concerns\":\"Could not finish the loop\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.others.length()").value(1))
                .andExpect(jsonPath("$.others[0].strengths").value("Explained the HashMap internals well"))
                .andExpect(jsonPath("$.hiddenCount").value(0));
        // Editing keeps one entry per person.
        feedback(INTERVIEWER, id, "{\"attendance\":\"HELD\",\"recommendation\":\"STRONG_NO\","
                + "\"ratings\":[{\"competency\":\"Coding / hands-on\",\"rating\":1}]}")
                .andExpect(jsonPath("$.mine.recommendation").value("STRONG_NO"))
                .andExpect(jsonPath("$.others.length()").value(1));
        assertThat(mockMvc.perform(get("/api/v1/interviews/feedback-due").with(INTERVIEWER))
                .andReturn().getResponse().getContentAsString()).doesNotContain(id);

        // A hiring manager who wasn't on the panel sees everything but can't add feedback.
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").with(HIRING_MANAGER))
                .andExpect(jsonPath("$.onPanel").value(false))
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.others.length()").value(2));
        feedback(HIRING_MANAGER, id, HELD_YES).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/applications/" + iv[0] + "/interview-feedback").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submitted").value(2))
                .andExpect(jsonPath("$[0].panelSize").value(2))
                .andExpect(jsonPath("$[0].recommendations", Matchers.containsInAnyOrder("YES", "STRONG_NO")));

        mockMvc.perform(get("/api/v1/applications/" + iv[0] + "/history").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void noShowNeedsNoScoresAndCancelledInterviewsTakeNone() throws Exception {
        String id = interview("\"interviewer@codewalnut.test\"")[1];
        moveToPast(id);
        feedback(INTERVIEWER, id, "{\"attendance\":\"CANDIDATE_NO_SHOW\",\"notes\":\"Waited 15 minutes\",\"strengths\":\"ignored\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.attendance").value("CANDIDATE_NO_SHOW"))
                .andExpect(jsonPath("$.mine.strengths").doesNotExist())
                .andExpect(jsonPath("$.mine.notes").value("Waited 15 minutes"));
        // An interview that ended early is still scored.
        feedback(ADMIN, id, HELD_YES.replace("\"HELD\"", "\"ENDED_EARLY\"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.attendance").value("ENDED_EARLY"))
                .andExpect(jsonPath("$.mine.recommendation").value("YES"))
                .andExpect(jsonPath("$.mine.ratings.length()").value(3));

        String cancelled = interview("\"interviewer@codewalnut.test\"")[1];
        send(ADMIN, "/api/v1/interviews/" + cancelled + "/cancel", "{\"reason\":\"Candidate asked to move it\"}");
        moveToPast(cancelled);
        feedback(INTERVIEWER, cancelled, HELD_YES).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("cancelled")));
    }

    @Test
    void outsidersCannotReadOrWriteFeedback() throws Exception {
        String[] iv = interview("");
        String id = iv[1];
        moveToPast(id);
        feedback(ADMIN, id, HELD_YES).andExpect(status().isOk());

        // An interviewer who wasn't on this panel can't find it.
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").with(INTERVIEWER)).andExpect(status().isNotFound());
        feedback(INTERVIEWER, id, HELD_YES).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/" + iv[0] + "/interview-feedback").with(INTERVIEWER))
                .andExpect(status().isForbidden());
        // A recruiter can add feedback for an interview they ran on someone's behalf.
        feedback(RECRUITER, id, HELD_YES).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").with(APPROVER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interviews/feedback-due").with(APPROVER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback")).andExpect(status().is4xxClientError());

        MockHttpSession candidate = signIn("ravi." + tag + "@gmail.com");
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").session(candidate)).andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/v1/interviews/" + id + "/feedback").session(candidate).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(HELD_YES)).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/applications/" + iv[0] + "/interview-feedback").session(candidate))
                .andExpect(status().is4xxClientError());

        String client = JsonPath.read(send(ADMIN, "/api/v1/clients", "{\"name\":\"Feedback Co " + tag + "\"}"), "$.id");
        String contact = "hm." + tag + "@feedbackco.example";
        send(ADMIN, "/api/v1/clients/" + client + "/contacts", "{\"email\":\"" + contact + "\",\"name\":\"HM\"}");
        MockHttpSession clientSession = signIn(contact);
        mockMvc.perform(get("/api/v1/interviews/" + id + "/feedback").session(clientSession)).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/applications/" + iv[0] + "/interview-feedback").session(clientSession))
                .andExpect(status().is4xxClientError());
    }
}
