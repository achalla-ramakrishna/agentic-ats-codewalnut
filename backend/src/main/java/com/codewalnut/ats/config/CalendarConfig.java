package com.codewalnut.ats.config;

import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.FakeCalendarClient;
import com.codewalnut.ats.client.GoogleCalendarClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

/** Real Google Calendar in production; a fake one in dev and demo so nothing is ever sent from them. */
@Configuration
public class CalendarConfig {

    @Bean
    @Profile({"dev", "demo"})
    public FakeCalendarClient fakeCalendarClient() {
        return new FakeCalendarClient();
    }

    @Bean
    @Profile("!dev & !demo")
    public CalendarClient googleCalendarClient(ObjectProvider<ClientRegistrationRepository> registrations,
            OAuth2AuthorizedClientRepository authorizedClients, RestClient.Builder restClientBuilder) {
        return new GoogleCalendarClient(registrations.getIfAvailable(), authorizedClients,
                restClientBuilder.baseUrl(GoogleCalendarClient.BASE_URL).build());
    }
}
