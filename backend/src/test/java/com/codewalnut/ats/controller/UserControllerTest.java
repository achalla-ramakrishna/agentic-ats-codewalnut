package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AppUserRepository userRepository;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@codewalnut.com";
    }

    private String createUser(String email, String rolesJson) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"Test\",\"roles\":" + rolesJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
    }

    @Test
    void nonAdminIsDeniedAndTheDenialIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(RECRUITER)).andExpect(status().isForbidden());

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.ACCESS_DENIED, "recruiter@codewalnut.test")).isNotEmpty();
    }

    @Test
    void nonAdminCannotCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(RECRUITER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAUserWithLowerCasedEmailAndItIsAudited() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"roles\":[\"RECRUITER\",\"INTERVIEWER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                        AuditAction.USER_CREATED, "admin@codewalnut.test"))
                .anyMatch(entry -> entry.getDetails().contains(email));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        String email = uniqueEmail();
        createUser(email, "[\"RECRUITER\"]");
        mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"roles\":[\"RECRUITER\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void emailOutsideAllowedDomainsIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@gmail.com\",\"roles\":[\"RECRUITER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userNeedsAtLeastOneRole() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"roles\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"roles\":[\"SUPERUSER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleChangeTakesEffectOnTheNextRequest() throws Exception {
        String email = uniqueEmail();
        String id = createUser(email, "[\"INTERVIEWER\"]");

        mockMvc.perform(get("/api/v1/me").with(user(email)))
                .andExpect(jsonPath("$.navigation.length()").value(2));

        mockMvc.perform(patch("/api/v1/users/" + id).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"RECRUITER\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").with(user(email)))
                .andExpect(jsonPath("$.roles[0]").value("RECRUITER"))
                .andExpect(jsonPath("$.navigation[1].key").value("jobs"));

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                        AuditAction.USER_UPDATED, "admin@codewalnut.test"))
                .anyMatch(entry -> id.equals(entry.getEntityId()));
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String email = uniqueEmail();
        String id = createUser(email, "[\"RECRUITER\"]");
        mockMvc.perform(get("/api/v1/me").with(user(email))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + id).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/me").with(user(email))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        String adminId = userRepository.findByEmail("admin@codewalnut.test").orElseThrow().getId().toString();

        mockMvc.perform(patch("/api/v1/users/" + adminId).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"RECRUITER\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/users/" + adminId).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingAnUnknownUserIs404() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + UUID.randomUUID()).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInButUnprovisionedUserIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(user("ghost@codewalnut.com")))
                .andExpect(status().isUnauthorized());
    }
}
