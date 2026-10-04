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
 * Coding questions (ASMT-29, ASMT-37, ADR-0016): staff write one with sample and hidden tests,
 * the candidate runs code against the samples and submits, the code is graded against every
 * test after submit. Tests use the fake sandbox (FakeCodeRunner), which prints its input back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CodingFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final String HIDDEN = "HIDDEN_EXPECTED_7";

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions json(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var builder = switch (method) {
            case "GET" -> get(url);
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

    /** An "echo the input" problem: the fake sandbox passes the sample and two of three hidden tests. */
    private static final String ECHO = """
            {"kind":"CODING","prompt":"Echo\\n\\nPrint the line you are given.","points":8,
             "coding":{"languages":["python","java"],
                       "samples":[{"input":"hi\\n","output":"hi\\n"}],
                       "tests":[{"input":"1\\n","output":"1\\n"},{"input":"2\\n","output":"2"},{"input":"3\\n","output":"%s\\n"}],
                       "inputFormat":"One line.","outputFormat":"The same line."}}""".formatted(HIDDEN);

    private String[] codingTest() throws Exception {
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/assessments",
                        "{\"title\":\"Coding " + tag + "\",\"category\":\"CODING\",\"durationMinutes\":45,\"passPercent\":50}")
                .andExpect(status().isCreated())), "$.summary.id");
        // A coding question needs hidden tests.
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions", """
                {"kind":"CODING","prompt":"No tests","points":5,"coding":{"samples":[{"input":"1","output":"1"}],"tests":[]}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("hidden test")));
        String detail = body(json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions", ECHO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].kind").value("CODING"))
                .andExpect(jsonPath("$.questions[0].coding.spec.languages", Matchers.contains("python", "java")))
                .andExpect(jsonPath("$.questions[0].coding.spec.starter.python").value(Matchers.containsString("sys.stdin")))
                .andExpect(jsonPath("$.questions[0].coding.tests.length()").value(3))
                .andExpect(jsonPath("$.summary.totalPoints").value(8)));
        String coding = JsonPath.read(detail, "$.questions[0].id");
        String shortQ = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"SHORT_ANSWER\",\"prompt\":\"2+2?\",\"acceptedAnswers\":[\"4\"],\"points\":2}")), "$.questions[1].id");
        return new String[] {test, coding, shortQ};
    }

    private String send(String test, String email) throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Coding " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Asha Coder\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        return JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                        "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isCreated())), "$.invite.id");
    }

    @Test
    void writeRunSubmitAndGradeACodingQuestion() throws Exception {
        String[] ids = codingTest();
        String test = ids[0], coding = ids[1], shortQ = ids[2];

        // Staff check the question with a solution: every test, inputs and expected outputs shown.
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions/" + coding + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(3))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.cases[3].status").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.cases[3].expected").value(HIDDEN + "\n"));
        json(INTERVIEWER, "POST", "/api/v1/assessments/" + test + "/questions/" + coding + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}").andExpect(status().isForbidden());
        json(RECRUITER, "GET", "/api/v1/coding/status", "").andExpect(jsonPath("$.available").value(true));

        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        String email = "asha." + tag + "@gmail.com";
        String invite = send(test, email);
        MockHttpSession session = candidateSession(email);

        // The candidate sees the statement, samples and starter code, but never the hidden tests.
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].kind").value("CODING"))
                .andExpect(jsonPath("$.questions[0].coding.samples[0].output").value("hi\n"))
                .andExpect(jsonPath("$.questions[0].coding.starter.java").value(Matchers.containsString("class Main"))));
        assertThat(taking).doesNotContain(HIDDEN);

        // Run: samples only.
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + coding + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compiled").value(true))
                .andExpect(jsonPath("$.passed").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.cases[0].input").value("hi\n"))
                .andExpect(jsonPath("$.runsLeft").value(29))
                .andExpect(jsonPath("$..expected", Matchers.not(Matchers.hasItem(HIDDEN + "\n"))));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + coding + "/run",
                "{\"language\":\"java\",\"source\":\"COMPILE_ERROR\"}")
                .andExpect(jsonPath("$.compiled").value(false))
                .andExpect(jsonPath("$.compileOutput").value(Matchers.containsString("expected")));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + coding + "/run",
                "{\"language\":\"cpp\",\"source\":\"int main(){}\"}").andExpect(status().isBadRequest());
        // Someone else can't run code on this test.
        candidate(candidateSession("other." + tag + "@gmail.com"), "POST",
                "/api/v1/candidate/tests/" + invite + "/questions/" + coding + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}").andExpect(status().isNotFound());

        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/activity",
                "{\"tabSwitches\":3,\"pastes\":1,\"pastedChars\":120}").andExpect(status().isNoContent());
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/activity",
                "{\"tabSwitches\":2,\"pastes\":1,\"pastedChars\":120}").andExpect(status().isNoContent());

        // Long code is kept whole (choice answers stay capped at 500 characters).
        String longSource = "print(input())\n" + "# padding\n".repeat(200);
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers",
                "{\"answers\":{\"" + coding + "\":[\"python\"," + quote(longSource) + "]}}")
                .andExpect(jsonPath("$.answers['" + coding + "'][1]").value(longSource));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit",
                "{\"answers\":{\"" + shortQ + "\":[\"4\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Graded after submit: 8 × 3/4 = 6 for the code, plus 2 = 8 of 10.
        json(RECRUITER, "GET", "/api/v1/tests/" + invite, "")
                .andExpect(jsonPath("$.invite.grading").value("DONE"))
                .andExpect(jsonPath("$.invite.score").value(8))
                .andExpect(jsonPath("$.invite.maxScore").value(10))
                .andExpect(jsonPath("$.invite.percent").value(80))
                .andExpect(jsonPath("$.answers[0].codeResult.language").value("python"))
                .andExpect(jsonPath("$.answers[0].codeResult.passed").value(3))
                .andExpect(jsonPath("$.answers[0].codeResult.total").value(4))
                .andExpect(jsonPath("$.answers[0].earned").value(6))
                .andExpect(jsonPath("$.answers[0].codeResult.cases[3].expected").value(HIDDEN + "\n"))
                .andExpect(jsonPath("$.activity.tabSwitches").value(3))
                .andExpect(jsonPath("$.activity.pastedChars").value(120))
                .andExpect(jsonPath("$.activity.runs").value(2));

        // Grading again gives the same result; only test managers can ask.
        json(INTERVIEWER, "POST", "/api/v1/tests/" + invite + "/regrade", "").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/regrade", "").andExpect(status().isOk());
        json(RECRUITER, "GET", "/api/v1/tests/" + invite, "")
                .andExpect(jsonPath("$.invite.grading").value("DONE"))
                .andExpect(jsonPath("$.invite.percent").value(80));
        // No more runs once submitted.
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + coding + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}").andExpect(status().isBadRequest());
    }

    @Test
    void blankCodeScoresZeroWithoutRunningAndRunsAreLimited() throws Exception {
        String[] ids = codingTest();
        json(RECRUITER, "POST", "/api/v1/assessments/" + ids[0] + "/publish", "").andExpect(status().isOk());
        String email = "ravi." + tag + "@gmail.com";
        String invite = send(ids[0], email);
        MockHttpSession session = candidateSession(email);
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "").andExpect(status().isOk());
        for (int i = 0; i < 30; i++) {
            candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + ids[1] + "/run",
                    "{\"language\":\"python\",\"source\":\"print(input())\"}").andExpect(status().isOk());
        }
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/questions/" + ids[1] + "/run",
                "{\"language\":\"python\",\"source\":\"print(input())\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("sample runs")));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit", "{\"answers\":{\"" + ids[2] + "\":[\"4\"]}}")
                .andExpect(status().isOk());
        json(RECRUITER, "GET", "/api/v1/tests/" + invite, "")
                .andExpect(jsonPath("$.invite.grading").value("DONE"))
                .andExpect(jsonPath("$.invite.percent").value(20))
                .andExpect(jsonPath("$.answers[0].earned").value(0));
    }

    /** ASMT-36: a coding paper from the built-in problems (Build from bank → Coding). */
    @Test
    void buildACodingPaperFromTheBank() throws Exception {
        json(RECRUITER, "GET", "/api/v1/question-bank/overview?area=CODING", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide[0].name").value("Basic programming"))
                .andExpect(jsonPath("$.presets[*].id", Matchers.hasItems("coding-fresher", "coding-mid", "coding-senior")));
        json(RECRUITER, "POST", "/api/v1/question-bank/build", """
                {"title":"Coding mid %s","area":"CODING","durationMinutes":60,"passPercent":50,"order":"EASY_FIRST",
                 "sections":[{"section":"FUNDAMENTALS","easy":0,"medium":1,"hard":0},{"section":"PRACTICAL","easy":0,"medium":1,"hard":0}]}"""
                .formatted(tag))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[*].kind", Matchers.everyItem(Matchers.is("CODING"))))
                .andExpect(jsonPath("$.questions[0].coding.spec.samples.length()").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.questions[0].coding.tests.length()").value(Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.questions[0].points").value(10));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
