package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** INT-28…INT-32: interview kits from the job description; staff only. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class InterviewKitFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final String JD = "Java backend developer, 2+ years.\\nMust have: Java, Spring Boot, MySQL.\\nNice to have: Docker.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewRepository interviewRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions send(RequestPostProcessor who, String method, String url, String body) throws Exception {
        var r = switch (method) {
            case "PATCH" -> patch(url);
            case "PUT" -> put(url);
            default -> post(url);
        };
        return mockMvc.perform(r.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String job() throws Exception {
        String job = JsonPath.read(send(RECRUITER, "POST", "/api/v1/jobs", "{\"title\":\"Java Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(RECRUITER, "PATCH", "/api/v1/jobs/" + job, "{\"description\":\"" + JD + "\"}").andExpect(status().isOk());
        return job;
    }

    @Test
    void recruiterGeneratesAKitAndThePanelUsesIt() throws Exception {
        String job = job();
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kit").doesNotExist())
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.hasDescription").value(true))
                .andExpect(jsonPath("$.roles.length()").value(Matchers.greaterThan(10)));

        send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kit.kit.level").value("JUNIOR"))
                .andExpect(jsonPath("$.kit.kit.roleId").value("java-backend"))
                .andExpect(jsonPath("$.kit.kit.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.kit.kit.skills[?(@.name == 'Docker')].mustHave").value(Matchers.contains(false)))
                .andExpect(jsonPath("$.kit.kit.test.preset.sections.length()").value(Matchers.greaterThan(2)))
                .andExpect(jsonPath("$.kit.kit.rounds[2].name").value("Live coding"))
                .andExpect(jsonPath("$.kit.kit.rounds[2].coding.length()").value(2))
                .andExpect(jsonPath("$.kit.outdated").value(false))
                .andExpect(jsonPath("$.kit.generatedBy").value("recruiter@codewalnut.test"));

        // Overrides: a senior kit adds system design.
        send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{\"level\":\"SENIOR\"}")
                .andExpect(jsonPath("$.kit.kit.level").value("SENIOR"))
                .andExpect(jsonPath("$.kit.kit.detectedLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.kit.kit.rounds[*].name").value(Matchers.hasItem("System design")));
        send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{\"level\":\"WIZARD\"}").andExpect(status().isBadRequest());

        // Changing the job description marks the kit as out of date.
        send(RECRUITER, "PATCH", "/api/v1/jobs/" + job, "{\"description\":\"Python developer\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").with(HIRING_MANAGER))
                .andExpect(jsonPath("$.kit.outdated").value(true))
                .andExpect(jsonPath("$.canGenerate").value(false));
        send(HIRING_MANAGER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{}").andExpect(status().isForbidden());

        // An interviewer sees the kit only once they're on an interview for this opening.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").with(INTERVIEWER)).andExpect(status().isNotFound());
        String app = JsonPath.read(send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Asha Rao\",\"stage\":\"SCREENING\",\"email\":\"asha." + tag + "@example.com\"}").andReturn().getResponse()
                .getContentAsString(), "$.id");
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String interview = JsonPath.read(send(ADMIN, "POST", "/api/v1/applications/" + app + "/interviews",
                "{\"title\":\"CodeWalnut interview\",\"startAt\":\"" + start + "\",\"durationMinutes\":45,\"timeZone\":\"Asia/Kolkata\","
                        + "\"interviewerEmails\":[\"interviewer@codewalnut.test\"]}").andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").with(INTERVIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.kit.kit.rounds.length()").value(Matchers.greaterThan(3)));
        send(INTERVIEWER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{}").andExpect(status().isForbidden());

        // The feedback form adds the job's must-haves to the scorecard.
        Interview i = interviewRepository.findById(UUID.fromString(interview)).orElseThrow();
        i.setStartAt(Instant.now().minus(Duration.ofHours(1)));
        i.setEndAt(Instant.now().minus(Duration.ofMinutes(15)));
        interviewRepository.save(i);
        mockMvc.perform(get("/api/v1/interviews/" + interview + "/feedback").with(INTERVIEWER))
                .andExpect(jsonPath("$.competencies[0].name").value("Problem solving"))
                .andExpect(jsonPath("$.competencies[*].name").value(Matchers.hasItems("Java", "Spring Boot")));
    }

    @Test
    void candidatesClientsAndApproversCannotSeeKits() throws Exception {
        String job = job();
        send(RECRUITER, "POST", "/api/v1/jobs/" + job + "/interview-kit", "{}").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").with(user("approver@codewalnut.test"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit")).andExpect(status().is4xxClientError());
        MockHttpSession candidate = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"cand." + tag + "@gmail.com\"}"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/jobs/" + job + "/interview-kit").session(candidate)).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID() + "/interview-kit").with(RECRUITER)).andExpect(status().isNotFound());
    }
}
