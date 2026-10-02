package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.FakeWhatsAppClient;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Candidate WhatsApp replies and delivery receipts through the signed webhook (MSG-24, MSG-25). */
@SpringBootTest(properties = {"ats.whatsapp.verify-token=fake-verify-token", "ats.whatsapp.app-secret=fake-app-secret"})
@AutoConfigureMockMvc
class WhatsAppWebhookTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeWhatsAppClient whatsApp;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @AfterEach
    void reset() {
        whatsApp.setApiEnabled(false);
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("fake-app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions webhook(String body, String signature) throws Exception {
        var request = post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("X-Hub-Signature-256", signature);
        }
        return mockMvc.perform(request);
    }

    private static String inbound(String from, String id, String text) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{"
                + "\"messages\":[{\"from\":\"" + from + "\",\"id\":\"" + id + "\",\"type\":\"text\",\"text\":{\"body\":\"" + text + "\"}}]}}]}]}";
    }

    private String[] application() throws Exception {
        String phone = WhatsAppFlowTest.randomMobile();
        String job = JsonPath.read(mockMvc.perform(post("/api/v1/jobs").with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hook " + tag + "\",\"hiringType\":\"INTERNAL\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        String app = JsonPath.read(mockMvc.perform(post("/api/v1/jobs/" + job + "/applications").with(ADMIN).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Divya Nair\",\"phone\":\"+91 " + phone + "\",\"stage\":\"SELECTED\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        return new String[] {app, phone};
    }

    @Test
    void metaVerifiesTheWebhookWithTheSharedToken() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "fake-verify-token")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("12345"));
        mockMvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsignedOrWronglySignedCallsAreRejected() throws Exception {
        String[] a = application();
        String body = inbound("91" + a[1], "wamid.forged-" + tag, "I am an attacker");
        webhook(body, null).andExpect(status().isUnauthorized());
        webhook(body, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());
        webhook(body.replace("attacker", "hacker"), sign(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/applications/" + a[0] + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void candidateRepliesLandInTheirConversationOnce() throws Exception {
        String[] a = application();
        String body = inbound("91" + a[1], "wamid.in-" + tag, "Sure, I will upload it tonight");
        webhook(body, sign(body)).andExpect(status().isOk());
        webhook(body, sign(body)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/" + a[0] + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorType").value("CANDIDATE"))
                .andExpect(jsonPath("$[0].whatsapp").value("RECEIVED"))
                .andExpect(jsonPath("$[0].body").value("Sure, I will upload it tonight"));
        String inbox = mockMvc.perform(get("/api/v1/messages/inbox").with(ADMIN)).andReturn().getResponse().getContentAsString();
        List<Object> mine = JsonPath.read(inbox, "$[?(@.applicationId == '" + a[0] + "' && @.awaitingReply == true)]");
        assertThat(mine).hasSize(1);

        String unknown = inbound("919999999999", "wamid.unknown-" + tag, "who is this");
        webhook(unknown, sign(unknown)).andExpect(status().isOk());
    }

    @Test
    void deliveryReceiptsUpdateOurMessageAndRepliesOpenTheFreeTextWindow() throws Exception {
        whatsApp.setApiEnabled(true);
        String[] a = application();
        mockMvc.perform(post("/api/v1/applications/" + a[0] + "/messages").with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"CANDIDATE\",\"body\":\"Please send your Aadhaar\",\"sendWhatsApp\":true}"))
                .andExpect(jsonPath("$.whatsapp").value("SENT"));
        String wamid = mockMvc.perform(get("/api/v1/applications/" + a[0] + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        assertThat(whatsApp.sent().get(whatsApp.sent().size() - 1).freeForm()).isFalse();
        String outgoingId = whatsAppIdOfLastSent();

        String receipt = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"" + outgoingId + "\",\"status\":\"read\"}]}}]}]}";
        webhook(receipt, sign(receipt)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/applications/" + a[0] + "/messages").param("channel", "CANDIDATE").with(ADMIN))
                .andExpect(jsonPath("$[0].whatsapp").value("READ"));
        assertThat(wamid).isNotEmpty();

        String reply = inbound("91" + a[1], "wamid.reply-" + tag, "Done!");
        webhook(reply, sign(reply)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/applications/" + a[0] + "/messages").with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"CANDIDATE\",\"body\":\"Thanks, received.\",\"sendWhatsApp\":true}"))
                .andExpect(jsonPath("$.whatsapp").value("SENT"));
        assertThat(whatsApp.sent().get(whatsApp.sent().size() - 1).freeForm()).isTrue();
    }

    @Autowired
    private com.codewalnut.ats.repository.MessageRepository messageRepository;

    private String whatsAppIdOfLastSent() {
        return messageRepository.findAll().stream()
                .filter(m -> m.getWhatsappMessageId() != null && "SENT".equals(m.getWhatsappStatus()))
                .max(java.util.Comparator.comparing(com.codewalnut.ats.domain.Message::getCreatedAt))
                .orElseThrow().getWhatsappMessageId();
    }
}
