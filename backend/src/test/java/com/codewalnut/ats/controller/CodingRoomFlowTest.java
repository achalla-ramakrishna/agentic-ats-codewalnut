package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** INT-36…INT-39: live coding rooms, against the fake sandbox (it prints its input back). Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class CodingRoomFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions staff(RequestPostProcessor who, MockHttpServletRequestBuilder r, String json)
            throws Exception {
        return mockMvc.perform(r.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions cand(MockHttpSession s, MockHttpServletRequestBuilder r, String json)
            throws Exception {
        return mockMvc.perform(r.session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    /** An interview with the interviewer on the panel; returns its id. */
    private String interview(String candidateEmail) throws Exception {
        String job = JsonPath.read(body(staff(ADMIN, post("/api/v1/jobs"), "{\"title\":\"Java Dev " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(staff(ADMIN, post("/api/v1/jobs/" + job + "/applications"),
                "{\"name\":\"Asha Rao\",\"stage\":\"SCREENING\"" + (candidateEmail == null ? "" : ",\"email\":\"" + candidateEmail + "\"") + "}")), "$.id");
        Instant earlier = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES);
        return JsonPath.read(body(staff(ADMIN, post("/api/v1/applications/" + app + "/interviews/log"),
                "{\"startAt\":\"" + earlier + "\",\"durationMinutes\":60,\"timeZone\":\"Asia/Kolkata\",\"interviewerEmails\":[\"interviewer@codewalnut.test\"]}")),
                "$.id");
    }

    @Test
    void interviewerOpensARoomAndWatchesTheCandidateCode() throws Exception {
        String email = "asha." + tag + "@gmail.com";
        String id = interview(email);
        staff(INTERVIEWER, get("/api/v1/interviews/" + id + "/coding-room"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room").doesNotExist())
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.runnerAvailable").value(true));
        mockMvc.perform(get("/api/v1/coding-problems").with(INTERVIEWER))
                .andExpect(jsonPath("$[?(@.id == 'two-sum')].title").value(Matchers.contains("Two sum")));

        String started = body(staff(INTERVIEWER, post("/api/v1/interviews/" + id + "/coding-room"), "{\"problemId\":\"two-sum\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.status").value("OPEN"))
                .andExpect(jsonPath("$.room.problem.title").value("Two sum"))
                .andExpect(jsonPath("$.room.problem.samples.length()").value(2))
                .andExpect(jsonPath("$.room.linkPath").value(Matchers.startsWith("/coding/"))));
        String token = JsonPath.<String>read(started, "$.room.linkPath").substring("/coding/".length());

        // The candidate signs in and codes.
        MockHttpSession candidate = signIn(email);
        cand(candidate, get("/api/v1/candidate/coding/" + token), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.problem.title").value("Two sum"))
                .andExpect(jsonPath("$.notes").doesNotExist());
        cand(candidate, put("/api/v1/candidate/coding/" + token), "{\"language\":\"python\",\"code\":\"print(input())\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("python"));
        staff(INTERVIEWER, get("/api/v1/interviews/" + id + "/coding-room"), "")
                .andExpect(jsonPath("$.room.code").value("print(input())"))
                .andExpect(jsonPath("$.room.language").value("python"))
                .andExpect(jsonPath("$.room.candidateSeenAt").isNotEmpty())
                .andExpect(jsonPath("$.room.codeUpdatedAt").isNotEmpty());
        cand(candidate, post("/api/v1/candidate/coding/" + token + "/run"), "{\"language\":\"python\",\"source\":\"print(input())\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRun.total").value(2))
                .andExpect(jsonPath("$.runsLeft").value(199));

        // Next problem: a typed one; the first is kept in the history.
        staff(INTERVIEWER, post("/api/v1/interviews/" + id + "/coding-room"),
                "{\"title\":\"Echo\",\"statement\":\"Print the input back.\",\"sampleInput\":\"hello\",\"sampleOutput\":\"hello\"}")
                .andExpect(jsonPath("$.room.problem.title").value("Echo"))
                .andExpect(jsonPath("$.room.history[0].title").value("Two sum"))
                .andExpect(jsonPath("$.room.history[0].code").value("print(input())"))
                .andExpect(jsonPath("$.room.history[0].total").value(2))
                .andExpect(jsonPath("$.room.lastRun").doesNotExist());
        cand(candidate, post("/api/v1/candidate/coding/" + token + "/run"), "{\"language\":\"python\",\"source\":\"print(input())\"}")
                .andExpect(jsonPath("$.lastRun.passed").value(1))
                .andExpect(jsonPath("$.lastRun.total").value(1));

        // Private notes never reach the candidate.
        staff(INTERVIEWER, put("/api/v1/interviews/" + id + "/coding-room/notes"), "{\"notes\":\"Needed a hint on the hash map\"}")
                .andExpect(jsonPath("$.room.notes").value("Needed a hint on the hash map"));
        assertThat(body(cand(candidate, get("/api/v1/candidate/coding/" + token), ""))).doesNotContain("hint on the hash map");

        // Ending makes it read-only.
        staff(INTERVIEWER, post("/api/v1/interviews/" + id + "/coding-room/end"), "").andExpect(jsonPath("$.room.status").value("ENDED"));
        cand(candidate, put("/api/v1/candidate/coding/" + token), "{\"language\":\"python\",\"code\":\"x\"}").andExpect(status().isConflict());
        cand(candidate, get("/api/v1/candidate/coding/" + token), "").andExpect(jsonPath("$.status").value("ENDED"));
    }

    @Test
    void onlyThatCandidateAndThePanelGetIn() throws Exception {
        String email = "ravi." + tag + "@gmail.com";
        String id = interview(email);
        String token = JsonPath.<String>read(body(staff(ADMIN, post("/api/v1/interviews/" + id + "/coding-room"), "{\"problemId\":\"two-sum\"}")),
                "$.room.linkPath").substring("/coding/".length());

        // Another candidate with the link, a wrong link, and a staff session get nothing.
        cand(signIn("someone.else." + tag + "@gmail.com"), get("/api/v1/candidate/coding/" + token), "").andExpect(status().isNotFound());
        cand(signIn(email), get("/api/v1/candidate/coding/nope" + tag), "").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/candidate/coding/" + token).with(ADMIN)).andExpect(status().is4xxClientError());

        // An interviewer not on this panel can't find it; approvers can't use rooms at all.
        String other = interview("other." + tag + "@gmail.com");
        staff(ADMIN, put("/api/v1/interviews/" + other + "/coding-room/notes"), "{}").andExpect(status().isNotFound());
        String notMine = JsonPath.read(body(staff(ADMIN, post("/api/v1/jobs"), "{\"title\":\"QA " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(staff(ADMIN, post("/api/v1/jobs/" + notMine + "/applications"),
                "{\"name\":\"Meera\",\"stage\":\"SCREENING\",\"email\":\"meera." + tag + "@gmail.com\"}")), "$.id");
        String noPanel = JsonPath.read(body(staff(ADMIN, post("/api/v1/applications/" + app + "/interviews/log"),
                "{\"startAt\":\"" + Instant.now().minus(Duration.ofMinutes(5)) + "\",\"durationMinutes\":45,\"timeZone\":\"Asia/Kolkata\"}")), "$.id");
        mockMvc.perform(get("/api/v1/interviews/" + noPanel + "/coding-room").with(INTERVIEWER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/interviews/" + id + "/coding-room").with(user("approver@codewalnut.test"))).andExpect(status().isForbidden());
        // A hiring manager not on the panel can watch but not run the room.
        staff(HIRING_MANAGER, get("/api/v1/interviews/" + id + "/coding-room"), "").andExpect(jsonPath("$.canManage").value(false));
        staff(HIRING_MANAGER, post("/api/v1/interviews/" + id + "/coding-room/end"), "").andExpect(status().isForbidden());

        // The candidate needs an email to sign in with.
        String noEmail = interview(null);
        staff(ADMIN, post("/api/v1/interviews/" + noEmail + "/coding-room"), "{\"problemId\":\"two-sum\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("email")));
        staff(ADMIN, post("/api/v1/interviews/" + id + "/coding-room"), "{\"title\":\"\"}").andExpect(status().isBadRequest());
    }
}
