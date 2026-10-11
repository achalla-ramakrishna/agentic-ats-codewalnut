package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.naukri.FakeXlsx;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** ADR-0026: rank a Naukri export against the opening, then add only the chosen rows. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class NaukriImportFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private static String phone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private String json(RequestPostProcessor who, String url, Object body, int expected) throws Exception {
        return mockMvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] export() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(FakeXlsx.NAUKRI_HEADINGS);
        rows.add(FakeXlsx.row("Unrelated " + tag, "unrelated." + tag + "@example.com", phone(), "6 Year(s) 0 Month(s)",
                "Accountant", "Chartered accountant, GST and audits", "Chennai", "3 Months"));
        rows.add(FakeXlsx.row("Strong " + tag, "strong." + tag + "@example.com", phone(), "6 Year(s) 2 Month(s)",
                "Senior Software Engineer", "Java, Spring Boot, microservices and Kafka developer", "Bengaluru", "15 Days or less"));
        rows.add(FakeXlsx.row("Strong Again " + tag, "strong." + tag + "@example.com", phone(), "6 Year(s) 2 Month(s)",
                "Senior Software Engineer", "Java developer", "Bengaluru", "15 Days or less"));
        return FakeXlsx.of(rows);
    }

    @Test
    void rankThenImportTheChosenRows() throws Exception {
        String jobId = JsonPath.read(json(ADMIN, "/api/v1/jobs", Map.of("title", "Senior Java Developer " + tag,
                "hiringType", "INTERNAL", "openings", 2), 201), "$.id");
        mockMvc.perform(patch("/api/v1/jobs/" + jobId).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("description",
                        "Senior Java Developer, 5-8 years: Java, Spring Boot, microservices, Kafka, MySQL. Bengaluru.")))).andExpect(status().isOk());
        MockMultipartFile file = new MockMultipartFile("file", "Senior-Java-Developer.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", export());

        mockMvc.perform(multipart("/api/v1/jobs/" + jobId + "/naukri/rank").file(file).with(INTERVIEWER).with(csrf()))
                .andExpect(status().isForbidden());
        String ranked = mockMvc.perform(multipart("/api/v1/jobs/" + jobId + "/naukri/rank").file(file).with(ADMIN).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat((Integer) JsonPath.read(ranked, "$.total")).isEqualTo(3);
        assertThat((String) JsonPath.read(ranked, "$.rankedBy")).isEqualTo("RULES");
        assertThat((String) JsonPath.read(ranked, "$.rows[0].profile.name")).isEqualTo("Strong " + tag);
        assertThat((Integer) JsonPath.read(ranked, "$.rows[0].matchPercent")).isGreaterThan(JsonPath.read(ranked, "$.rows[2].matchPercent"));
        assertThat((String) JsonPath.read(ranked, "$.rows[0].status")).isEqualTo("NEW");
        assertThat(ranked).contains("DUPLICATE_IN_FILE").doesNotContain("Female", "1995");

        Map<String, Object> top = JsonPath.read(ranked, "$.rows[0]");
        String imported = json(ADMIN, "/api/v1/jobs/" + jobId + "/naukri/import", Map.of("stage", "SOURCED", "rows",
                List.of(Map.of("profile", top.get("profile"), "matchPercent", top.get("matchPercent"), "summary", top.get("summary")))), 200);
        assertThat((Integer) JsonPath.read(imported, "$.added")).isEqualTo(1);

        String list = mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications").with(ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(list, "$[*].name");
        assertThat(names).containsExactly("Strong " + tag);
        assertThat((String) JsonPath.read(list, "$[0].lastNote")).startsWith("From Naukri (applied 09-Oct-2026) · Match ")
                .contains("Notice: 15 Days or less");

        // Ranking again shows them as already in the opening; importing again adds nobody.
        String again = mockMvc.perform(multipart("/api/v1/jobs/" + jobId + "/naukri/rank").file(file).with(ADMIN).with(csrf()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(again, "$.rows[0].status")).isEqualTo("ALREADY_IN_OPENING");
        String twice = json(ADMIN, "/api/v1/jobs/" + jobId + "/naukri/import", Map.of("stage", "SOURCED", "rows",
                List.of(Map.of("profile", top.get("profile")))), 200);
        assertThat((Integer) JsonPath.read(twice, "$.added")).isZero();
        assertThat(twice).contains("already in this opening");
    }

    @Test
    void aKnownPersonWithoutAnEmailGetsTheOneFromTheSheet() throws Exception {
        String phone = phone();
        String first = JsonPath.read(json(ADMIN, "/api/v1/jobs", Map.of("title", "First " + tag, "hiringType", "INTERNAL", "openings", 1), 201), "$.id");
        json(ADMIN, "/api/v1/jobs/" + first + "/applications/import",
                Map.of("text", "name\tphone\nKnown " + tag + "\t" + phone, "stage", "SOURCED", "dryRun", false), 200);
        String second = JsonPath.read(json(ADMIN, "/api/v1/jobs", Map.of("title", "Second " + tag, "hiringType", "INTERNAL", "openings", 1), 201), "$.id");

        Map<String, Object> profile = new java.util.HashMap<>(Map.of("line", 2, "name", "Known " + tag,
                "email", "a." + tag + "@example.com, b." + tag + "@example.com", "phone", phone));
        String imported = json(ADMIN, "/api/v1/jobs/" + second + "/naukri/import",
                Map.of("stage", "SOURCED", "rows", List.of(Map.of("profile", profile))), 200);

        assertThat((Integer) JsonPath.read(imported, "$.added")).isEqualTo(1);
        assertThat((String) JsonPath.read(imported, "$.rows[0].email")).isEqualTo("a." + tag + "@example.com");
        assertThat((Boolean) JsonPath.read(imported, "$.rows[0].hasEmail")).isTrue();
    }

    @Test
    void aFileThatIsNotASpreadsheetIsRefusedWithAClearMessage() throws Exception {
        String jobId = JsonPath.read(json(ADMIN, "/api/v1/jobs", Map.of("title", "Any " + tag, "hiringType", "INTERNAL", "openings", 1), 201), "$.id");
        String body = mockMvc.perform(multipart("/api/v1/jobs/" + jobId + "/naukri/rank")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-1.4".getBytes())).with(ADMIN).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("isn't an Excel");
    }
}
