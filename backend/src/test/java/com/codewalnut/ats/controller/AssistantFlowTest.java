package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The assistant on an opening (AI-12…AI-17), with the offline rule-based assistant the dev
 * profile uses. It proposes; nothing changes until someone applies. Fake people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions send(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String opening(String... names) throws Exception {
        String job = JsonPath.read(send(RECRUITER, "/api/v1/jobs", "{\"title\":\"AI " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        for (String name : names) {
            send(RECRUITER, "/api/v1/jobs/" + job + "/applications", "{\"name\":\"" + name + "\",\"stage\":\"INTERVIEWED\"}");
        }
        return job;
    }

    private ResultActions ask(String job, String instruction) throws Exception {
        return send(RECRUITER, "/api/v1/jobs/" + job + "/assistant", "{\"instruction\":\"" + instruction + "\"}");
    }

    @Test
    void proposesStageMovesForNamesAndAsksAboutAmbiguousOnes() throws Exception {
        String job = opening("Sagar Kumar", "Sucheth R", "Amogh S", "Amogh K", "Divya N");

        ask(job, "sagar, sucheth and amogh are shortlisted")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.actions.length()").value(2))
                .andExpect(jsonPath("$.actions[0].candidateName").value("Sagar Kumar"))
                .andExpect(jsonPath("$.actions[0].fromStage").value("INTERVIEWED"))
                .andExpect(jsonPath("$.actions[0].toStage").value("SHORTLISTED"))
                .andExpect(jsonPath("$.actions[1].candidateName").value("Sucheth R"))
                .andExpect(jsonPath("$.unresolved[0].mention").value("amogh"))
                .andExpect(jsonPath("$.unresolved[0].options.length()").value(2))
                .andExpect(jsonPath("$.unresolved[0].toStage").value("SHORTLISTED"));

        // Nothing changed: it only proposed.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$[?(@.stage == 'SHORTLISTED')]").isEmpty());
    }

    @Test
    void rejectionsNeedAReasonAndUnknownNamesAreReported() throws Exception {
        String job = opening("Ravi Teja", "Meera Iyer");
        ask(job, "ravi is rejected")
                .andExpect(jsonPath("$.actions[0].toStage").value("REJECTED"))
                .andExpect(jsonPath("$.actions[0].needsReason").value(true));
        ask(job, "ravi is rejected because weak in React")
                .andExpect(jsonPath("$.actions[0].note").value("weak in React"))
                .andExpect(jsonPath("$.actions[0].needsReason").value(false));
        ask(job, "zoya is shortlisted")
                .andExpect(jsonPath("$.actions.length()").value(0))
                .andExpect(jsonPath("$.unresolved[0].options.length()").value(0));
        ask(job, "meera is interviewed")
                .andExpect(jsonPath("$.actions.length()").value(0))
                .andExpect(jsonPath("$.notes[0]").value(Matchers.containsString("already at Interviewed")));
    }

    @Test
    void onlyPeopleWhoManageOpeningsCanUseIt() throws Exception {
        String job = opening("Asha Rao");
        send(user("hiring.manager@codewalnut.test"), "/api/v1/jobs/" + job + "/assistant", "{\"instruction\":\"asha is shortlisted\"}")
                .andExpect(status().isForbidden());
        send(user("interviewer@codewalnut.test"), "/api/v1/jobs/" + job + "/assistant", "{\"instruction\":\"asha is shortlisted\"}")
                .andExpect(status().isForbidden());
        ask(job, " ").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assistant/status").with(RECRUITER)).andExpect(jsonPath("$.available").value(true));
    }
}
