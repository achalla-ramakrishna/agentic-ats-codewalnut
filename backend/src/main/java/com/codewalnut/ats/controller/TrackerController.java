package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.TrackerDtos.AddCandidateRequest;
import com.codewalnut.ats.dto.TrackerDtos.AddToOpeningRequest;
import com.codewalnut.ats.dto.TrackerDtos.CandidateOpening;
import com.codewalnut.ats.dto.TrackerDtos.ApplicationResponse;
import com.codewalnut.ats.dto.ProfileDtos.CandidateProfile;
import com.codewalnut.ats.dto.ProfileDtos.UpdateProfileRequest;
import com.codewalnut.ats.service.CandidateProfileService;
import com.codewalnut.ats.dto.TrackerDtos.ClientResponse;
import com.codewalnut.ats.dto.TrackerDtos.CreateClientRequest;
import com.codewalnut.ats.dto.TrackerDtos.CreateJobRequest;
import com.codewalnut.ats.dto.TrackerDtos.DashboardResponse;
import com.codewalnut.ats.dto.TrackerDtos.EventResponse;
import com.codewalnut.ats.dto.TrackerDtos.ImportRequest;
import com.codewalnut.ats.dto.TrackerDtos.ImportResult;
import com.codewalnut.ats.dto.TrackerDtos.JobResponse;
import com.codewalnut.ats.dto.TrackerDtos.MoveStageRequest;
import com.codewalnut.ats.dto.TrackerDtos.NoteRequest;
import com.codewalnut.ats.dto.TrackerDtos.StageOption;
import com.codewalnut.ats.dto.TrackerDtos.UpdateJobRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.TrackerService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrackerController {

    private final TrackerService trackerService;
    private final CandidateProfileService candidateProfileService;
    private final CurrentUserService currentUserService;

    @GetMapping("/stages")
    public List<StageOption> stages() {
        currentUserService.require();
        return Arrays.stream(Stage.values()).map(s -> new StageOption(s, s.getLabel(), s.isExit())).toList();
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return trackerService.dashboard(currentUserService.require());
    }

    @GetMapping("/clients")
    public List<ClientResponse> clients() {
        return trackerService.listClients(currentUserService.require()).stream().map(ClientResponse::from).toList();
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse createClient(@Valid @RequestBody CreateClientRequest request) {
        return ClientResponse.from(trackerService.createClient(currentUserService.require(), request));
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs() {
        return trackerService.listJobs(currentUserService.require());
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        return trackerService.createJob(currentUserService.require(), request);
    }

    @GetMapping("/jobs/{id}")
    public JobResponse job(@PathVariable UUID id) {
        return trackerService.getJob(currentUserService.require(), id);
    }

    @PatchMapping("/jobs/{id}")
    public JobResponse updateJob(@PathVariable UUID id, @Valid @RequestBody UpdateJobRequest request) {
        return trackerService.updateJob(currentUserService.require(), id, request);
    }

    @GetMapping("/jobs/{id}/applications")
    public List<ApplicationResponse> applications(@PathVariable UUID id) {
        return trackerService.listApplications(currentUserService.require(), id);
    }

    @PostMapping("/jobs/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse addCandidate(@PathVariable UUID id, @Valid @RequestBody AddCandidateRequest request) {
        return trackerService.addCandidate(currentUserService.require(), id, request);
    }

    @PostMapping("/jobs/{id}/applications/import")
    public ImportResult importCandidates(@PathVariable UUID id, @Valid @RequestBody ImportRequest request) {
        return trackerService.importCandidates(currentUserService.require(), id, request);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> search(
            @RequestParam(required = false) String q, @RequestParam(required = false) Stage stage) {
        return trackerService.searchApplications(currentUserService.require(), q, stage);
    }

    @PatchMapping("/applications/{id}/stage")
    public ApplicationResponse moveStage(@PathVariable UUID id, @Valid @RequestBody MoveStageRequest request) {
        return trackerService.moveStage(currentUserService.require(), id, request);
    }

    @GetMapping("/candidates/{id}/profile")
    public CandidateProfile profile(@PathVariable UUID id) {
        return candidateProfileService.profile(currentUserService.require(), id);
    }

    @PatchMapping("/candidates/{id}")
    public CandidateProfile updateCandidate(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request) {
        return candidateProfileService.update(currentUserService.require(), id, request);
    }

    @GetMapping("/applications/{id}/openings")
    public List<CandidateOpening> openings(@PathVariable UUID id) {
        return trackerService.openings(currentUserService.require(), id);
    }

    @PostMapping("/applications/{id}/openings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse addToOpening(@PathVariable UUID id, @Valid @RequestBody AddToOpeningRequest request) {
        return trackerService.addToOpening(currentUserService.require(), id, request);
    }

    @PostMapping("/applications/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return trackerService.addNote(currentUserService.require(), id, request.text());
    }

    @GetMapping("/applications/{id}/history")
    public List<EventResponse> history(@PathVariable UUID id) {
        return trackerService.history(currentUserService.require(), id);
    }
}
