package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TRK-15, CLI-10: rename an opening, change its client or hiring type, rename a client. Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
class OpeningEditFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");
    private static final RequestPostProcessor HIRING_MANAGER = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private ResultActions send(RequestPostProcessor who, MockHttpServletRequestBuilder r, String body) throws Exception {
        return mockMvc.perform(r.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String client(String name) throws Exception {
        return id(send(RECRUITER, post("/api/v1/clients"), "{\"name\":\"" + name + " " + tag + "\"}"));
    }

    @Test
    void renameAndMoveAnOpeningBetweenClients() throws Exception {
        String acme = client("Acme");
        String globex = client("Globex");
        String job = id(send(RECRUITER, post("/api/v1/jobs"), "{\"title\":\"Java Dev " + tag + "\",\"clientId\":\"" + acme
                + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}"));

        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"title\":\"Senior Java Developer " + tag + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Java Developer " + tag));
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"title\":\"   \"}")
                .andExpect(jsonPath("$.title").value("Senior Java Developer " + tag));

        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"clientId\":\"" + globex + "\",\"hiringType\":\"DIRECT_PLACEMENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Globex " + tag))
                .andExpect(jsonPath("$.hiringType").value("DIRECT_PLACEMENT"));

        // Internal openings have no client; a client opening needs one.
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"hiringType\":\"INTERNAL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client").doesNotExist())
                .andExpect(jsonPath("$.hiringType").value("INTERNAL"));
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"hiringType\":\"CLIENT_DEPLOYED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("needs a client")));
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"hiringType\":\"CLIENT_DEPLOYED\",\"clientId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());

        send(HIRING_MANAGER, patch("/api/v1/jobs/" + job), "{\"title\":\"Nope\"}").andExpect(status().isForbidden());
        send(INTERVIEWER, patch("/api/v1/jobs/" + job), "{\"title\":\"Nope\"}").andExpect(status().isForbidden());
    }

    @Test
    void movingToAnotherClientWaitsUntilSharesAreStopped() throws Exception {
        String acme = client("Initech");
        String globex = client("Umbrella");
        String job = id(send(RECRUITER, post("/api/v1/jobs"), "{\"title\":\"QA " + tag + "\",\"clientId\":\"" + acme
                + "\",\"hiringType\":\"CLIENT_DEPLOYED\"}"));
        String app = id(send(RECRUITER, post("/api/v1/jobs/" + job + "/applications"),
                "{\"name\":\"Ravi Kumar\",\"stage\":\"SHORTLISTED\",\"email\":\"ravi." + tag + "@example.com\"}"));
        send(RECRUITER, put("/api/v1/applications/" + app + "/client-share"),
                "{\"includeContact\":false,\"includeProfile\":true,\"documentIds\":[]}").andExpect(status().isOk());

        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"clientId\":\"" + globex + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("Stop sharing 1 candidate with Initech " + tag)));
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"hiringType\":\"INTERNAL\"}").andExpect(status().isConflict());
        // Other edits still work while shared.
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"title\":\"QA Engineer " + tag + "\",\"hiringType\":\"DIRECT_PLACEMENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Initech " + tag));

        mockMvc.perform(delete("/api/v1/applications/" + app + "/client-share").with(RECRUITER).with(csrf())).andExpect(status().isOk());
        send(RECRUITER, patch("/api/v1/jobs/" + job), "{\"clientId\":\"" + globex + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Umbrella " + tag));
    }

    @Test
    void renameAClient() throws Exception {
        String c = client("Blend");
        client("Taken");
        send(RECRUITER, patch("/api/v1/clients/" + c), "{\"name\":\"Blend Technologies " + tag + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Blend Technologies " + tag));
        send(RECRUITER, patch("/api/v1/clients/" + c), "{\"name\":\"taken " + tag + "\"}").andExpect(status().isConflict());
        send(RECRUITER, patch("/api/v1/clients/" + c), "{\"name\":\"BLEND TECHNOLOGIES " + tag + "\"}")
                .andExpect(jsonPath("$.name").value("BLEND TECHNOLOGIES " + tag));
        send(RECRUITER, patch("/api/v1/clients/" + UUID.randomUUID()), "{\"name\":\"X\"}").andExpect(status().isNotFound());
        send(HIRING_MANAGER, patch("/api/v1/clients/" + c), "{\"name\":\"X\"}").andExpect(status().isForbidden());
    }
}
