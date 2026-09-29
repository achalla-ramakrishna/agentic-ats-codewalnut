package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Google sign-in wiring with a (fake) client configured, as on Railway. */
@SpringBootTest(properties = {
    "spring.profiles.active=default",
    "spring.security.oauth2.client.registration.google.client-id=fake-id.apps.googleusercontent.com",
    "spring.security.oauth2.client.registration.google.client-secret=fake-secret"
})
@AutoConfigureMockMvc
class GoogleSignInConfiguredTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageOffersGoogleAndTheRedirectUriToRegister() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(jsonPath("$.googleEnabled").value(true))
                .andExpect(jsonPath("$.googleRedirectUri").value("http://localhost/login/oauth2/code/google"))
                .andExpect(jsonPath("$.devLoginEnabled").value(false));
    }

    @Test
    void continueWithGoogleRedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("client_id=fake-id.apps.googleusercontent.com"),
                        containsString("scope=openid%20profile%20email"),
                        containsString("redirect_uri=http://localhost/login/oauth2/code/google"))));
    }

    @Test
    void springsBuiltInLoginPageIsNotServed() throws Exception {
        String body = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Login with OAuth 2.0");
    }

    @Test
    void aFailedGoogleSignInReturnsToOurLoginPageWithError() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google").param("error", "access_denied").param("state", "x"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void staffConnectGoogleCalendarWithOnlyTheCalendarEventsScope() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google-calendar")
                        .with(user("admin@codewalnut.test").roles("STAFF")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("scope=https://www.googleapis.com/auth/calendar.events"),
                        not(containsString("openid")),
                        containsString("login_hint=admin@codewalnut.test"),
                        containsString("redirect_uri=http://localhost/oauth2/callback/google-calendar"))));
    }

    @Test
    void candidatesAndStrangersCannotStartACalendarConnection() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google-calendar"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().doesNotExist("Location"));
        mockMvc.perform(get("/oauth2/authorization/google-calendar")
                        .with(user("someone@gmail.com").roles("CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void signInStillAsksOnlyForProfileAndEmail() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(header().string("Location", allOf(
                        not(containsString("calendar")), not(containsString("login_hint")))));
    }
}
