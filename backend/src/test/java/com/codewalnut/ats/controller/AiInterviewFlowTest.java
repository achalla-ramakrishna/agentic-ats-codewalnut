package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final RequestPostProcessor REVIEWER = user("reviewer@codewalnut.test");
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

    private ResultActions reveal(MockHttpSession session, String invite, String question) throws Exception {
        return mockMvc.perform(post("/api/v1/candidate/tests/" + invite + "/questions/" + question + "/reveal").session(session).with(csrf()));
    }

    /** Opens the question first, as the page does (AINT-24), then uploads. */
    private ResultActions upload(MockHttpSession session, String invite, String question, byte[] data, String transcript) throws Exception {
        reveal(session, invite, question);
        return record(session, invite, question, data, transcript);
    }

    private ResultActions record(MockHttpSession session, String invite, String question, byte[] data, String transcript) throws Exception {
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
                .andExpect(jsonPath("$.interview.summary.durationMinutes").value(60))
                .andExpect(jsonPath("$.interview.summary.jobId").value(job))); // sending starts at this opening
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

        // AINT-21: an AI interview reviewer sees the finished interview (not the one never started), watches the
        // answers and says whether the candidate goes to the next round. Recruiters can't give verdicts.
        mockMvc.perform(get("/api/v1/ai-reviews").with(REVIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviews[?(@.inviteId == '" + invite + "')].candidateName").value("Meera Sample"))
                .andExpect(jsonPath("$.interviews[?(@.candidateName == 'Arun Sample')]").isEmpty());
        mockMvc.perform(get("/api/v1/ai-reviews").with(RECRUITER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ai-reviews/" + invite).with(REVIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job))
                .andExpect(jsonPath("$.detail.answers[0].given[0]").value(first))
                .andExpect(jsonPath("$.detail.review.areas.length()").value(5))
                .andExpect(jsonPath("$.verdicts").isEmpty());
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + first).with(REVIEWER)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tests/" + invite).with(REVIEWER)).andExpect(status().isForbidden()); // nothing else about the candidate
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(REVIEWER)).andExpect(status().isForbidden());
        // Not selecting needs a reason.
        json(REVIEWER, "PUT", "/api/v1/ai-reviews/" + invite, "{\"decision\":\"HOLD\"}").andExpect(status().isBadRequest());
        json(REVIEWER, "PUT", "/api/v1/ai-reviews/" + invite, "{\"decision\":\"MAYBE\"}").andExpect(status().isBadRequest());
        json(REVIEWER, "PUT", "/api/v1/ai-reviews/" + invite,
                "{\"decision\":\"HOLD\",\"feedback\":\"Good design answers, thin on testing.\",\"marks\":[{\"position\":1,\"mark\":\"GOOD\",\"note\":\"Clear\"},"
                        + "{\"position\":2,\"mark\":null,\"note\":\" \"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdicts[0].decision").value("HOLD"))
                .andExpect(jsonPath("$.verdicts[0].mine").value(true))
                .andExpect(jsonPath("$.verdicts[0].marks.length()").value(1));
        json(REVIEWER, "PUT", "/api/v1/ai-reviews/" + invite, "{\"decision\":\"SELECT\",\"feedback\":\"Watched again: strong.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdicts.length()").value(1))
                .andExpect(jsonPath("$.verdicts[0].decisionLabel").value("Select for next round"));
        // A hiring manager can give one too; the ranking shows both; the team sees a note; nobody moved.
        json(HM, "PUT", "/api/v1/ai-reviews/" + invite, "{\"decision\":\"SELECT\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews").with(RECRUITER))
                .andExpect(jsonPath("$.candidates[0].verdicts.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].verdicts[0].decision").value("SELECT"));
        mockMvc.perform(get("/api/v1/ai-reviews").with(REVIEWER))
                .andExpect(jsonPath("$.interviews[?(@.inviteId == '" + invite + "')].mine").value("SELECT"));
        mockMvc.perform(get("/api/v1/applications/" + app + "/messages?channel=TEAM").with(RECRUITER))
                .andExpect(jsonPath("$[*].body", Matchers.hasItem(Matchers.containsString("reviewed the AI interview: Select for next round (was: Maybe"))));
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

        // AINT-19: room for videos, visible to anyone who sees candidates.
        mockMvc.perform(get("/api/v1/ai-interviews/storage").with(HM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.interviews").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.freeBytes").isNumber())
                .andExpect(jsonPath("$.roomFor").isNumber())
                .andExpect(jsonPath("$.temporary").value(false));

        // AINT-20: delete a rejected candidate's videos; transcripts and the review stay.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews/closed-videos").with(RECRUITER))
                .andExpect(jsonPath("$.videos").value(0));
        mockMvc.perform(patch("/api/v1/applications/" + app + "/stage").with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"REJECTED\",\"note\":\"Not a fit\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews/closed-videos").with(RECRUITER))
                .andExpect(jsonPath("$.candidates").value(1))
                .andExpect(jsonPath("$.videos").value(videos.size()))
                .andExpect(jsonPath("$.bytes").value(Matchers.greaterThan(0)));
        mockMvc.perform(delete("/api/v1/jobs/" + job + "/ai-interviews/closed-videos").with(HM).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/jobs/" + job + "/ai-interviews/closed-videos").with(RECRUITER).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos").value(videos.size()));
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + first).with(RECRUITER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.answers[0].given[0]").value(""))
                .andExpect(jsonPath("$.answers[0].given[1]").value(transcript))
                .andExpect(jsonPath("$.review.areas.length()").value(5));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews/closed-videos").with(RECRUITER))
                .andExpect(jsonPath("$.videos").value(0));
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

        // AINT-23: delete the AI interview (answers, videos, verdicts); the candidate stays in the opening.
        String videoId = JsonPath.read(body(mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))), "$.answers[0].given[0]");
        json(HM, "PUT", "/api/v1/ai-reviews/" + invite, "{\"decision\":\"SELECT\"}").andExpect(status().isOk());
        json(HM, "POST", "/api/v1/ai-interviews/delete", "{\"inviteIds\":[\"" + invite + "\"]}").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/ai-interviews/delete", "{\"inviteIds\":[]}").andExpect(status().isBadRequest());
        json(RECRUITER, "POST", "/api/v1/ai-interviews/delete", "{\"inviteIds\":[\"" + UUID.randomUUID() + "\"]}").andExpect(status().isNotFound());
        json(RECRUITER, "POST", "/api/v1/ai-interviews/delete", "{\"inviteIds\":[\"" + invite + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviews").value(1))
                .andExpect(jsonPath("$.videos").value(1));
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + videoId).with(RECRUITER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews").with(RECRUITER)).andExpect(jsonPath("$.sent").value(0));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER)).andExpect(jsonPath("$[0].name").value("Dev Sample"));
    }
    @Test
    void anArchivedInterviewCanBeDeletedForGoodWithItsAnswersAndVideos() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Python Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}")),
                "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        String email = "old." + tag + "@gmail.com";
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Old Sample\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        MockHttpSession session = candidateSession(email);
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", ""));
        String video = ((List<String>) JsonPath.read(taking, "$.questions[?(@.kind == 'VIDEO')].id")).get(0);
        String videoId = JsonPath.read(body(upload(session, invite, video, WEBM, "hello")), "$.videoId");
        candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/submit", "{\"answers\":{}}").andExpect(status().isOk());

        // ASMT-41: taken → can't delete; for good only once archived; then answers and videos go too.
        mockMvc.perform(delete("/api/v1/assessments/" + test).with(RECRUITER).with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/assessments/" + test + "?forGood=true").with(RECRUITER).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("Archive this test first")));
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/archive", "").andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/assessments/" + test + "?forGood=true").with(HM).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/assessments/" + test + "?forGood=true").with(RECRUITER).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(1))
                .andExpect(jsonPath("$.videos").value(1))
                .andExpect(jsonPath("$.bytes").value(WEBM.length));
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tests/" + invite + "/videos/" + videoId).with(RECRUITER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER)).andExpect(jsonPath("$[0].name").value("Old Sample"));
    }
    @Test
    void moreTimeForCandidatesWhoHaventTakenIt() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Java Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}")),
                "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        String[] invites = new String[2];
        for (int i = 0; i < 2; i++) {
            String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                    "{\"name\":\"Late Sample " + i + "\",\"email\":\"late" + i + "." + tag + "@gmail.com\",\"stage\":\"SCREENING\"}")), "$.id");
            invites[i] = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                    "{\"assessmentId\":\"" + test + "\",\"dueDays\":1,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        }
        // The second one starts, so it isn't extended.
        candidate(candidateSession("late1." + tag + "@gmail.com"), "POST", "/api/v1/candidate/tests/" + invites[1] + "/start", "")
                .andExpect(status().isOk());

        // ASMT-42: managers only; 1–60 days; open till the end of that day.
        json(HM, "POST", "/api/v1/tests/extend", "{\"inviteIds\":[\"" + invites[0] + "\"],\"days\":15}").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/tests/extend", "{\"inviteIds\":[\"" + invites[0] + "\"],\"days\":0}").andExpect(status().isBadRequest());
        String r = body(json(RECRUITER, "POST", "/api/v1/tests/extend",
                "{\"inviteIds\":[\"" + invites[0] + "\",\"" + invites[1] + "\"],\"days\":15}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extended").value(1))
                .andExpect(jsonPath("$.skipped[0]").value("Late Sample 1")));
        java.time.Instant due = java.time.Instant.parse(JsonPath.read(r, "$.dueAt"));
        java.time.ZoneId india = java.time.ZoneId.of("Asia/Kolkata");
        assertThat(due.atZone(india).toLocalDate()).isEqualTo(java.time.LocalDate.now(india).plusDays(15));
        // AINT-25: review the opening again with the AI — managers only; refused when no AI key is set (as in tests).
        json(HM, "POST", "/api/v1/jobs/" + job + "/ai-interviews/review-again", "").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/ai-interviews/review-again", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("ANTHROPIC_API_KEY")));
        mockMvc.perform(get("/api/v1/tests/" + invites[0]).with(RECRUITER))
                .andExpect(jsonPath("$.invite.status").value("SENT"))
                .andExpect(jsonPath("$.invite.dueAt").value(JsonPath.<String>read(r, "$.dueAt")));
    }

    @Autowired
    private com.codewalnut.ats.repository.InterviewAnswerCheckRepository checkRepository;

    @Autowired
    private com.codewalnut.ats.service.InterviewAnswerChecks answerChecks;

    /** AINT-24: hidden questions, thinking time, one retake, signals for staff, the reading alert and ending early. */
    @Test
    void spokenQuestionsStayHiddenUntilOpenedAndReadingIsCaughtWhileRunning() throws Exception {
        String job = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs",
                "{\"title\":\"Python Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")), "$.id");
        String test = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/ai-interviews", "{\"jobId\":\"" + job + "\"}")),
                "$.interview.summary.id");
        json(RECRUITER, "POST", "/api/v1/assessments/" + test + "/publish", "").andExpect(status().isOk());
        String email = "reader." + tag + "@gmail.com";
        String app = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Reader Sample\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}")), "$.id");
        String invite = JsonPath.read(body(json(RECRUITER, "POST", "/api/v1/applications/" + app + "/tests",
                "{\"assessmentId\":\"" + test + "\",\"dueDays\":3,\"sendEmail\":false,\"sendWhatsApp\":false}")), "$.invite.id");
        MockHttpSession session = candidateSession(email);
        String detail = body(mockMvc.perform(get("/api/v1/assessments/" + test).with(RECRUITER)));
        String video = ((List<String>) JsonPath.read(detail, "$.questions[?(@.kind == 'VIDEO')].id")).get(0);
        String prompt = ((List<String>) JsonPath.read(detail, "$.questions[?(@.kind == 'VIDEO')].prompt")).get(0);

        // The spoken question's text isn't sent until it is opened; the thinking time comes with the test.
        String taking = body(candidate(session, "POST", "/api/v1/candidate/tests/" + invite + "/start", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thinkSeconds").value(30))
                .andExpect(jsonPath("$.questions[0].hidden").value(true))
                .andExpect(jsonPath("$.questions[0].prompt").value(""))
                .andExpect(jsonPath("$.questions[0].takesLeft").value(2)));
        assertThat(taking).doesNotContain(prompt);
        // Recording without opening the question is refused.
        record(session, invite, video, WEBM, "x").andExpect(status().isBadRequest());
        reveal(session, invite, video)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value(prompt))
                .andExpect(jsonPath("$.thinkSecondsLeft").value(Matchers.greaterThan(25)))
                .andExpect(jsonPath("$.takesLeft").value(2))
                .andExpect(jsonPath("$.warning").doesNotExist());
        String revealedAt = JsonPath.read(body(reveal(session, invite, video)), "$.revealedAt");
        candidate(session, "GET", "/api/v1/candidate/tests/" + invite, "")
                .andExpect(jsonPath("$.questions[0].hidden").value(false))
                .andExpect(jsonPath("$.questions[0].prompt").value(prompt))
                .andExpect(jsonPath("$.questions[0].revealedAt").value(revealedAt)); // opening again doesn't restart the clock
        reveal(stranger(), invite, video).andExpect(status().isNotFound());

        String read = "Firstly, dependency injection is a design pattern that plays a crucial role in building robust and scalable "
                + "applications. Secondly, it ensures loose coupling. Additionally, it improves testability. In conclusion, it is essential.";
        mockMvc.perform(multipart("/api/v1/candidate/tests/" + invite + "/questions/" + video + "/video")
                        .file(new MockMultipartFile("file", "answer.webm", "video/webm", WEBM))
                        .param("transcript", read).param("seconds", "50").param("focusLosses", "2").session(session).with(csrf()))
                .andExpect(status().isOk());
        record(session, invite, video, WEBM, read).andExpect(status().isOk()); // one retake
        record(session, invite, video, WEBM, read).andExpect(status().isBadRequest()); // no more

        // Staff see the answer and its signals while the interview is still running.
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.invite.status").value("STARTED"))
                .andExpect(jsonPath("$.answers[0].signals.takes").value(2))
                .andExpect(jsonPath("$.answers[0].signals.focusLosses").value(2))
                .andExpect(jsonPath("$.answers[0].signals.thinkSeconds").isNumber())
                .andExpect(jsonPath("$.answers[0].signals.reading").value("SKIPPED")); // no AI in tests

        // The agent says it sounds read out: the team is told at once, the candidate is warned before the next question.
        var check = checkRepository.findByInviteId(UUID.fromString(invite)).get(0);
        check.setReading(com.codewalnut.ats.domain.InterviewAnswerCheck.Reading.PENDING);
        checkRepository.save(check);
        answerChecks.recordCheck(check.getId(), new com.codewalnut.ats.client.InterviewAgent.ReadCheck("LIKELY",
                "Generated shape: 'Firstly… Secondly… In conclusion' and no project of their own."));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/ai-interviews").with(RECRUITER))
                .andExpect(jsonPath("$.candidates[0].soundsRead").value(1))
                .andExpect(jsonPath("$.candidates[0].readingAlertedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.answers[0].signals.reading").value("LIKELY"))
                .andExpect(jsonPath("$.answers[0].signals.readingReason").value(Matchers.containsString("In conclusion")));
        mockMvc.perform(get("/api/v1/applications/" + app + "/messages?channel=TEAM").with(RECRUITER))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("may be reading"));
        String next = ((List<String>) JsonPath.read(detail, "$.questions[?(@.kind == 'VIDEO')].id")).get(1);
        reveal(session, invite, next).andExpect(jsonPath("$.warning").value(Matchers.containsString("own words")));

        // A person ends it early (recruiters only); it is submitted and reviewed as usual, and the candidate can't go on.
        json(HM, "POST", "/api/v1/tests/" + invite + "/end", "{}").andExpect(status().isForbidden());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/end", "{\"reason\":\"Reading from another screen\"}")
                .andExpect(status().isNoContent());
        json(RECRUITER, "POST", "/api/v1/tests/" + invite + "/end", "{}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tests/" + invite).with(RECRUITER))
                .andExpect(jsonPath("$.invite.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.invite.evaluation").value("DONE"));
        record(session, invite, next, WEBM, "x").andExpect(status().isBadRequest());
        reveal(session, invite, next).andExpect(status().isBadRequest());
    }

    private MockHttpSession stranger() throws Exception {
        return candidateSession("someone." + tag + "@gmail.com");
    }
}
