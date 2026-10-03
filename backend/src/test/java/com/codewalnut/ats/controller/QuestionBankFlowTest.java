package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
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

/** Question bank and paper builder (ASMT-18…): built-in aptitude bank, presets, pictures, section scores. */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionBankFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HM = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions send(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var b = method.equals("PUT") ? put(url) : post(url);
        return mockMvc.perform(b.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    @Test
    void theBuiltInAptitudeBankIsReadyWithPictures() throws Exception {
        mockMvc.perform(get("/api/v1/question-bank/overview").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts[?(@.section == 'QUANT' && @.difficulty == 'EASY')].count",
                        Matchers.everyItem(Matchers.greaterThanOrEqualTo(26))))
                .andExpect(jsonPath("$.presets[*].id", Matchers.hasItems("quick", "tcs-nqt", "wipro-nlth")))
                .andExpect(jsonPath("$.topics[*].topic", Matchers.hasItems("Data interpretation", "Non-verbal: figure series", "Syllogisms")));
        mockMvc.perform(get("/api/v1/question-bank?section=LOGICAL&pictures=true&topic=Non-verbal: mirror and water images").with(RECRUITER))
                .andExpect(jsonPath("$.total").value(Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.items[0].optionFigures.length()").value(4))
                .andExpect(jsonPath("$.items[0].figure").value(Matchers.startsWith("<svg")));
        mockMvc.perform(get("/api/v1/question-bank").with(HM)).andExpect(status().isForbidden());
    }

    @Test
    void buildATcsStylePaperTakeItAndSeeSectionScores() throws Exception {
        String detail = body(send(RECRUITER, "POST", "/api/v1/question-bank/build", """
                {"title":"Aptitude %s","area":"APTITUDE","durationMinutes":75,"passPercent":60,"order":"BY_SECTION",
                 "sections":[{"section":"QUANT","easy":8,"medium":8,"hard":4},{"section":"VERBAL","easy":10,"medium":10,"hard":5},
                             {"section":"LOGICAL","easy":8,"medium":8,"hard":4}]}""".formatted(tag))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.questionCount").value(65))
                .andExpect(jsonPath("$.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.questions[0].section").value("QUANT"))
                .andExpect(jsonPath("$.questions[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.questions[19].difficulty").value("HARD"))
                .andExpect(jsonPath("$.questions[20].section").value("VERBAL"))
                .andExpect(jsonPath("$.questions[64].section").value("LOGICAL")));
        String test = JsonPath.read(detail, "$.summary.id");
        send(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());

        // Send it and take it, answering only the numerical section correctly.
        String email = "apt." + tag + "@gmail.com";
        String job = JsonPath.read(body(send(RECRUITER, "POST", "/api/v1/jobs", "{\"title\":\"Apt " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String app = JsonPath.read(body(send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Apt Tester\",\"email\":\"" + email + "\",\"stage\":\"SOURCED\"}")), "$.id");
        String invite = JsonPath.read(body(send(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":2,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}")).andReturn().getRequest().getSession(false);
        String taking = mockMvc.perform(post("/api/v1/candidate/tests/" + invite + "/start").session(session).with(csrf()))
                .andExpect(jsonPath("$.questions[0].section").value("QUANT"))
                .andExpect(jsonPath("$.questions[*].correct").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(taking, "$.questions[*].id");
        List<Integer> correct = JsonPath.read(detail, "$.questions[*].correct[0]");
        StringBuilder answers = new StringBuilder("{\"answers\":{");
        for (int k = 0; k < 20; k++) {
            answers.append(k == 0 ? "" : ",").append('"').append(ids.get(k)).append("\":[\"").append(correct.get(k)).append("\"]");
        }
        answers.append("}}");
        mockMvc.perform(post("/api/v1/candidate/tests/" + invite + "/submit").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(answers.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.sections[0].label").value("Numerical ability"))
                .andExpect(jsonPath("$.sections[0].score").value(8 + 16 + 12))
                .andExpect(jsonPath("$.sections[0].max").value(8 + 16 + 12))
                .andExpect(jsonPath("$.sections[1].label").value("Verbal ability"))
                .andExpect(jsonPath("$.sections[1].score").value(0))
                .andExpect(jsonPath("$.sections[2].questions").value(20));
    }

    @Test
    void shortagesAreExplained() throws Exception {
        send(RECRUITER, "POST", "/api/v1/question-bank/build", """
                {"title":"Too many","area":"APTITUDE","durationMinutes":30,"passPercent":50,"order":"SHUFFLED",
                 "topics":[{"section":"VERBAL","topic":"Synonyms","easy":0,"medium":0,"hard":20}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("Synonyms · hard: 16 available, 20 asked")));
    }

    @Test
    void theTopicGuideHasFiftyQuestionsPerTopic() throws Exception {
        mockMvc.perform(get("/api/v1/question-bank/overview").with(RECRUITER))
                .andExpect(jsonPath("$.guide.length()").value(Matchers.greaterThanOrEqualTo(26)))
                .andExpect(jsonPath("$.guide[0].name").value("Percentages"))
                .andExpect(jsonPath("$.guide[0].covers").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.guide[0].easy").value(17))
                .andExpect(jsonPath("$.guide[0].medium").value(17))
                .andExpect(jsonPath("$.guide[0].hard").value(16));
    }

    @Test
    void technicalBanksHaveLevelsPresetsAndCodeQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/question-bank/overview?area=JAVA").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide.length()").value(Matchers.greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.guide[0].section").value("FUNDAMENTALS"))
                .andExpect(jsonPath("$.guide[0].level").value("Freshers"))
                .andExpect(jsonPath("$.guide[0].easy").value(4))
                .andExpect(jsonPath("$.presets[*].id", Matchers.hasItems("java-fresher", "java-mid", "java-senior")));
        mockMvc.perform(get("/api/v1/question-bank?area=SQL&section=ADVANCED").with(RECRUITER))
                .andExpect(jsonPath("$.total").value(Matchers.greaterThanOrEqualTo(36)))
                .andExpect(jsonPath("$.items[0].area").value("SQL"));
        send(RECRUITER, "POST", "/api/v1/question-bank/build", """
                {"title":"Java freshers %s","area":"JAVA","durationMinutes":30,"passPercent":60,"order":"EASY_FIRST",
                 "sections":[{"section":"FUNDAMENTALS","easy":8,"medium":8,"hard":4}]}""".formatted(tag))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.questionCount").value(20))
                .andExpect(jsonPath("$.summary.category").value("JAVA"))
                .andExpect(jsonPath("$.questions[*].code", Matchers.hasItem(Matchers.notNullValue())));
        // A technical question cannot use an aptitude section.
        send(RECRUITER, "POST", "/api/v1/question-bank", """
                {"area":"PYTHON","section":"QUANT","topic":"Basics","difficulty":"EASY",
                 "question":{"kind":"SINGLE_CHOICE","prompt":"2 + 2?","options":["4","5"],"correct":[0],"points":1}}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void buildByTopicsHardestFirst() throws Exception {
        send(RECRUITER, "POST", "/api/v1/question-bank/build", """
                {"title":"Topics %s","area":"APTITUDE","durationMinutes":15,"passPercent":50,"order":"HARD_FIRST",
                 "topics":[{"section":"QUANT","topic":"Percentages","easy":1,"medium":1,"hard":1},
                           {"section":"LOGICAL","topic":"Clocks","easy":2,"medium":0,"hard":0},
                           {"section":"LOGICAL","topic":"Syllogisms","easy":0,"medium":0,"hard":2}]}""".formatted(tag))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.questionCount").value(7))
                .andExpect(jsonPath("$.questions[0].difficulty").value("HARD"))
                .andExpect(jsonPath("$.questions[2].difficulty").value("HARD"))
                .andExpect(jsonPath("$.questions[3].difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.questions[6].difficulty").value("EASY"))
                .andExpect(jsonPath("$.questions[*].topic", Matchers.everyItem(Matchers.in(List.of("Percentages", "Clocks", "Syllogisms")))));
    }

    @Test
    void aiDraftsWaitForReviewAndPicturesAreChecked() throws Exception {
        String drafted = body(send(RECRUITER, "POST", "/api/v1/question-bank/draft",
                "{\"section\":\"QUANT\",\"topic\":\"Bank " + tag + "\",\"difficulty\":\"EASY\",\"count\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.questions[*].status", Matchers.everyItem(Matchers.is("REVIEW")))));
        String id = JsonPath.read(drafted, "$.questions[0].id");
        mockMvc.perform(get("/api/v1/question-bank?topic=Bank " + tag).with(RECRUITER)).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/question-bank?topic=Bank " + tag + "&status=REVIEW").with(RECRUITER)).andExpect(jsonPath("$.total").value(2));
        send(RECRUITER, "POST", "/api/v1/question-bank/" + id + "/approve", "").andExpect(jsonPath("$.status").value("ACTIVE"));

        String png = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0});
        String question = "{\"kind\":\"SINGLE_CHOICE\",\"prompt\":\"Look at the picture\",\"options\":[\"1\",\"2\"],\"correct\":[0],\"points\":1,\"figure\":%s}";
        send(RECRUITER, "POST", "/api/v1/question-bank", "{\"area\":\"APTITUDE\",\"section\":\"LOGICAL\",\"topic\":\"Mine " + tag
                + "\",\"difficulty\":\"MEDIUM\",\"question\":" + question.formatted("\"" + png + "\"") + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.figure").value(png))
                .andExpect(jsonPath("$.source").value("MANUAL"));
        send(RECRUITER, "POST", "/api/v1/question-bank", "{\"area\":\"APTITUDE\",\"section\":\"LOGICAL\",\"topic\":\"Mine " + tag
                + "\",\"difficulty\":\"MEDIUM\",\"question\":" + question.formatted("\"<svg onload='alert(1)'></svg>\"") + "}")
                .andExpect(status().isBadRequest());
        send(RECRUITER, "POST", "/api/v1/question-bank", "{\"area\":\"APTITUDE\",\"section\":\"LOGICAL\",\"topic\":\"Mine " + tag
                + "\",\"difficulty\":\"MEDIUM\",\"question\":" + question.formatted("\"data:image/png;base64,aGVsbG8=\"") + "}")
                .andExpect(status().isBadRequest());

        // Hand-picked bank questions go into a draft test, pictures included.
        String test = JsonPath.read(body(send(RECRUITER, "POST", "/api/v1/assessments",
                "{\"title\":\"Picked " + tag + "\",\"category\":\"APTITUDE\",\"durationMinutes\":20,\"passPercent\":50}")), "$.summary.id");
        String pictureQuestion = JsonPath.<List<String>>read(body(mockMvc.perform(get("/api/v1/question-bank?topic=Clocks").with(RECRUITER))),
                "$.items[*].id").get(0);
        send(RECRUITER, "POST", "/api/v1/assessments/" + test + "/from-bank", "{\"questionIds\":[\"" + pictureQuestion + "\",\"" + id + "\"]}")
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].figure").value(Matchers.startsWith("<svg")))
                .andExpect(jsonPath("$.questions[0].topic").value("Clocks"));
    }
}
