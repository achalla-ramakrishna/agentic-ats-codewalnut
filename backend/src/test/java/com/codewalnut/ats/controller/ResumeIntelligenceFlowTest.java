package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.ResumeTextTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Résumé intelligence (RI-01…): bulk upload, reading, suggestions and questions, with the
 * offline keyword reader the dev profile uses and background work run inline. Fake people only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResumeIntelligenceFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final String DESCRIPTION = "We need interns.\\n- Java and Spring Boot\\n- React\\n- Graduating in 2026";

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions json(String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(RECRUITER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String opening() throws Exception {
        return JsonPath.read(json("/api/v1/jobs", "{\"title\":\"RI " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String openingWithDescription() throws Exception {
        String job = opening();
        mockMvc.perform(patch("/api/v1/jobs/" + job).with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"" + DESCRIPTION + "\"}"))
                .andExpect(status().isOk());
        return job;
    }

    private static String phone() {
        return "+9198" + ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    private ResultActions upload(String job, MockMultipartFile... files) throws Exception {
        var request = multipart("/api/v1/jobs/" + job + "/resumes");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mockMvc.perform(request.with(RECRUITER).with(csrf()));
    }

    private MockMultipartFile asha() {
        return new MockMultipartFile("files", "Asha_Resume.pdf", "application/pdf",
                ResumeTextTestSupport.pdf("Asha Tester", "asha." + tag + "@example.test",
                        "B.E. Computer Science, 2026", "Skills: Java, Spring Boot, React"));
    }

    @Test
    void uploadsReadsAndSuggestsWhomToContact() throws Exception {
        String job = openingWithDescription();
        String raviPhone = phone();
        MockMultipartFile ravi = new MockMultipartFile("files", "ravi-cv.docx", "application/octet-stream",
                ResumeTextTestSupport.docx("<w:p><w:r><w:t>Ravi Tester</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:t>" + raviPhone + "</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:t>Python, Django, graduated 2024</w:t></w:r></w:p>"));
        MockMultipartFile notes = new MockMultipartFile("files", "notes.txt", "text/plain", "hello".getBytes());

        upload(job, asha(), ravi, notes)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.failed").value(1));

        // Read in the background (inline in tests): two new candidates, the text file refused.
        mockMvc.perform(get("/api/v1/jobs/" + job + "/resumes").with(RECRUITER))
                .andExpect(jsonPath("$.done").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.newCandidates").value(2))
                .andExpect(jsonPath("$.items[?(@.fileName == 'notes.txt')].error").value(Matchers.hasItem("Only PDF or Word files can be read.")))
                .andExpect(jsonPath("$.items[?(@.fileName == 'Asha_Resume.pdf')].candidateName").value(Matchers.hasItem("Asha Tester")));

        String apps = mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'Asha Tester')].email").value(Matchers.hasItem("asha." + tag + "@example.test")))
                .andExpect(jsonPath("$[?(@.name == 'Ravi Tester')].phone").value(Matchers.hasItem(raviPhone)))
                .andExpect(jsonPath("$[*].stage", Matchers.everyItem(Matchers.is("SOURCED"))))
                .andExpect(jsonPath("$[0].documents", Matchers.contains("ORIGINAL_RESUME")))
                .andReturn().getResponse().getContentAsString();
        String ashaApp = JsonPath.<List<String>>read(apps, "$[?(@.name == 'Asha Tester')].id").get(0);

        mockMvc.perform(get("/api/v1/jobs/" + job + "/insights").with(RECRUITER))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.hasDescription").value(true))
                .andExpect(jsonPath("$.analyzed").value(2))
                .andExpect(jsonPath("$.insights[?(@.applicationId == '" + ashaApp + "')].fitPercent").value(Matchers.hasItem(100)))
                .andExpect(jsonPath("$.contactNext.length()").value(1))
                .andExpect(jsonPath("$.contactNext[0].candidateName").value("Asha Tester"))
                .andExpect(jsonPath("$.contactNext[0].reason").value(Matchers.startsWith("Meets 3 of 3 requirements")))
                .andExpect(jsonPath("$.closestToSelection").isEmpty());

        mockMvc.perform(get("/api/v1/applications/" + ashaApp + "/insight").with(user("hiring.manager@codewalnut.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.documentFileName").value("Asha_Resume.pdf"))
                .andExpect(jsonPath("$.profile.graduationYear").value(2026))
                .andExpect(jsonPath("$.profile.requirements.length()").value(3));

        // The same résumé again: recognised, not added twice.
        upload(job, asha()).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/resumes").with(RECRUITER))
                .andExpect(jsonPath("$.items[3].outcome").value("ALREADY_IN_OPENING"));
        mockMvc.perform(get("/api/v1/jobs/" + job + "/applications").with(RECRUITER))
                .andExpect(jsonPath("$.length()").value(2));

        // Further along the pipeline: closest to selection.
        mockMvc.perform(patch("/api/v1/applications/" + ashaApp + "/stage").with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"CLIENT_INTERVIEW\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/insights").with(RECRUITER))
                .andExpect(jsonPath("$.contactNext").isEmpty())
                .andExpect(jsonPath("$.closestToSelection[0].candidateName").value("Asha Tester"))
                .andExpect(jsonPath("$.closestToSelection[0].reason").value(Matchers.startsWith("At Client interview")));

        // Questions get an answer and the candidates it points to; nothing changes.
        json("/api/v1/jobs/" + job + "/assistant", "{\"instruction\":\"who knows react?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions").isEmpty())
                .andExpect(jsonPath("$.answer").value(Matchers.containsString("react")))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].name").value("Asha Tester"))
                .andExpect(jsonPath("$.matches[0].fitPercent").value(100));
        json("/api/v1/jobs/" + job + "/assistant",
                "{\"instruction\":\"can you give me the list of candidates whom we have not been interviewed so far\"}")
                .andExpect(jsonPath("$.actions").isEmpty())
                .andExpect(jsonPath("$.matches[*].name", Matchers.contains("Ravi Tester")));
    }

    @Test
    void readsExistingCandidatesAndNewResumesAutomatically() throws Exception {
        String job = openingWithDescription();
        String candidateId = JsonPath.read(json("/api/v1/jobs/" + job + "/applications",
                        "{\"name\":\"Divya Tester\",\"phone\":\"" + phone() + "\",\"stage\":\"SCREENING\"}")
                .andReturn().getResponse().getContentAsString(), "$.candidateId");

        json("/api/v1/jobs/" + job + "/insights/analyze", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(0))
                .andExpect(jsonPath("$.noResume").value(1));

        // A résumé added later is read straight away.
        mockMvc.perform(multipart("/api/v1/candidates/" + candidateId + "/documents")
                        .file(new MockMultipartFile("file", "divya.pdf", "application/pdf",
                                ResumeTextTestSupport.pdf("Divya Tester", "React and TypeScript")))
                        .param("kind", "ORIGINAL_RESUME").with(RECRUITER).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/insights").with(RECRUITER))
                .andExpect(jsonPath("$.analyzed").value(1))
                .andExpect(jsonPath("$.insights[0].fitPercent").value(33))
                .andExpect(jsonPath("$.insights[0].met").value(1));

        json("/api/v1/jobs/" + job + "/insights/analyze", "{}")
                .andExpect(jsonPath("$.upToDate").value(1));
    }

    @Test
    void onlyPeopleWhoManageOpeningsCanUploadOrAnalyze() throws Exception {
        String job = opening();
        mockMvc.perform(multipart("/api/v1/jobs/" + job + "/resumes").file(asha())
                        .with(user("hiring.manager@codewalnut.test")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs/" + job + "/insights/analyze")
                        .with(user("hiring.manager@codewalnut.test")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/insights").with(user("interviewer@codewalnut.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/" + job + "/insights").with(user("hiring.manager@codewalnut.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDescription").value(false));
    }
}
