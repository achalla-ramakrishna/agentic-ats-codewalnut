package com.codewalnut.ats.config;

import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.FakeCalendarClient;
import com.codewalnut.ats.client.FakeMailClient;
import com.codewalnut.ats.client.GmailClient;
import com.codewalnut.ats.client.GoogleAccess;
import com.codewalnut.ats.client.GoogleCalendarClient;
import com.codewalnut.ats.client.MailClient;
import com.codewalnut.ats.client.FakeWhatsAppClient;
import com.codewalnut.ats.client.WhatsAppClient;
import com.codewalnut.ats.client.WhatsAppCloudClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

/**
 * Real Google Calendar, Gmail and WhatsApp in production; fakes in dev and demo so nothing is
 * ever sent from them.
 */
@Configuration
public class IntegrationsConfig {

    @Bean
    @Profile({"dev", "demo"})
    public FakeCalendarClient fakeCalendarClient() {
        return new FakeCalendarClient();
    }

    @Bean
    @Profile({"dev", "demo"})
    public FakeMailClient fakeMailClient() {
        return new FakeMailClient();
    }

    @Bean
    @Profile("!dev & !demo")
    public GoogleAccess googleAccess(ObjectProvider<ClientRegistrationRepository> registrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        return new GoogleAccess(registrations.getIfAvailable(), authorizedClients);
    }

    @Bean
    @Profile("!dev & !demo")
    public CalendarClient googleCalendarClient(GoogleAccess google, RestClient.Builder restClientBuilder) {
        return new GoogleCalendarClient(google, restClientBuilder.clone().baseUrl(GoogleCalendarClient.BASE_URL).build());
    }

    @Bean
    @Profile("!dev & !demo")
    public MailClient gmailClient(GoogleAccess google, RestClient.Builder restClientBuilder) {
        return new GmailClient(google, restClientBuilder.clone().baseUrl(GmailClient.BASE_URL).build());
    }

    @Bean
    @Profile({"dev", "demo"})
    public FakeWhatsAppClient fakeWhatsAppClient() {
        return new FakeWhatsAppClient();
    }

    @Bean
    @Profile("!dev & !demo")
    public WhatsAppClient whatsAppClient(WhatsAppProperties properties, RestClient.Builder restClientBuilder) {
        return new WhatsAppCloudClient(properties, restClientBuilder.clone().baseUrl(WhatsAppCloudClient.BASE_URL).build());
    }
}
