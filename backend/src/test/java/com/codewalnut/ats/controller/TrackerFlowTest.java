package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The simple hiring tracker end to end: client → opening → import → stage moves → dashboard. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class TrackerFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);
    /** Unique per run: tests share a database, and phones are used to spot existing candidates. */
    private final String phone = randomPhone();
    private final String ashaPhone = randomPhone();
    private final String ravisPhone = randomPhone();

    private static String randomPhone() {
        return "9" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private String send(RequestPostProcessor who, String url, Object body, int expected) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private String clientDeployedOpening() throws Exception {
        String clientId = JsonPath.read(send(ADMIN, "/api/v1/clients", Map.of("name", "Blendish " + tag), 201), "$.id");
        return JsonPath.read(send(ADMIN, "/api/v1/jobs", Map.of(
                "title", "Interns " + tag, "clientId", clientId, "hiringType", "CLIENT_DEPLOYED", "openings", 20), 201), "$.id");
    }

    private String paste() {
        return String.join("\n",
                "slno\tname\temail\tphone",
                "1\tASHA " + tag + "\tasha." + tag + "@gmail.com\t" + ashaPhone,
                "2\tKAVYA " + tag + "\tKavya KS\t",
                "3\tRAVI " + tag + "\travi." + tag + "@gmail.com|\t" + ravisPhone,
                "4\tRAVI AGAIN\travi." + tag + "@gmail.com\t" + ravisPhone,
                "5\t\tnobody." + tag + "@gmail.com\t" + phone);
    }

    @Test
    void previewThenImportThenMoveThroughStages() throws Exception {
        String jobId = clientDeployedOpening();

        String preview = send(ADMIN, "/api/v1/jobs/" + jobId + "/applications/import",
                Map.of("text", paste(), "stage", "INTERVIEWED", "dryRun", true), 200);
        assertThat((Integer) JsonPath.read(preview, "$.added")).isEqualTo(3);
        assertThat((List<String>) JsonPath.read(preview, "$.rows[*].outcome"))
                .containsExactly("NEW", "NEW", "NEW", "DUPLICATE_IN_PASTE", "ERROR");
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(0));

        String result = send(ADMIN, "/api/v1/jobs/" + jobId + "/applications/import",
                Map.of("text", paste(), "stage", "INTERVIEWED", "dryRun", false), 200);
        assertThat((Integer) JsonPath.read(result, "$.added")).isEqualTo(3);

        String list = mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].stage").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("INTERVIEWED"))))
                .andReturn().getResponse().getContentAsString();
        String appId = JsonPath.read(list, "$[0].id");

        // Re-importing the same paste adds nobody.
        String again = send(ADMIN, "/api/v1/jobs/" + jobId + "/applications/import",
                Map.of("text", paste(), "stage", "INTERVIEWED", "dryRun", false), 200);
        assertThat((Integer) JsonPath.read(again, "$.added")).isZero();

        mockMvc.perform(patch("/api/v1/applications/" + appId + "/stage").with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SUBMITTED_TO_CLIENT\",\"note\":\"Sent to Blend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED_TO_CLIENT"))
                .andExpect(jsonPath("$.lastNote").value("Sent to Blend"));

        send(RECRUITER, "/api/v1/applications/" + appId + "/notes", Map.of("text", "Client interview Monday"), 201);

        mockMvc.perform(get("/api/v1/applications/" + appId + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].type").value("NOTE"))
                .andExpect(jsonPath("$[1].type").value("STAGE_CHANGED"))
                .andExpect(jsonPath("$[1].fromStage").value("INTERVIEWED"))
                .andExpect(jsonPath("$[1].actorEmail").value("recruiter@codewalnut.test"))
                .andExpect(jsonPath("$[2].type").value("CREATED"));

        mockMvc.perform(get("/api/v1/jobs/" + jobId).with(ADMIN))
                .andExpect(jsonPath("$.stageCounts.INTERVIEWED").value(2))
                .andExpect(jsonPath("$.stageCounts.SUBMITTED_TO_CLIENT").value(1))
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/api/v1/dashboard").with(ADMIN))
                .andExpect(jsonPath("$.openJobs[?(@.id == '" + jobId + "')]").exists())
                .andExpect(jsonPath("$.recentActivity[0].note").value("Client interview Monday"));
    }

    @Test
    void rejectingNeedsAReason() throws Exception {
        String jobId = clientDeployedOpening();
        String appId = JsonPath.read(send(ADMIN, "/api/v1/jobs/" + jobId + "/applications",
                Map.of("name", "Om " + tag, "email", "om." + tag + "@gmail.com", "stage", "INTERVIEWED"), 201), "$.id");

        mockMvc.perform(patch("/api/v1/applications/" + appId + "/stage").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/applications/" + appId + "/stage").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"REJECTED\",\"note\":\"Not available until June\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void addingTheSamePersonTwiceToAnOpeningIsRefused() throws Exception {
        String jobId = clientDeployedOpening();
        Map<String, String> body = Map.of("name", "Om " + tag, "email", "dup." + tag + "@gmail.com", "stage", "SOURCED");
        send(ADMIN, "/api/v1/jobs/" + jobId + "/applications", body, 201);
        send(ADMIN, "/api/v1/jobs/" + jobId + "/applications", body, 409);
    }

    @Test
    void clientOpeningNeedsAClient() throws Exception {
        send(ADMIN, "/api/v1/jobs", Map.of("title", "No client", "hiringType", "CLIENT_DEPLOYED"), 400);
    }

    @Test
    void permissions() throws Exception {
        String jobId = clientDeployedOpening();
        // Interviewers can't see openings or candidates.
        mockMvc.perform(get("/api/v1/jobs").with(INTERVIEWER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications").with(INTERVIEWER)).andExpect(status().isForbidden());
        // Hiring managers can look but not change.
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications").with(HIRING_MANAGER)).andExpect(status().isOk());
        send(HIRING_MANAGER, "/api/v1/jobs/" + jobId + "/applications",
                Map.of("name", "X", "stage", "SOURCED"), 403);
        // Interviewer dashboard is empty rather than an error.
        mockMvc.perform(get("/api/v1/dashboard").with(INTERVIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openJobs.length()").value(0));
    }
}
