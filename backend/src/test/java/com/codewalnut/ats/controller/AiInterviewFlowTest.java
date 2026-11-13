package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * AI interviews (AINT-01…, ADR-0030): build one for an opening, send it, the candidate records
 * video answers and submits, the review ranks them. Dev profile, so the built-in agent (rules)
 * reviews; fake people and fake "videos" only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiInterviewFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HM = user("hiring.manager@codewalnut.test");
    /** The start of a WebM file (EBML header); enough for the type check. */
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions json(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var builder = "PUT".equals(method) ? put(url) : post(url);
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

    private ResultActions upload(MockHttpSession session, String invite, String question, byte[] data, String transcript) throws Exception {
        return mockMvc.perform(multipart("/api/v1/candidate/tests/" + invite + "/questions/" + question + "/video")
                .file(new MockMultipartFile("file", "answer.webm", "video/webm", data))
                .param("transcript", transcript).param("seconds", "75").session(session).with(csrf()));
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    @Test
    void buildAnInterviewForAnOpening() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Senior Java Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        // AINT-01: only people who manage tests can build one.
        json(HM, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}").andExpect(status().isForbidden());

        String created = body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\",\"durationMinutes\":60}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview.summary.category").value("INTERVIEW"))
                .andExpect(jsonPath("$.interview.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.interview.summary.durationMinutes").value(60)));
        List<String> sections = JsonPath.read(created, "$.interview.questions[*].section");
        // Every phase, in order.
        assertThat(sections.stream().distinct().toList()).containsExactly("INTRO", "EXPERIENCE", "SKILLS", "PROJECTS", "ARCHITECTURE",
                "TECHNICAL", "SOFT_SKILLS", "READINESS");
        List<String> kinds = JsonPath.read(created, "$.interview.questions[*].kind");
        assertThat(kinds).contains("VIDEO", "WRITTEN", "SHORT_ANSWER", "SINGLE_CHOICE");
        List<Integer> seconds = JsonPath.read(created, "$.interview.questions[?(@.kind == 'VIDEO')].maxSeconds");
        assertThat(seconds).allMatch(s -> s >= 60 && s <= 180);
        List<Integer> openPoints = JsonPath.read(created, "$.interview.questions[?(@.kind == 'VIDEO' || @.kind == 'WRITTEN')].points");
        assertThat(openPoints).allMatch(p -> p == 0);
        // A senior Java opening gets the hand-written lead set: code to read, reviewer notes, checked answers.
        assertThat((List<String>) JsonPath.read(created, "$.notes")).anyMatch(n -> n.contains("hand-written senior Java"));
        assertThat((List<String>) JsonPath.read(created, "$.interview.questions[?(@.kind == 'VIDEO' && @.code)].prompt"))
                .anyMatch(p -> p.contains("reviewing this pull request"));
        assertThat((List<String>) JsonPath.read(created, "$.interview.questions[?(@.kind == 'VIDEO')].explanation")).doesNotContainNull();
        assertThat((List<Boolean>) JsonPath.read(created, "$.interview.questions[*].aiDrafted")).containsOnly(false);

        // AINT-11: four hand-written sets to choose from.
        String setC = body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\",\"questionSet\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview.summary.title").value(Matchers.endsWith("Set C"))));
        assertThat((List<String>) JsonPath.read(setC, "$.interview.questions[*].prompt")).anyMatch(p -> p.contains("synchronized, ReentrantLock and ReadWriteLock"));
        json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\",\"questionSet\":5}").andExpect(status().isBadRequest());

        // Preview a set's questions before creating it (managers only).
        mockMvc.perform(get("/api/v1/ai-interviews/sets/2").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letter").value("B"))
                .andExpect(jsonPath("$.questions[0].phase").value("INTRO"))
                .andExpect(jsonPath("$.questions[?(@.kind == 'MULTIPLE_CHOICE')].correctOption").isNotEmpty())
                .andExpect(jsonPath("$.questions[?(@.kind == 'VIDEO')].lookFor").isNotEmpty());
        mockMvc.perform(get("/api/v1/ai-interviews/sets/2").with(HM)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ai-interviews/sets/9").with(RECRUITER)).andExpect(status().isBadRequest());

        // AINT-02: staff add and edit video questions with a phase and a time limit.
        String test = JsonPath.read(created, "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"VIDEO\",\"prompt\":\"Tell us about a production incident you handled.\",\"points\":1,"
                        + "\"section\":\"TECHNICAL\",\"maxSeconds\":150}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[-1].kind").value("VIDEO"))
                .andExpect(jsonPath("$.questions[-1].section").value("TECHNICAL"))
                .andExpect(jsonPath("$.questions[-1].maxSeconds").value(150))
                .andExpect(jsonPath("$.questions[-1].points").value(0));
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/questions",
                "{\"kind\":\"VIDEO\",\"prompt\":\"Bad phase\",\"points\":1,\"section\":\"QUANT\"}")
                .andExpect(status().isBadRequest());
        // Video answers belong to interviews, not the question bank.
        json(RECRUITER, "POST", "/api/v1/question-bank", "{\"area\":\"JAVA\",\"section\":\"ADVANCED\",\"topic\":\"Talk\",\"difficulty\":\"EASY\","
                + "\"question\":{\"kind\":\"VIDEO\",\"prompt\":\"Tell us\",\"points\":1}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidateRecordsAnswersAndTheReviewRanksThem() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Senior Java Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String created = body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}"));
        String test = JsonPath.read(created, "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());

        String email = "meera." + tag + "@gmail.com";
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Meera Sample\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        String other = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Arun Sample\",\"email\":\"arun." + tag + "@gmail.com\",\"stage\":\"SCREENING\"}")), "$.id");
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isCreated())), "$.invite.id");
        json(RECRUITER, "POST", "/api/v1/applications/" + other + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")
                .andExpect(status().isCreated());

        MockHttpSession session = candidateSession(email);
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].kind").value("VIDEO"))
                .andExpect(jsonPath("$.questions[0].maxSeconds").value(60))
                .andExpect(jsonPath("$.questions[0].section").value("INTRO")));
        assertThat(taking).doesNotContain("Idempotency key with a unique index"); // reviewer notes stay with staff
        List<String> videos = JsonPath.read(taking, "$.questions[?(@.kind == 'VIDEO')].id");
        List<String> written = JsonPath.read(taking, "$.questions[?(@.kind == 'WRITTEN' && @.section == 'READINESS')].id");

        // AINT-04: not a video → refused; someone else can't record into this interview.
        upload(session, invite, videos.get(0), "not a video".getBytes(), "").andExpect(status().isBadRequest());
        MockHttpSession stranger = candidateSession("stranger." + tag + "@gmail.com");
        upload(stranger, invite, videos.get(0), WEBM, "hi").andExpect(status().isNotFound());

        String transcript = "I have eight years of Java and Spring Boot experience building microservices on Kafka and MySQL "
                + "for payments, leading a team of five, designing APIs and tuning JVM garbage collection for latency.";
        String first = JsonPath.read(body(upload(session, invite, videos.get(0), WEBM, "draft").andExpect(status().isOk())
                .andExpect(jsonPath("$.seconds").value(60))), "$.videoId"); // capped at the question's limit
        String again = JsonPath.read(body(upload(session, invite, videos.get(0), WEBM, transcript).andExpect(status().isOk())), "$.videoId");
        assertThat(again).isEqualTo(first); // recording again replaces the answer
        for (String q : videos.subList(1, videos.size())) {
            upload(session, invite, q, WEBM, transcript).andExpect(status().isOk());
        }
        // A saved answers request can't overwrite the video id, only the transcript.
        candidate(session, "PUT", "/api/v1/candidate/tests/" + invite + "/answers",
                "{\"answers\":{\"" + videos.get(0) + "\":[\"not-an-id\",\"" + transcript + "\"],\"" + written.get(0)
                        + "\":[\"30 days notice, can join on 1 March\"],\"" + written.get(1) + "\":[\"18 LPA, expecting 25 LPA\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers['" + videos.get(0) + "'][0]").value(first));
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit", "{\"answers\":{}}")
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // AINT-05: reviewed straight away (inline in tests), with a score the app worked out.
        String detail = body(mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invite.evaluation").value("DONE"))
                .andExpect(jsonPath("$.invite.aiScore").isNumber())
                .andExpect(jsonPath("$.review.usesAi").value(false))
                .andExpect(jsonPath("$.review.areas.length()").value(5))
                .andExpect(jsonPath("$.review.noticePeriod").value(Matchers.containsString("30 days")))
                .andExpect(jsonPath("$.review.expectedSalary").value(Matchers.containsString("25 LPA")))
                .andExpect(jsonPath("$.answers[0].given[0]").value(first))
                .andExpect(jsonPath("$.answers[0].given[1]").value(transcript)));
        assertThat((String) JsonPath.read(detail, "$.sections[0].label")).isEqualTo("Introduction");

        // AINT-06: staff play the video; candidates can't use the staff endpoint.
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + first).with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/webm"))
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(WEBM));
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + first).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tests/" + UUID.randomUUID() + "/videos/" + first).with(RECRUITER)).andExpect(status().isNotFound());

        // AINT-07: the ranking puts the reviewed candidate first; the one who hasn't started comes after.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews").with(HM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2))
                .andExpect(jsonPath("$.submitted").value(1))
                .andExpect(jsonPath("$.reviewed").value(1))
                .andExpect(jsonPath("$.candidates[0].candidateName").value("Meera Sample"))
                .andExpect(jsonPath("$.candidates[0].aiScore").isNumber())
                .andExpect(jsonPath("$.candidates[0].areas.TECHNICAL").isNumber())
                .andExpect(jsonPath("$.candidates[0].videos").value(videos.size()))
                .andExpect(jsonPath("$.candidates[1].candidateName").value("Arun Sample"))
                .andExpect(jsonPath("$.candidates[1].status").value("SENT"));
        // AI is advisory: nobody moved.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$[*].stage", Matchers.everyItem(Matchers.not("SHORTLISTED"))));

        // Review again: managers only, submitted interviews only.
        json(HM, "POST", "/api/v1/tests/" + invite + "/review-again", "").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/review-again", "").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER)).andExpect(jsonPath("$.invite.evaluation").value("DONE"));

        // AINT-10: let them finish — reopened with saved answers kept, reviewed again on submit.
        json(HM, "POST", "/api/v1/tests/" + invite + "/reopen?minutes=30", "").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/reopen?minutes=5", "").andExpect(status().isBadRequest());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/reopen?minutes=30", "").andExpect(status().isNoContent());
        candidate(session, "GET", "/api/v1/candidate/tests/" + invite, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.test.status").value("STARTED"))
                .andExpect(jsonPath("$.secondsLeft").value(Matchers.greaterThan(1700)))
                .andExpect(jsonPath("$.answers['" + videos.get(0) + "'][0]").value(first));
        upload(session, invite, videos.get(1), WEBM, transcript).andExpect(status().isOk());
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit", "{\"answers\":{}}")
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.invite.evaluation").value("DONE"))
                .andExpect(jsonPath("$.answers[0].given[0]").value(first));
        json(RECRUITER, "POST", "/api/v1/tests/" + UUID.randomUUID() + "/reopen", "").andExpect(status().isNotFound());
    }

    @Test
    void uploadsOnlyWhileTheInterviewIsOpen() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Python Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}")),
                "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        String email = "dev." + tag + "@gmail.com";
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Dev Sample\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        MockHttpSession session = candidateSession(email);
        String detail = body(mockMvc.perform(get("/api/v1/assessments/" + test).with(RECRUITER)));
        String video = ((List<String>) JsonPath.read(detail, "$.questions[?(@.kind == 'VIDEO')].id")).get(0);
        String mcq = ((List<String>) JsonPath.read(detail, "$.questions[?(@.kind != 'VIDEO')].id")).get(0);
        // Not started yet.
        upload(session, invite, video, WEBM, "x").andExpect(status().isBadRequest());
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "").andExpect(status().isOk());
        // Only video questions take recordings.
        upload(session, invite, mcq, WEBM, "x").andExpect(status().isNotFound());
        upload(session, invite, video, WEBM, "x").andExpect(status().isOk());
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit", "{\"answers\":{}}").andExpect(status().isOk());
        upload(session, invite, video, WEBM, "x").andExpect(status().isBadRequest());
    }
}
