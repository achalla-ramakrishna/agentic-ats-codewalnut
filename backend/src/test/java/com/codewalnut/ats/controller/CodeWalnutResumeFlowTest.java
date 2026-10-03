package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.ResumeTextTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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

/** CodeWalnut résumés (CWR-01…): draft from the original, edit, PDF/Word, save. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class CodeWalnutResumeFlowTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor HM = user("hiring.manager@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private ResultActions json(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void draftEditExportAndSaveWithoutThePhoneNumber() throws Exception {
        String job = JsonPath.read(json(RECRUITER, "/api/v1/jobs", "{\"title\":\"CWR " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String created = json(RECRUITER, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Ravi Tester\",\"email\":\"ravi." + tag + "@example.test\",\"stage\":\"SHORTLISTED\"}")
                .andReturn().getResponse().getContentAsString();
        String app = JsonPath.read(created, "$.id");
        String candidate = JsonPath.read(created, "$.candidateId");

        json(RECRUITER, "/api/v1/applications/" + app + "/codewalnut-resume/generate", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("original résumé")));

        mockMvc.perform(multipart("/api/v1/candidates/" + candidate + "/documents")
                        .file(new MockMultipartFile("file", "ravi.pdf", "application/pdf", ResumeTextTestSupport.pdf(
                                "Ravi Tester", "Java Developer", "Mysuru | +91 98450 12345 | ravi@example.test",
                                "SUMMARY", "Builds REST APIs with Spring Boot.",
                                "EXPERIENCE", "Intern at Acme, 2025", "Phone: 9845012345",
                                "EDUCATION", "B.E. Computer Science, 2022 - 2026")))
                        .param("kind", "ORIGINAL_RESUME").with(RECRUITER).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/applications/" + app + "/codewalnut-resume").with(RECRUITER))
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.aiAvailable").value(true));

        String draft = json(RECRUITER, "/api/v1/applications/" + app + "/codewalnut-resume/generate", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.sourceFileName").value("ravi.pdf"))
                .andExpect(jsonPath("$.resume.name").value("Ravi Tester"))
                .andExpect(jsonPath("$.resume.summary").value("Builds REST APIs with Spring Boot."))
                .andExpect(jsonPath("$.resume.sections[*].title", Matchers.contains("Experience", "Education")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(draft).doesNotContain("98450", "9845012345")
                .contains("2022 - 2026");

        // A person edits it; a phone typed in is still removed.
        mockMvc.perform(put("/api/v1/applications/" + app + "/codewalnut-resume").with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showEmail\":false,\"includeScreening\":true,\"resume\":{\"name\":\"Ravi Tester\","
                                + "\"headline\":\"Java Developer | Spring Boot\",\"location\":\"Mysuru, India\",\"email\":\"ravi@example.test\","
                                + "\"summary\":\"Spring Boot developer. Call 98450 12345.\",\"skills\":[{\"label\":\"Backend\",\"items\":[\"Java\",\"Spring Boot\"]}],"
                                + "\"sections\":[{\"title\":\"Experience\",\"entries\":[{\"title\":\"Intern\",\"subtitle\":\"Acme\",\"period\":\"2025\",\"bullets\":[\"Built APIs.\"]}]}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showEmail").value(false))
                .andExpect(jsonPath("$.resume.summary").value("Spring Boot developer. Call ."));

        mockMvc.perform(get("/api/v1/applications/" + app + "/codewalnut-resume.pdf").with(HM))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("Ravi%20Tester%20-%20CodeWalnut.pdf")));
        mockMvc.perform(get("/api/v1/applications/" + app + "/codewalnut-resume.docx").with(RECRUITER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")));

        // Only people who manage openings make or save them.
        json(HM, "/api/v1/applications/" + app + "/codewalnut-resume/generate", "").andExpect(status().isForbidden());
        json(HM, "/api/v1/applications/" + app + "/codewalnut-resume/save", "").andExpect(status().isForbidden());

        json(RECRUITER, "/api/v1/applications/" + app + "/codewalnut-resume/save", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Ravi Tester - CodeWalnut.pdf"));
        mockMvc.perform(get("/api/v1/candidates/" + candidate + "/documents").with(RECRUITER))
                .andExpect(jsonPath("$[?(@.kind == 'CODEWALNUT_RESUME')].fileName").value(Matchers.hasItem("Ravi Tester - CodeWalnut.pdf")));
    }
}
