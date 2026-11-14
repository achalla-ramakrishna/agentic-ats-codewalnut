package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Export an opening's candidate contacts for a phone (vCard) or a spreadsheet (CSV). Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class ContactExportTest {

    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    /** Phones are unique per run (the app matches people by phone). */
    private final String digits = String.valueOf(1000 + (int) (Math.random() * 9000));

    private ResultActions post(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders_post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder MockMvcRequestBuilders_post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private String add(String job, String name, String phone) throws Exception {
        return JsonPath.read(post(RECRUITER, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"" + name + "\",\"email\":\"" + name.split(" ")[0].replace(",", "").toLowerCase() + "." + tag + "@example.com\",\"phone\":\""
                        + phone + "\",\"stage\":\"SCREENING\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void exportsContactsForAPhoneOrASpreadsheet() throws Exception {
        String job = JsonPath.read(post(RECRUITER, "/api/v1/jobs", "{\"title\":\"Senior Java Developer " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String asha = add(job, "Asha Rao", "98765 4" + digits);
        add(job, "Ravi, Kumar", "+44 7700 90" + digits);

        String vcf = post(RECRUITER, "/api/v1/jobs/" + job + "/contacts/export", "{\"format\":\"VCF\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/vcard")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("senior-java-developer-" + tag + "-contacts.vcf")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(vcf.split("BEGIN:VCARD", -1)).hasSize(3);
        assertThat(vcf).contains("FN:Asha Rao\r\n", "N:Rao;Asha;;;\r\n", "TEL;TYPE=CELL:+91987654" + digits + "\r\n",
                "EMAIL;TYPE=INTERNET:asha." + tag + "@example.com", "ORG:Candidate – Senior Java Developer " + tag, "FN:Ravi\\, Kumar",
                "TEL;TYPE=CELL:+44770090" + digits);

        // Only the ticked people; CSV for spreadsheets.
        String csv = post(RECRUITER, "/api/v1/jobs/" + job + "/contacts/export", "{\"format\":\"CSV\",\"applicationIds\":[\"" + asha + "\"]}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Name,Phone,Email,Stage,Opening,LinkedIn,Added\r\n");
        assertThat(csv).contains("Asha Rao,+91987654" + digits + ",asha." + tag + "@example.com,").doesNotContain("Ravi");

        // People who don't contact candidates can't export; someone from another opening isn't included.
        post(INTERVIEWER, "/api/v1/jobs/" + job + "/contacts/export", "{\"format\":\"VCF\"}").andExpect(status().isForbidden());
        post(RECRUITER, "/api/v1/jobs/" + job + "/contacts/export", "{\"format\":\"CSV\",\"applicationIds\":[\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isBadRequest());
        post(RECRUITER, "/api/v1/jobs/" + job + "/contacts/export", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void phonesAreReadyToCallOrWhatsApp() {
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.phones("98765 43210 / 091234 56789"))
                .containsExactly("+919876543210", "+919123456789");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.phones("919876543210")).containsExactly("+919876543210");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.phones("+1 (415) 555-0100")).containsExactly("+14155550100");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.phones("ext 22")).containsExactly("ext 22");
    }

    @Test
    void spreadsheetCellsCantRunFormulas() {
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.cell("+919876543210")).isEqualTo("+919876543210");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.cell("-2+3")).isEqualTo("-2+3");
        assertThat(com.codewalnut.ats.service.ContactExportServiceAccess.cell("+cmd|x")).isEqualTo("\"'+cmd|x\"");
    }
}
