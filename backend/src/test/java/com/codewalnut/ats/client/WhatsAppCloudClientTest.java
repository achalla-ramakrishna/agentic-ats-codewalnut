package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codewalnut.ats.config.WhatsAppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Cloud API request shapes, against a mock Meta. */
class WhatsAppCloudClientTest {

    private static final WhatsAppProperties PROPS =
            new WhatsAppProperties("fake-token", "123456", "candidate_message", "en", "", "", "91");

    private WhatsAppCloudClient client(MockRestServiceServer[] server) {
        RestClient.Builder builder = RestClient.builder().baseUrl(WhatsAppCloudClient.BASE_URL);
        server[0] = MockRestServiceServer.bindTo(builder).build();
        return new WhatsAppCloudClient(PROPS, builder.build());
    }

    @Test
    void sendsTheApprovedTemplateWithOneLineText() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        WhatsAppCloudClient client = client(server);
        server[0].expect(requestTo(WhatsAppCloudClient.BASE_URL + "/123456/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fake-token"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.to").value("919000011111"))
                .andExpect(jsonPath("$.template.name").value("candidate_message"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("Ravi"))
                .andExpect(jsonPath("$.template.components[0].parameters[2].text").value("Hi Ravi, · Please upload your PAN."))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.send(new WhatsAppClient.Outgoing("919000011111", "Ravi", "Intern", "Hi Ravi,\n\n  Please upload your PAN.", false)))
                .isEqualTo("wamid.1");
        server[0].verify();
    }

    @Test
    void sendsPlainTextInsideTheReplyWindow() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        WhatsAppCloudClient client = client(server);
        server[0].expect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Line one\nLine two"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.2\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.send(new WhatsAppClient.Outgoing("919000011111", "Ravi", "Intern", "Line one\nLine two", true)))
                .isEqualTo("wamid.2");
    }

    @Test
    void refusalsAreClearAndLongTextIsTrimmed() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        WhatsAppCloudClient client = client(server);
        server[0].expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.send(new WhatsAppClient.Outgoing("919000011111", "R", "I", "x", false)))
                .isInstanceOf(CalendarException.class).hasMessageContaining("template");
        assertThat(WhatsAppCloudClient.templateText("a".repeat(2000))).hasSize(WhatsAppCloudClient.MAX_TEMPLATE_TEXT);
    }
}
