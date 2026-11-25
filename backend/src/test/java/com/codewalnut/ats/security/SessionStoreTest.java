package com.codewalnut.ats.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sign-ins are kept in MySQL (Spring Session JDBC), so a deploy or restart doesn't sign out a candidate who is
 * in the middle of an AI interview: the session row is in the database and the cookie alone gets them back in.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@AutoConfigureMockMvc
class SessionStoreTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aSignInIsStoredInTheDatabaseAndWorksFromTheCookieAlone() throws Exception {
        String email = "session." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        Cookie cookie = mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from SPRING_SESSION where PRINCIPAL_NAME = ?", Integer.class, email)).isEqualTo(1);
        // A new request with only the cookie (as after a restart) is still signed in as the candidate.
        mockMvc.perform(get("/api/v1/auth/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CANDIDATE"));
        mockMvc.perform(get("/api/v1/candidate/tests").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/candidate/tests")).andExpect(status().isUnauthorized());
    }
}
