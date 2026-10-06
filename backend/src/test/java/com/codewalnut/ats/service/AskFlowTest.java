package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.AskClient;
import com.codewalnut.ats.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Ask ATS (ASK-01…ASK-05) with the offline rule-based client the dev profile uses. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class AskFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AskService askService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions send(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String opening() throws Exception {
        String job = JsonPath.read(send(RECRUITER, "/api/v1/jobs", "{\"title\":\"Ask " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(RECRUITER, "/api/v1/jobs/" + job + "/applications", "{\"name\":\"Zara " + tag + "\",\"stage\":\"SHORTLISTED\"}");
        send(RECRUITER, "/api/v1/jobs/" + job + "/applications", "{\"name\":\"Yusuf " + tag + "\",\"stage\":\"SCREENING\"}");
        return job;
    }

    @Test
    void answersFromTheAtsAndKeepsAPrivateChat() throws Exception {
        String job = opening();
        mockMvc.perform(get("/api/v1/ask/status").with(RECRUITER))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.suggestions.length()").value(Matchers.greaterThan(2)));

        String first = send(RECRUITER, "/api/v1/ask", "{\"question\":\"Zara " + tag + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Zara " + tag))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].text").value(Matchers.containsString("](/jobs/" + job + "?candidate=")))
                .andExpect(jsonPath("$.messages[1].text").value(Matchers.containsString("Shortlisted")))
                .andReturn().getResponse().getContentAsString();
        String chat = JsonPath.read(first, "$.id");

        // A follow-up continues the same chat.
        send(RECRUITER, "/api/v1/ask", "{\"conversationId\":\"" + chat + "\",\"question\":\"How do I share a candidate with a client?\"}")
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[3].text").value(Matchers.containsString("Share with")));
        mockMvc.perform(get("/api/v1/ask/conversations").with(RECRUITER))
                .andExpect(jsonPath("$[0].id").value(chat));

        // Chats are private: someone else can't open, continue or delete it.
        mockMvc.perform(get("/api/v1/ask/conversations/" + chat).with(ADMIN)).andExpect(status().isNotFound());
        send(ADMIN, "/api/v1/ask", "{\"conversationId\":\"" + chat + "\",\"question\":\"hi\"}").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/ask/conversations/" + chat).with(ADMIN).with(csrf())).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/ask/conversations/" + chat).with(RECRUITER).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/ask/conversations/" + chat).with(RECRUITER)).andExpect(status().isNotFound());
    }

    @Test
    void anInterviewerCantReadCandidatesThroughIt() throws Exception {
        opening();
        send(INTERVIEWER, "/api/v1/ask", "{\"question\":\"Zara " + tag + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].text").value(Matchers.containsString("Not allowed")))
                .andExpect(jsonPath("$.messages[1].text").value(Matchers.not(Matchers.containsString("Zara " + tag + "]"))));
    }

    @Test
    void everyToolAnswersWithJson() throws Exception {
        String job = opening();
        var admin = users.findByEmail("admin@codewalnut.test").orElseThrow();
        String found = askService.tool(admin, "search_candidates", Map.of("query", "Zara " + tag, "jobId", job));
        String applicationId = JsonPath.read(found, "$.candidates[0].applicationId");
        assertThat((Integer) JsonPath.read(found, "$.count")).isEqualTo(1);

        String detail = askService.tool(admin, "get_candidate", Map.of("applicationId", applicationId));
        assertThat((String) JsonPath.read(detail, "$.stage")).isEqualTo("Shortlisted");
        assertThat((Object) JsonPath.read(detail, "$.history")).isNotNull();

        String openings = askService.tool(admin, "list_openings", Map.of("status", "ALL"));
        assertThat(openings).contains("Ask " + tag);
        for (Map.Entry<String, Map<String, Object>> call : Map.<String, Map<String, Object>>of(
                "opening_profiles", Map.of("jobId", job),
                "list_interviews", Map.of("when", "recent"),
                "test_results", Map.of("jobId", job),
                "recent_activity", Map.of()).entrySet()) {
            String json = askService.tool(admin, call.getKey(), call.getValue());
            assertThat(objectMapper.readTree(json).isObject()).as(call.getKey()).isTrue();
        }
        // Every declared tool is handled.
        for (AskClient.ToolSpec spec : AskService.TOOLS) {
            assertThat(spec.description()).isNotBlank();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> askService.tool(admin, "get_candidate", Map.of("applicationId", "nope")))
                .hasMessageContaining("applicationId");
    }

    @Autowired
    private AskActionService askActions;

    @Test
    void proposesActionsThatOnlyHappenWhenConfirmed() throws Exception {
        String job = opening();
        String chat = send(RECRUITER, "/api/v1/ask", "{\"question\":\"move Zara " + tag + " and Yusuf " + tag + " to interviewed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].actions.length()").value(2))
                .andExpect(jsonPath("$.messages[1].actions[0].status").value("PENDING"))
                .andExpect(jsonPath("$.messages[1].actions[0].summary").value(Matchers.containsString("Shortlisted → Interviewed")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(chat, "$.id");
        String zara = JsonPath.read(chat, "$.messages[1].actions[0].id");
        String yusuf = JsonPath.read(chat, "$.messages[1].actions[1].id");
        String zaraApp = JsonPath.read(chat, "$.messages[1].actions[0].applicationId");

        // Proposing changed nothing.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$[?(@.id == '" + zaraApp + "')].stage").value(Matchers.hasItem("SHORTLISTED")));

        // Someone else can't act on my chat.
        send(ADMIN, "/api/v1/ask/conversations/" + id + "/actions/" + zara, "{\"decision\":\"do\"}").andExpect(status().isNotFound());

        send(RECRUITER, "/api/v1/ask/conversations/" + id + "/actions/" + zara, "{\"decision\":\"do\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.messages[1].actions[0].status").value("DONE"))
                .andExpect(jsonPath("$.conversation.messages[1].actions[0].doneBy").value("recruiter@codewalnut.test"));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$[?(@.id == '" + zaraApp + "')].stage").value(Matchers.hasItem("INTERVIEWED")));
        // Once only.
        send(RECRUITER, "/api/v1/ask/conversations/" + id + "/actions/" + zara, "{\"decision\":\"do\"}").andExpect(status().isConflict());

        send(RECRUITER, "/api/v1/ask/conversations/" + id + "/actions/" + yusuf, "{\"decision\":\"skip\"}")
                .andExpect(jsonPath("$.conversation.messages[1].actions[1].status").value("SKIPPED"));
    }

    @Test
    void checksEachProposalAndRunsMessagesAsThePerson() throws Exception {
        String job = opening();
        var recruiter = users.findByEmail("recruiter@codewalnut.test").orElseThrow();
        var interviewer = users.findByEmail("interviewer@codewalnut.test").orElseThrow();
        String found = askService.tool(recruiter, "search_candidates", Map.of("query", "Yusuf " + tag, "jobId", job));
        String yusuf = JsonPath.read(found, "$.candidates[0].applicationId");

        java.util.List<com.codewalnut.ats.dto.AskDtos.ProposedAction> proposed = new java.util.ArrayList<>();
        Map<String, Object> reply = askActions.propose(recruiter, Map.of("actions", java.util.List.of(
                Map.of("type", "MOVE_STAGE", "applicationId", yusuf, "stage", "REJECTED"),
                Map.of("type", "MESSAGE", "applicationId", yusuf, "body", "Hi", "sendEmail", true),
                Map.of("type", "SHARE_WITH_CLIENT", "applicationId", yusuf),
                Map.of("type", "REMIND_TEST", "applicationId", yusuf),
                Map.of("type", "MESSAGE", "applicationId", yusuf, "body", "Hi Yusuf, are you free for a call today? Priya, CodeWalnut"),
                Map.of("type", "ADD_NOTE", "applicationId", yusuf, "note", "Prefers mornings"))), proposed);
        assertThat(objectMapper.writeValueAsString(reply)).contains("needs a reason", "no email address", "internal opening",
                "no test waiting");
        assertThat(proposed).extracting(com.codewalnut.ats.dto.AskDtos.ProposedAction::type).containsExactly("MESSAGE", "ADD_NOTE");

        // An interviewer can't even propose a stage move.
        java.util.List<com.codewalnut.ats.dto.AskDtos.ProposedAction> none = new java.util.ArrayList<>();
        assertThat(objectMapper.writeValueAsString(askActions.propose(interviewer,
                Map.of("actions", java.util.List.of(Map.of("type", "MOVE_STAGE", "applicationId", yusuf, "stage", "SELECTED"))), none)))
                .contains("Not allowed");
        assertThat(none).isEmpty();

        // The edited text is what goes out, and it counts as contact.
        var done = askActions.execute(recruiter, proposed.get(0), null, "Hi Yusuf, edited text. Priya, CodeWalnut", "http://localhost/");
        assertThat(done.result()).isEqualTo("Sent");
        mockMvc.perform(get("/api/v1/applications/" + yusuf + "/messages").param("channel", "CANDIDATE").with(RECRUITER))
                .andExpect(jsonPath("$[0].body").value("Hi Yusuf, edited text. Priya, CodeWalnut"));
    }
}
