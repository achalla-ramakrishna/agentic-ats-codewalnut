package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
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

/**
 * Online tests (ASMT-08…): build a test from the starter bank, send it, the candidate takes it
 * against the timer, the score comes back. Fake people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HM = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssessmentInviteRepository inviteRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions json(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var builder = switch (method) {
            case "PUT" -> put(url);
            default -> post(url);
        };
        return mockMvc.perform(builder.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions candidate(MockHttpSession session, String method, String url, String body) throws Exception {
        var builder = switch (method) {
            case "GET" -> get(url);
            case "PUT" -> put(url);
            default -> post(url);
        };
        return mockMvc.perform(builder.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpSession candidateSession(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    /** A ready Java test: four starter questions drafted, then made ready. */
    private String readyJavaTest() throws Exception {
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/assessments",
                        "{\"title\":\"Java basics " + tag + "\",\"category\":\"JAVA\",\"durationMinutes\":30,\"passPercent\":60}")
                .andExpect(status().isCreated())), "$.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/draft", "{\"count\":4,\"level\":\"fresher\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(4))
                .andExpect(jsonPath("$.assessment.questions[*].aiDrafted", Matchers.everyItem(Matchers.is(true))));
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("READY"));
        return test;
    }

    private String[] candidateInOpening(String email) throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Tests " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Kiran Tester\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        return new String[] {job, app};
    }

    @Test
    void buildSendTakeAndScoreATest() throws Exception {
        String test = readyJavaTest();
        // Locked once ready.
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"SHORT_ANSWER\",\"prompt\":\"2+2?\",\"acceptedAnswers\":[\"4\"],\"points\":1}")
                .andExpect(status().isBadRequest());

        String email = "kiran." + tag + "@gmail.com";
        String[] ids = candidateInOpening(email);
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + ids[1] + "/tests",
                        "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invite.status").value("SENT"))
                .andExpect(jsonPath("$.message.body").value(Matchers.containsString("/tests/")))), "$.invite.id");
        json(RECRUITER, "POST", "/api/v1/applications/" + ids[1] + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isConflict());

        // Another candidate can't open it.
        MockHttpSession stranger = candidateSession("other." + tag + "@gmail.com");
        candidate(stranger, "POST", "/api/v1/candidate/tests/" + invite + "/start", "").andExpect(status().isNotFound());

        MockHttpSession session = candidateSession(email);
        candidate(session, "GET", "/api/v1/candidate/tests", "")
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].questionCount").value(4))
                .andExpect(jsonPath("$[0].jobTitle").value("Tests " + tag));
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.test.status").value("STARTED"))
                .andExpect(jsonPath("$.secondsLeft").value(Matchers.greaterThan(1700)))
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correct").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist()));
        List<String> q = JsonPath.read(taking, "$.questions[*].id");

        // Starter bank order: "false true" (option 1), LinkedHashSet (2), 17, @RestController (1).
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers",
                "{\"answers\":{\"" + q.get(0) + "\":[\"1\"],\"" + q.get(1) + "\":[\"0\"]}}")
                .andExpect(jsonPath("$.answers['" + q.get(0) + "'][0]").value("1"));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit",
                "{\"answers\":{\"" + q.get(2) + "\":[\" 17 \"],\"" + q.get(3) + "\":[\"1\"]}}")
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.percent").doesNotExist());
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers", "{\"answers\":{}}")
                .andExpect(status().isBadRequest());

        // Points: 1 + 0 + 2 + 1 = 4 of 5 → 80%, passed.
        mockMvc.perform(get("/api/v1/applications/" + ids[1] + "/tests").with(HM))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].score").value(4))
                .andExpect(jsonPath("$[0].maxScore").value(5))
                .andExpect(jsonPath("$[0].percent").value(80))
                .andExpect(jsonPath("$[0].passed").value(true));
        mockMvc.perform(get("/api/v1/tests/" + invite).with(HM))
                .andExpect(jsonPath("$.answers[1].earned").value(0))
                .andExpect(jsonPath("$.answers[1].correct[0]").value(2));
        mockMvc.perform(get("/api/v1/jobs/" + ids[0] + "/tests").with(RECRUITER))
                .andExpect(jsonPath("$[0].percent").value(80));
        mockMvc.perform(get("/api/v1/applications/" + ids[1] + "/history").with(RECRUITER))
                .andExpect(jsonPath("$[*].type", Matchers.hasItems("TEST_SENT", "TEST_SUBMITTED")));

        // The assistant knows the result.
        json(RECRUITER, "POST", "/api/v1/jobs/" + ids[0] + "/assistant", "{\"instruction\":\"who took the java test?\"}")
                .andExpect(jsonPath("$.matches[0].name").value("Kiran Tester"));
    }

    @Test
    void theTimerIsEnforcedAndUnstartedTestsExpire() throws Exception {
        String test = readyJavaTest();
        String email = "late." + tag + "@gmail.com";
        String[] ids = candidateInOpening(email);
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + ids[1] + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        MockHttpSession session = candidateSession(email);
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", ""));
        String first = JsonPath.<List<String>>read(taking, "$.questions[*].id").get(0);
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers", "{\"answers\":{\"" + first + "\":[\"1\"]}}");

        // Time runs out: what was saved is scored.
        var row = inviteRepository.findById(UUID.fromString(invite)).orElseThrow();
        row.setDeadlineAt(Instant.now().minusSeconds(120));
        inviteRepository.save(row);
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers", "{\"answers\":{\"" + first + "\":[\"0\"]}}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/applications/" + ids[1] + "/tests").with(RECRUITER))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].score").value(1))
                .andExpect(jsonPath("$[0].passed").value(false));

        // A second test, never started, expires at the due date and can be nudged with two more days.
        String other = readyJavaTest();
        String second = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + ids[1] + "/tests",
                "{\"assessmentId\":\"" + other + "\",\"dueDays\":1,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        var unstarted = inviteRepository.findById(UUID.fromString(second)).orElseThrow();
        unstarted.setDueAt(Instant.now().minusSeconds(60));
        inviteRepository.save(unstarted);
        candidate(session, "POST", "/api/v1/candidate/tests/" + second + "/start", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("past its due date")));
        json(RECRUITER, "POST", "/api/v1/tests/" + second + "/remind", "{\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invite.status").value("SENT"))
                .andExpect(jsonPath("$.invite.reminderCount").value(1))
                .andExpect(jsonPath("$.message.body").value(Matchers.containsString("reminder")));
        candidate(session, "POST", "/api/v1/candidate/tests/" + second + "/start", "").andExpect(status().isOk());
    }

    @Test
    void questionsAreValidatedAndOnlyManagersBuildTests() throws Exception {
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/assessments",
                "{\"title\":\"Aptitude " + tag + "\",\"category\":\"APTITUDE\",\"durationMinutes\":20,\"passPercent\":50}")), "$.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"SINGLE_CHOICE\",\"prompt\":\"Pick\",\"options\":[\"a\",\"b\"],\"correct\":[0,1],\"points\":1}")
                .andExpect(status().isBadRequest());
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"MULTI_CHOICE\",\"prompt\":\"Pick all even\",\"options\":[\"2\",\"3\",\"4\"],\"correct\":[2,0],\"points\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correct", Matchers.contains(0, 2)));
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assessments").with(HM)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assessments/" + test).with(HM)).andExpect(status().isForbidden());
        json(HM, "POST", "/api/v1/assessments",
                "{\"title\":\"x\",\"category\":\"JAVA\",\"durationMinutes\":20,\"passPercent\":50}").andExpect(status().isForbidden());
        // Unknown candidates get "not found", not "forbidden".
        candidate(candidateSession("nobody." + tag + "@gmail.com"), "GET", "/api/v1/candidate/tests", "")
                .andExpect(jsonPath("$").isEmpty());
        // A copy is a fresh draft with the same questions.
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/duplicate", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.questions.length()").value(1));
    }
}
