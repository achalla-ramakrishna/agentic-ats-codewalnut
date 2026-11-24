package com.codewalnut.ats.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.client.ResumeTextTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * RI-…: a résumé uploaded while the AI is out of credit isn't lost — the file is kept and read again (Try again or
 * every 10 minutes). Fake people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResumeRetryTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private ResumeAnalyzer analyzer;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @Test
    void anUploadTheAiCouldntReadIsKeptAndReadAgain() throws Exception {
        String job = JsonPath.read(mockMvc.perform(post("/api/v1/jobs").with(RECRUITER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Retry " + tag + "\",\"hiringType\":\"INTERNAL\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        doThrow(new CalendarException("The AI is unavailable: the Anthropic account has run out of credit. This résumé will need another try."))
                .when(analyzer).analyze(any(), any());
        MockMultipartFile resume = new MockMultipartFile("files", "Meera_Resume.pdf", "application/pdf",
                ResumeTextTestSupport.pdf("Meera Tester", "meera." + tag + "@example.test", "Skills: Java, Spring Boot"));
        mockMvc.perform(multipart("/api/v1/jobs/" + job + "/resumes").file(resume).with(RECRUITER).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/resumes").with(RECRUITER))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.items[0].error").value(Matchers.containsStringIgnoringCase("it will try again automatically")));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER)).andExpect(jsonPath("$.length()").value(0));

        // Credit added: Try again reads the kept file; the candidate is added and read.
        doCallRealMethod().when(analyzer).analyze(any(), any());
        mockMvc.perform(post("/api/v1/jobs/" + job + "/resumes/retry").with(RECRUITER).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads").value(1));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/resumes").with(RECRUITER))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.failed").value(0));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$[0].name").value("Meera Tester"));
        // Nothing left to retry.
        mockMvc.perform(post("/api/v1/jobs/" + job + "/resumes/retry").with(RECRUITER).with(csrf())).andExpect(jsonPath("$.uploads").value(0));
    }
}
