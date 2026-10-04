package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** PIPE-13, PIPE-14: put one candidate forward for another client's opening. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateOpeningsFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions post(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String job(String title, String client) throws Exception {
        String clientId = id(post(ADMIN, "/api/v1/clients", "{\"name\":\"" + client + " " + tag + "\"}"));
        return id(post(ADMIN, "/api/v1/jobs", "{\"title\":\"" + title + " " + tag + "\",\"clientId\":\"" + clientId
                + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}"));
    }

    @Test
    void recruiterPutsACandidateForwardForAnotherClient() throws Exception {
        String acme = job("Java Developer", "Acme");
        String globex = job("Spring Boot Developer", "Globex");
        String app = id(post(RECRUITER, "/api/v1/jobs/" + acme + "/applications",
                "{\"name\":\"Asha Rao\",\"stage\":\"INTERVIEWED\",\"email\":\"asha." + tag + "@example.com\"}"));

        String added = id(post(RECRUITER, "/api/v1/applications/" + app + "/openings",
                "{\"jobId\":\"" + globex + "\",\"note\":\"Strong Spring skills\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("SOURCED"))
                .andExpect(jsonPath("$.name").value("Asha Rao")));

        // Same candidate record; both openings listed, from either side.
        String fromNew = mockMvc.perform(get("/api/v1/applications/" + added + "/openings").with(HIRING_MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].applicationId").value(added))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].clientName").value("Globex " + tag))
                .andExpect(jsonPath("$[1].stage").value("INTERVIEWED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(fromNew).contains("Acme " + tag);
        mockMvc.perform(get("/api/v1/applications/" + app + "/history").with(RECRUITER))
                .andExpect(jsonPath("$[0].note").value("Also put forward for Spring Boot Developer " + tag + " (Globex " + tag + "): Strong Spring skills"));
        mockMvc.perform(get("/api/v1/applications/" + added + "/history").with(RECRUITER))
                .andExpect(jsonPath("$[0].note").value("Added from Java Developer " + tag + " (Acme " + tag + "): Strong Spring skills"));

        // Stages stay per opening.
        mockMvc.perform(patch("/api/v1/applications/" + added + "/stage").with(RECRUITER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SCREENING\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/applications/" + app + "/openings").with(RECRUITER))
                .andExpect(jsonPath("$[1].stage").value("INTERVIEWED"))
                .andExpect(jsonPath("$[1].current").value(true))
                .andExpect(jsonPath("$[0].stage").value("SCREENING"));

        // Not twice.
        post(RECRUITER, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + globex + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void refusesClosedOpeningsExitStagesAndPeopleWhoCantManageJobs() throws Exception {
        String a = job("QA Engineer", "Initech");
        String b = job("SDET", "Umbrella");
        String app = id(post(ADMIN, "/api/v1/jobs/" + a + "/applications", "{\"name\":\"Ravi Kumar\",\"stage\":\"SCREENING\"}"));

        post(ADMIN, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + b + "\",\"stage\":\"REJECTED\"}")
                .andExpect(status().isBadRequest());
        post(INTERVIEWER, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + b + "\"}").andExpect(status().isForbidden());
        post(HIRING_MANAGER, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + b + "\"}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/applications/" + app + "/openings").with(INTERVIEWER)).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/jobs/" + b).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\"}")).andExpect(status().isOk());
        post(ADMIN, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + b + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("closed")));
        post(ADMIN, "/api/v1/applications/" + app + "/openings", "{\"jobId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());
    }
}
