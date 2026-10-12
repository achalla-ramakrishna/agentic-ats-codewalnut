package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** PIPE-24: slots → personal links → candidates book → one click sends the invites. Fake people only. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowTest {

    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor INTERVIEWER = user("interviewer@codewalnut.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    private String call(MockHttpServletRequestBuilder request, RequestPostProcessor who, Object body, int expected) throws Exception {
        if (who != null) {
            request = request.with(who);
        }
        request = request.with(csrf());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidatesBookSlotsAndTheRecruiterSendsAllInvitesAtOnce() throws Exception {
        String job = JsonPath.read(call(post("/api/v1/jobs"), ADMIN, Map.of("title", "Senior Java " + tag, "hiringType", "INTERNAL"), 201), "$.id");
        String asha = JsonPath.read(call(post("/api/v1/jobs/" + job + "/applications"), ADMIN,
                Map.of("name", "Asha Test", "email", "asha." + tag + "@example.com", "stage", "SOURCED"), 201), "$.id");
        String ravi = JsonPath.read(call(post("/api/v1/jobs/" + job + "/applications"), ADMIN,
                Map.of("name", "Ravi Test", "email", "ravi." + tag + "@example.com", "stage", "SOURCED"), 201), "$.id");

        // Two 30-minute slots, three days from now, 10:00–11:00 India time.
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3);
        Map<String, Object> slotsRequest = Map.of("from", day.toString(), "to", day.toString(), "weekdaysOnly", false,
                "windows", List.of(Map.of("start", "10:00", "end", "11:00")), "durationMinutes", 30, "timeZone", "Asia/Kolkata",
                "interviewerEmails", List.of("interviewer@codewalnut.test"));
        call(post("/api/v1/jobs/" + job + "/slots"), INTERVIEWER, slotsRequest, 403);
        String created = call(post("/api/v1/jobs/" + job + "/slots"), ADMIN, slotsRequest, 200);
        assertThat((Integer) JsonPath.read(created, "$.created")).isEqualTo(2);
        // Asking again doesn't duplicate.
        assertThat((Integer) JsonPath.read(call(post("/api/v1/jobs/" + job + "/slots"), ADMIN, slotsRequest, 200), "$.skipped")).isEqualTo(2);
        List<String> slotIds = JsonPath.read(created, "$.slots.slots[*].id");

        String links = call(post("/api/v1/booking-links"), ADMIN, Map.of("applicationIds", List.of(asha, ravi)), 200);
        String ashaLink = JsonPath.read(links, "$.links['" + asha + "']");
        String raviLink = JsonPath.read(links, "$.links['" + ravi + "']");
        assertThat(ashaLink).startsWith("http://localhost/book/");
        String ashaToken = ashaLink.substring(ashaLink.lastIndexOf('/') + 1);
        String raviToken = raviLink.substring(raviLink.lastIndexOf('/') + 1);
        // The same link every time.
        assertThat((String) JsonPath.read(call(post("/api/v1/booking-links"), ADMIN, Map.of("applicationIds", List.of(asha)), 200),
                "$.links['" + asha + "']")).isEqualTo(ashaLink);

        // The candidate's page: no sign-in.
        String page = call(get("/api/v1/public/booking/" + ashaToken), null, null, 200);
        assertThat((String) JsonPath.read(page, "$.firstName")).isEqualTo("Asha");
        assertThat((List<Object>) JsonPath.read(page, "$.slots")).hasSize(2);
        call(get("/api/v1/public/booking/not-a-real-token"), null, null, 404);

        call(post("/api/v1/public/booking/" + ashaToken), null, Map.of("slotId", slotIds.get(0)), 200);
        String taken = call(post("/api/v1/public/booking/" + raviToken), null, Map.of("slotId", slotIds.get(0)), 400);
        assertThat(taken).contains("Someone just booked that time");
        // Asha changes her mind: her first slot is freed.
        String moved = call(post("/api/v1/public/booking/" + ashaToken), null, Map.of("slotId", slotIds.get(1)), 200);
        assertThat((String) JsonPath.read(moved, "$.booked.id")).isEqualTo(slotIds.get(1));
        call(post("/api/v1/public/booking/" + raviToken), null, Map.of("slotId", slotIds.get(0)), 200);
        call(delete("/api/v1/slots/" + slotIds.get(0)), ADMIN, null, 400);

        // The workflow shows both waiting for an invite.
        String board = call(get("/api/v1/workflow").param("jobId", job), ADMIN, null, 200);
        assertThat((List<String>) JsonPath.read(board, "$.rows[*].nextStep.code")).containsOnly("SEND_INVITE");

        String sent = call(post("/api/v1/jobs/" + job + "/slots/send-invites"), ADMIN, null, 200);
        assertThat((Integer) JsonPath.read(sent, "$.scheduled")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(call(get("/api/v1/jobs/" + job + "/slots"), ADMIN, null, 200), "$.scheduled")).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(call(get("/api/v1/public/booking/" + ashaToken), null, null, 200), "$.scheduled")).isTrue();
        assertThat(call(post("/api/v1/public/booking/" + ashaToken), null, Map.of("slotId", slotIds.get(0)), 400))
                .contains("already scheduled");
        board = call(get("/api/v1/workflow").param("jobId", job), ADMIN, null, 200);
        assertThat((List<Boolean>) JsonPath.read(board, "$.rows[*].booking.scheduled")).containsOnly(true);
        // Nothing left to send.
        assertThat((Integer) JsonPath.read(call(post("/api/v1/jobs/" + job + "/slots/send-invites"), ADMIN, null, 200), "$.scheduled")).isZero();
    }

    @Test
    void noLinkWithoutFreeSlots() throws Exception {
        String job = JsonPath.read(call(post("/api/v1/jobs"), ADMIN, Map.of("title", "No slots " + tag, "hiringType", "INTERNAL"), 201), "$.id");
        String app = JsonPath.read(call(post("/api/v1/jobs/" + job + "/applications"), ADMIN,
                Map.of("name", "Meena Test", "email", "meena." + tag + "@example.com", "stage", "SOURCED"), 201), "$.id");
        String links = call(post("/api/v1/booking-links"), ADMIN, Map.of("applicationIds", List.of(app)), 200);
        assertThat((String) JsonPath.read(links, "$.problems['" + app + "']")).contains("add slots first");
    }
}
