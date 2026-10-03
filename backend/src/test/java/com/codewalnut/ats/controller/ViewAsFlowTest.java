package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** "View as" (VIEW-01…): admins see the app as a candidate, client contact or staff role, read-only. */
@SpringBootTest
@AutoConfigureMockMvc
class ViewAsFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor RECRUITER = user("recruiter@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final MockHttpSession session = new MockHttpSession();

    private ResultActions post(RequestPostProcessor who, String url, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).session(session).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions get(RequestPostProcessor who, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).session(session).with(who));
    }

    private String candidateInOpening(String email) throws Exception {
        String job = JsonPath.read(post(RECRUITER, "/api/v1/jobs", "{\"title\":\"View " + tag + "\",\"hiringType\":\"INTERNAL\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        post(RECRUITER, "/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Nila Tester\",\"email\":\"" + email + "\",\"stage\":\"SCREENING\"}").andExpect(status().isCreated());
        return job;
    }

    @Test
    void adminSeesTheCandidateViewReadOnlyAndComesBack() throws Exception {
        String email = "nila." + tag + "@gmail.com";
        candidateInOpening(email);

        get(ADMIN, "/api/v1/admin/view-as/options?q=nila." + tag)
                .andExpect(jsonPath("$.candidates[0].email").value(email))
                .andExpect(jsonPath("$.candidates[0].openings[0]").value("View " + tag))
                .andExpect(jsonPath("$.roles[*].role", Matchers.not(Matchers.hasItem("ADMIN"))));

        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"CANDIDATE\",\"email\":\"" + email + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Nila Tester"));

        get(ADMIN, "/api/v1/auth/session")
                .andExpect(jsonPath("$.type").value("CANDIDATE"))
                .andExpect(jsonPath("$.viewAs.kind").value("CANDIDATE"))
                .andExpect(jsonPath("$.viewAs.label").value("Nila Tester"));
        String apps = get(ADMIN, "/api/v1/candidate/applications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobTitle").value("View " + tag))
                .andReturn().getResponse().getContentAsString();
        String appId = JsonPath.read(apps, "$[0].id");
        get(ADMIN, "/api/v1/me").andExpect(status().isForbidden());

        // Read-only: nothing is sent in their name.
        post(ADMIN, "/api/v1/candidate/applications/" + appId + "/messages", "{\"body\":\"hello\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("read-only")));
        get(ADMIN, "/api/v1/candidate/applications/" + appId + "/messages").andExpect(status().isOk());

        post(ADMIN, "/api/v1/admin/view-as/stop", "").andExpect(status().isNoContent());
        get(ADMIN, "/api/v1/auth/session")
                .andExpect(jsonPath("$.type").value(Matchers.not("CANDIDATE")))
                .andExpect(jsonPath("$.viewAs").doesNotExist());
        get(ADMIN, "/api/v1/me").andExpect(status().isOk());
        get(ADMIN, "/api/v1/audit-log?size=5")
                .andExpect(jsonPath("$.items[*].action", Matchers.hasItems("VIEW_AS_STARTED", "VIEW_AS_STOPPED")));
    }

    @Test
    void adminPreviewsAStaffRoleWithOnlyThatRolesAccess() throws Exception {
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"ROLE\",\"role\":\"HIRING_MANAGER\"}").andExpect(status().isOk());
        get(ADMIN, "/api/v1/auth/session").andExpect(jsonPath("$.type").value("STAFF"))
                .andExpect(jsonPath("$.viewAs.label").value("Hiring Manager"));
        get(ADMIN, "/api/v1/me")
                .andExpect(jsonPath("$.roles", Matchers.contains("HIRING_MANAGER")))
                .andExpect(jsonPath("$.navigation[*].key", Matchers.not(Matchers.hasItems("users", "view-as"))));
        get(ADMIN, "/api/v1/users").andExpect(status().isForbidden());
        post(ADMIN, "/api/v1/jobs", "{\"title\":\"x\",\"hiringType\":\"INTERNAL\"}").andExpect(status().isForbidden());
        // Can't start another one on top.
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"ROLE\",\"role\":\"RECRUITER\"}").andExpect(status().isForbidden());
        post(ADMIN, "/api/v1/admin/view-as/stop", "").andExpect(status().isNoContent());
        get(ADMIN, "/api/v1/me").andExpect(jsonPath("$.roles", Matchers.hasItem("ADMIN")));
    }

    @Test
    void adminSeesWhatAClientContactSees() throws Exception {
        String clientId = JsonPath.read(post(ADMIN, "/api/v1/clients", "{\"name\":\"Acme " + tag + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String contact = "hm." + tag + "@acme.example";
        post(ADMIN, "/api/v1/clients/" + clientId + "/contacts", "{\"email\":\"" + contact + "\",\"name\":\"Meera HM\"}")
                .andExpect(status().isCreated());
        get(ADMIN, "/api/v1/admin/view-as/options").andExpect(jsonPath("$.clients[*].email", Matchers.hasItem(contact)));
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"CLIENT\",\"email\":\"" + contact + "\"}")
                .andExpect(jsonPath("$.label").value("Meera HM (Acme " + tag + ")"));
        get(ADMIN, "/api/v1/auth/session").andExpect(jsonPath("$.type").value("CLIENT"));
        get(ADMIN, "/api/v1/client/me").andExpect(status().isOk());
        get(ADMIN, "/api/v1/client/candidates").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        post(ADMIN, "/api/v1/admin/view-as/stop", "").andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminsCanViewAsAndOnlyRealPeople() throws Exception {
        get(RECRUITER, "/api/v1/admin/view-as/options").andExpect(status().isForbidden());
        post(RECRUITER, "/api/v1/admin/view-as", "{\"kind\":\"ROLE\",\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"ROLE\",\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"CANDIDATE\",\"email\":\"nobody." + tag + "@gmail.com\"}")
                .andExpect(status().isNotFound());
        post(ADMIN, "/api/v1/admin/view-as", "{\"kind\":\"CLIENT\",\"email\":\"nobody." + tag + "@acme.example\"}")
                .andExpect(status().isNotFound());
    }
}
