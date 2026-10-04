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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** INT-21, INT-22: interview questions are for staff who interview; candidates, client contacts and approvers can't read them. */
@SpringBootTest
@AutoConfigureMockMvc
class InterviewGuideFlowTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void interviewersAndRecruitersReadTheGuide() throws Exception {
        for (String who : new String[] {"interviewer@codewalnut.test", "recruiter@codewalnut.test", "hiring.manager@codewalnut.test"}) {
            mockMvc.perform(get("/api/v1/interview-guide").with(user(who)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories.length()").value(Matchers.greaterThanOrEqualTo(15)))
                    .andExpect(jsonPath("$.categories[?(@.id == 'dsa-freshers')].questions[*].language",
                            Matchers.hasItems("Java", "Python", "JavaScript", "C++")))
                    .andExpect(jsonPath("$.scale.length()").value(4))
                    .andExpect(jsonPath("$.roles[?(@.id == 'java-backend')].categories[*]", Matchers.hasItems("java", "dsa-freshers")));
        }
    }

    @Test
    void candidatesClientsAndApproversCannot() throws Exception {
        mockMvc.perform(get("/api/v1/interview-guide").with(user("approver@codewalnut.test"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interview-guide")).andExpect(status().is4xxClientError());
        String tag = UUID.randomUUID().toString().substring(0, 8);
        // A candidate signed in with Google.
        mockMvc.perform(get("/api/v1/interview-guide").session(signIn("cand." + tag + "@gmail.com")))
                .andExpect(status().is4xxClientError());
        // A client contact.
        String client = JsonPath.read(mockMvc.perform(post("/api/v1/clients").with(user("admin@codewalnut.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Guide Co " + tag + "\"}")).andReturn().getResponse()
                .getContentAsString(), "$.id");
        String contact = "hm." + tag + "@guideco.example";
        mockMvc.perform(post("/api/v1/clients/" + client + "/contacts").with(user("admin@codewalnut.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + contact + "\",\"name\":\"HM\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/interview-guide").session(signIn(contact))).andExpect(status().is4xxClientError());
    }
}
