package com.codewalnut.ats.controller;

import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.GoogleCalendarClient;
import com.codewalnut.ats.dto.InterviewDtos.CalendarStatusResponse;
import com.codewalnut.ats.dto.InterviewDtos.CancelInterviewRequest;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.dto.InterviewDtos.ScheduleInterviewRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.InterviewService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    static final String RETURN_TO = "ats.calendar.returnTo";

    private final InterviewService interviewService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/calendar/status")
    public CalendarStatusResponse calendarStatus() {
        CalendarClient.Status status = interviewService.calendarStatus(currentUserService.require());
        String redirectUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/oauth2/callback/" + GoogleCalendarClient.REGISTRATION_ID).toUriString();
        return new CalendarStatusResponse(status.available(), status.connected(), redirectUri);
    }

    /** Full-page navigation from the SPA: remember where to come back to, then go to Google. */
    @GetMapping("/api/v1/calendar/connect")
    public RedirectView connect(@RequestParam(defaultValue = "/interviews") String returnTo, HttpSession session) {
        CalendarClient.Status status = interviewService.calendarStatus(currentUserService.require());
        String target = safeReturnTo(returnTo);
        if (status.connected()) {
            return new RedirectView(target, true);
        }
        session.setAttribute(RETURN_TO, target);
        return new RedirectView("/oauth2/authorization/" + GoogleCalendarClient.REGISTRATION_ID, true);
    }

    /**
     * Spring Security handles Google's callback (with ?code) and then redirects here without
     * it; we send the user back to the screen they came from.
     */
    @GetMapping("/oauth2/callback/" + GoogleCalendarClient.REGISTRATION_ID)
    public RedirectView connected(HttpSession session) {
        Object target = session.getAttribute(RETURN_TO);
        session.removeAttribute(RETURN_TO);
        return new RedirectView(target instanceof String s ? s : "/interviews", true);
    }

    @GetMapping("/api/v1/interviews")
    public List<InterviewResponse> upcoming() {
        return interviewService.upcoming(currentUserService.require());
    }

    @GetMapping("/api/v1/applications/{id}/interviews")
    public List<InterviewResponse> forApplication(@PathVariable UUID id) {
        return interviewService.forApplication(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/applications/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.schedule(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/interviews/{id}/cancel")
    public InterviewResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelInterviewRequest request) {
        return interviewService.cancel(currentUserService.require(), id, request == null ? null : request.reason());
    }

    /** Only same-site paths, so the redirect can't be pointed at another site. */
    static String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.length() > 500 || !returnTo.startsWith("/") || returnTo.startsWith("//")
                || returnTo.contains("\\") || returnTo.chars().anyMatch(Character::isISOControl)) {
            return "/interviews";
        }
        return returnTo;
    }
}
