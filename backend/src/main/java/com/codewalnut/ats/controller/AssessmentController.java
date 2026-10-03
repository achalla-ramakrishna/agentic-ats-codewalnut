package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AssessmentDtos.AssessmentDetail;
import com.codewalnut.ats.dto.AssessmentDtos.AssessmentSummary;
import com.codewalnut.ats.dto.AssessmentDtos.CreateAssessmentRequest;
import com.codewalnut.ats.dto.AssessmentDtos.DraftRequest;
import com.codewalnut.ats.dto.AssessmentDtos.DraftResult;
import com.codewalnut.ats.dto.AssessmentDtos.InviteDetail;
import com.codewalnut.ats.dto.AssessmentDtos.InviteView;
import com.codewalnut.ats.dto.AssessmentDtos.MyTest;
import com.codewalnut.ats.dto.AssessmentDtos.QuestionRequest;
import com.codewalnut.ats.dto.AssessmentDtos.RemindRequest;
import com.codewalnut.ats.dto.AssessmentDtos.SaveAnswersRequest;
import com.codewalnut.ats.dto.AssessmentDtos.SendResult;
import com.codewalnut.ats.dto.AssessmentDtos.SendTestRequest;
import com.codewalnut.ats.dto.AssessmentDtos.TakeTest;
import com.codewalnut.ats.dto.AssessmentDtos.UpdateAssessmentRequest;
import com.codewalnut.ats.security.CurrentCandidateService;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AssessmentInviteService;
import com.codewalnut.ats.service.AssessmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Online tests: the library, sending and results (staff), and taking them (candidates). ADR-0011. */
@RestController
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final AssessmentInviteService inviteService;
    private final CurrentUserService currentUserService;
    private final CurrentCandidateService currentCandidateService;

    // ---- library ----

    @GetMapping("/api/v1/assessments")
    public List<AssessmentSummary> list() {
        return assessmentService.list(currentUserService.require());
    }

    @PostMapping("/api/v1/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDetail create(@Valid @RequestBody CreateAssessmentRequest request) {
        return assessmentService.create(currentUserService.require(), request);
    }

    @GetMapping("/api/v1/assessments/{id}")
    public AssessmentDetail get(@PathVariable UUID id) {
        return assessmentService.get(currentUserService.require(), id);
    }

    @PatchMapping("/api/v1/assessments/{id}")
    public AssessmentDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateAssessmentRequest request) {
        return assessmentService.update(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/assessments/{id}/questions")
    public AssessmentDetail addQuestion(@PathVariable UUID id, @Valid @RequestBody QuestionRequest request) {
        return assessmentService.addQuestion(currentUserService.require(), id, request);
    }

    @PutMapping("/api/v1/assessments/{id}/questions/{questionId}")
    public AssessmentDetail updateQuestion(@PathVariable UUID id, @PathVariable UUID questionId,
            @Valid @RequestBody QuestionRequest request) {
        return assessmentService.updateQuestion(currentUserService.require(), id, questionId, request);
    }

    @DeleteMapping("/api/v1/assessments/{id}/questions/{questionId}")
    public AssessmentDetail deleteQuestion(@PathVariable UUID id, @PathVariable UUID questionId) {
        return assessmentService.deleteQuestion(currentUserService.require(), id, questionId);
    }

    @PostMapping("/api/v1/assessments/{id}/draft")
    public DraftResult draft(@PathVariable UUID id, @Valid @RequestBody DraftRequest request) {
        return assessmentService.draft(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/assessments/{id}/publish")
    public AssessmentDetail publish(@PathVariable UUID id) {
        return assessmentService.publish(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/assessments/{id}/unpublish")
    public AssessmentDetail unpublish(@PathVariable UUID id) {
        return assessmentService.unpublish(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/assessments/{id}/archive")
    public AssessmentDetail archive(@PathVariable UUID id) {
        return assessmentService.archive(currentUserService.require(), id);
    }

    @DeleteMapping("/api/v1/assessments/{id}")
    public com.codewalnut.ats.dto.AssessmentDtos.DeleteResult delete(@PathVariable UUID id) {
        return assessmentService.delete(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/assessments/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDetail duplicate(@PathVariable UUID id) {
        return assessmentService.duplicate(currentUserService.require(), id);
    }

    // ---- sending and results ----

    @PostMapping("/api/v1/applications/{id}/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public SendResult send(@PathVariable UUID id, @Valid @RequestBody SendTestRequest request) {
        return inviteService.send(currentUserService.require(), id, request, baseUrl());
    }

    @GetMapping("/api/v1/applications/{id}/tests")
    public List<InviteView> forApplication(@PathVariable UUID id) {
        return inviteService.forApplication(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/jobs/{id}/tests")
    public List<InviteView> forJob(@PathVariable UUID id) {
        return inviteService.forJob(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/tests/{id}")
    public InviteDetail detail(@PathVariable UUID id) {
        return inviteService.detail(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/tests/{id}/remind")
    public SendResult remind(@PathVariable UUID id, @RequestBody RemindRequest request) {
        return inviteService.remind(currentUserService.require(), id, request, baseUrl());
    }

    @PostMapping("/api/v1/tests/{id}/cancel")
    public InviteView cancel(@PathVariable UUID id) {
        return inviteService.cancel(currentUserService.require(), id);
    }

    // ---- candidate ----

    @GetMapping("/api/v1/candidate/tests")
    public List<MyTest> myTests() {
        return inviteService.myTests(currentCandidateService.require());
    }

    @PostMapping("/api/v1/candidate/tests/{id}/start")
    public TakeTest start(@PathVariable UUID id) {
        return inviteService.start(currentCandidateService.require(), id);
    }

    @GetMapping("/api/v1/candidate/tests/{id}")
    public TakeTest resume(@PathVariable UUID id) {
        return inviteService.resume(currentCandidateService.require(), id);
    }

    @PutMapping("/api/v1/candidate/tests/{id}/answers")
    public TakeTest save(@PathVariable UUID id, @Valid @RequestBody SaveAnswersRequest request) {
        return inviteService.save(currentCandidateService.require(), id, request.answers());
    }

    @PostMapping("/api/v1/candidate/tests/{id}/submit")
    public MyTest submit(@PathVariable UUID id, @Valid @RequestBody SaveAnswersRequest request) {
        return inviteService.submit(currentCandidateService.require(), id, request.answers());
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString();
    }
}
