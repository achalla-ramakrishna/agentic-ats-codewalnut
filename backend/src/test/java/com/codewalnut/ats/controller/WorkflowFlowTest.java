package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The workflow view (WF-01…WF-06): who was contacted, who is waiting on us, what's next. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class WorkflowFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions send(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Map<String, Object> row(String board, String applicationId) {
        List<Map<String, Object>> rows = JsonPath.read(board, "$.rows[?(@.applicationId == '" + applicationId + "')]");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    void showsWhoWasContactedWhoIsWaitingAndWhatToDoNext() throws Exception {
        String job = id(send(ADMIN, "/api/v1/jobs", "{\"title\":\"Workflow " + tag + "\",\"hiringType\":\"INTERNAL\"}"));
        String never = id(send(ADMIN, "/api/v1/jobs/" + job + "/applications", "{\"name\":\"Nina " + tag + "\",\"stage\":\"SOURCED\"}"));
        String mailed = id(send(ADMIN, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Mohan " + tag + "\",\"email\":\"mohan." + tag + "@example.test\",\"stage\":\"SCREENING\"}"));
        String replied = id(send(ADMIN, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Rekha " + tag + "\",\"email\":\"rekha." + tag + "@gmail.com\",\"stage\":\"SCREENING\"}"));

        send(ADMIN, "/api/v1/applications/" + mailed + "/messages",
                "{\"channel\":\"CANDIDATE\",\"body\":\"Are you open to a Java role?\",\"sendEmail\":true}").andExpect(status().isCreated());
        send(ADMIN, "/api/v1/applications/" + replied + "/messages",
                "{\"channel\":\"CANDIDATE\",\"body\":\"What is your notice period?\",\"sendEmail\":false}").andExpect(status().isCreated());
        MockHttpSession rekha = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"rekha." + tag + "@gmail.com\"}"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(post("/api/v1/candidate/applications/" + replied + "/messages").session(rekha).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"30 days\"}"))
                .andExpect(status().isCreated());

        // A call made outside the app is logged.
        send(ADMIN, "/api/v1/applications/" + mailed + "/contacts", "{\"how\":\"CALL\",\"note\":\"Interested, call back Monday\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("CONTACT"))
                .andExpect(jsonPath("$.items[0].title").value("Call (logged)"))
                .andExpect(jsonPath("$.items[0].detail").value("Interested, call back Monday"));
        send(ADMIN, "/api/v1/applications/" + mailed + "/contacts", "{\"how\":\"PIGEON\"}").andExpect(status().isBadRequest());

        String board = mockMvc.perform(get("/api/v1/workflow").param("jobId", job).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.counts.never").value(1))
                .andExpect(jsonPath("$.counts.reply").value(1))
                .andExpect(jsonPath("$.canLog").value(true))
                // Someone waiting on us comes first.
                .andExpect(jsonPath("$.rows[0].applicationId").value(replied))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> nina = row(board, never);
        assertThat(nina.get("contacts")).isEqualTo(0);
        assertThat(nina.get("hasPhone")).isEqualTo(false);
        assertThat(nina.get("lastContact")).isNull();
        assertThat(JsonPath.<String>read(nina, "$.nextStep.code")).isEqualTo("FIRST_CONTACT");

        Map<String, Object> mohan = row(board, mailed);
        assertThat(mohan.get("contacts")).isEqualTo(2);
        assertThat(JsonPath.<String>read(mohan, "$.lastContact.how")).isEqualTo("Call");
        assertThat(JsonPath.<String>read(mohan, "$.lastContact.by")).isEqualTo("admin@codewalnut.test");
        assertThat(JsonPath.<String>read(mohan, "$.nextStep.code")).isEqualTo("TEST_OR_INTERVIEW");

        Map<String, Object> rekhaRow = row(board, replied);
        assertThat(rekhaRow.get("awaitingReply")).isEqualTo(true);
        assertThat(JsonPath.<String>read(rekhaRow, "$.nextStep.code")).isEqualTo("REPLY");
        assertThat(JsonPath.<Boolean>read(rekhaRow, "$.nextStep.urgent")).isTrue();

        // All open openings by default.
        mockMvc.perform(get("/api/v1/workflow").with(ADMIN))
                .andExpect(jsonPath("$.rows[?(@.applicationId == '" + never + "')]").isNotEmpty());

        // The timeline has both sides of the conversation, newest first.
        mockMvc.perform(get("/api/v1/applications/" + replied + "/timeline").with(ADMIN))
                .andExpect(jsonPath("$.items[0].kind").value("MESSAGE_IN"))
                .andExpect(jsonPath("$.items[0].detail").value("30 days"))
                .andExpect(jsonPath("$.items[1].title").value("Message on their candidate page"))
                .andExpect(jsonPath("$.items[?(@.kind == 'ADDED')]").isNotEmpty());

        // Replying clears "waiting for our reply".
        send(ADMIN, "/api/v1/applications/" + replied + "/messages",
                "{\"channel\":\"CANDIDATE\",\"body\":\"Thanks, noted.\",\"sendEmail\":false}").andExpect(status().isCreated());
        String after = mockMvc.perform(get("/api/v1/workflow").param("jobId", job).with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        assertThat(row(after, replied).get("awaitingReply")).isEqualTo(false);
    }

    @Test
    void onlyPeopleWhoSeeCandidatesGetIt() throws Exception {
        mockMvc.perform(get("/api/v1/workflow").with(INTERVIEWER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workflow")).andExpect(status().isUnauthorized());
    }
}
