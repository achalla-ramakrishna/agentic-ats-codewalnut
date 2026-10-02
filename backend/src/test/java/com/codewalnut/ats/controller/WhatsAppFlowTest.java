package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.FakeMailClient;
import com.codewalnut.ats.client.FakeWhatsAppClient;
import com.codewalnut.ats.client.WhatsAppClient;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** WhatsApp from the candidate conversation (MSG-21…MSG-25). Fake people and numbers only. */
@SpringBootTest
@AutoConfigureMockMvc
class WhatsAppFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeWhatsAppClient whatsApp;

    @Autowired
    private FakeMailClient mail;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @AfterEach
    void reset() {
        whatsApp.setApiEnabled(false);
        whatsApp.setFailing(false);
        mail.setFailing(false);
    }

    static String randomMobile() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private ResultActions send(String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Returns [applicationId, phone]. */
    private String[] application(boolean withPhone) throws Exception {
        String phone = randomMobile();
        String job = JsonPath.read(send("/api/v1/jobs", "{\"title\":\"WA " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String app = JsonPath.read(send("/api/v1/jobs/" + job + "/applications", "{\"name\":\"Ravi Teja\",\"email\":\"wa." + tag
                + "@gmail.com\"" + (withPhone ? ",\"phone\":\"" + phone + "\"" : "") + ",\"stage\":\"SELECTED\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        return new String[] {app, phone};
    }

    private static String message(String text, boolean email, boolean whatsapp) {
        return "{\"channel\":\"CANDIDATE\",\"body\":\"" + text + "\",\"sendEmail\":" + email + ",\"sendWhatsApp\":" + whatsapp + "}";
    }

    @Test
    void withoutTheBusinessApiWhatsAppOpensWithTheMessageReady() throws Exception {
        String[] a = application(true);
        send("/api/v1/applications/" + a[0] + "/messages", message("Can you send your Aadhaar card?", false, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.whatsapp").value("OPENED"))
                .andExpect(jsonPath("$.whatsappLink").value(Matchers.startsWith("https://wa.me/91" + a[1] + "?text=Can%20you%20send%20your%20Aadhaar%20card%3F")))
                .andExpect(jsonPath("$.emailed").value(false));
        assertThat(whatsApp.sent()).noneMatch(m -> m.to().endsWith(a[1]));
        mockMvc.perform(get("/api/v1/applications/" + a[0] + "/history").with(ADMIN))
                .andExpect(jsonPath("$[0].type").value("WHATSAPP_SENT"))
                .andExpect(jsonPath("$[0].note").value("WhatsApp opened to send: Can you send your Aadhaar card?"));
        mockMvc.perform(get("/api/v1/whatsapp/status").with(ADMIN))
                .andExpect(jsonPath("$.apiEnabled").value(false))
                .andExpect(jsonPath("$.repliesEnabled").value(false));
    }

    @Test
    void withTheBusinessApiItSendsTheApprovedTemplate() throws Exception {
        whatsApp.setApiEnabled(true);
        String[] a = application(true);
        send("/api/v1/applications/" + a[0] + "/messages", message("Please upload your PAN card.", true, true))
                .andExpect(jsonPath("$.whatsapp").value("SENT"))
                .andExpect(jsonPath("$.emailed").value(true))
                .andExpect(jsonPath("$.whatsappLink").doesNotExist());
        WhatsAppClient.Outgoing sent = whatsApp.sent().get(whatsApp.sent().size() - 1);
        assertThat(sent.to()).isEqualTo("91" + a[1]);
        assertThat(sent.firstName()).isEqualTo("Ravi");
        assertThat(sent.jobTitle()).isEqualTo("WA " + tag);
        assertThat(sent.freeForm()).isFalse();
        assertThat(sent.text()).startsWith("Please upload your PAN card.").contains("http://localhost/");
    }

    @Test
    void oneChannelFailingDoesNotLoseTheOther() throws Exception {
        whatsApp.setApiEnabled(true);
        whatsApp.setFailing(true);
        String[] a = application(true);
        send("/api/v1/applications/" + a[0] + "/messages", message("Hello", true, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailed").value(true))
                .andExpect(jsonPath("$.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.warnings[0]").value(Matchers.containsString("WhatsApp failed")));

        send("/api/v1/applications/" + a[0] + "/messages", message("Only WhatsApp", false, true))
                .andExpect(status().isBadGateway());
        mockMvc.perform(get("/api/v1/applications/" + a[0] + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void whatsAppNeedsAMobileNumber() throws Exception {
        String[] a = application(false);
        send("/api/v1/applications/" + a[0] + "/messages", message("Hello", false, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("mobile number")));
    }

    @Test
    void theWebhookIsOffUntilConfigured() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "x").param("hub.challenge", "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
