package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.dto.ProfileDtos.CandidateProfile;
import com.codewalnut.ats.dto.ProfileDtos.MyDocument;
import com.codewalnut.ats.dto.ProfileDtos.MyDocumentsResponse;
import com.codewalnut.ats.dto.ProfileDtos.UpdateProfileRequest;
import com.codewalnut.ats.service.CandidateProfileService;
import com.codewalnut.ats.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.codewalnut.ats.dto.CandidateMeResponse;
import com.codewalnut.ats.security.CurrentCandidateService;
import com.codewalnut.ats.dto.InterviewDtos.CandidateInterviewResponse;
import com.codewalnut.ats.dto.TrackerDtos.MyApplicationResponse;
import com.codewalnut.ats.service.InterviewService;
import com.codewalnut.ats.service.PublicJobService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Candidate-facing API. Everything under /api/v1/candidate is for candidate sessions only. */
@RestController
@RequiredArgsConstructor
public class CandidateController {

    private final CurrentCandidateService currentCandidateService;
    private final PublicJobService publicJobService;
    private final InterviewService interviewService;
    private final CandidateProfileService candidateProfileService;
    private final DocumentService documentService;

    @GetMapping("/api/v1/candidate/applications")
    public List<MyApplicationResponse> myApplications() {
        return publicJobService.myApplications(currentCandidateService.require());
    }

    @PostMapping(path = "/api/v1/candidate/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MyApplicationResponse apply(
            @RequestParam String slug,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam(required = false) String note,
            @RequestParam(defaultValue = "false") boolean consent,
            @RequestParam("resume") MultipartFile resume) throws IOException {
        return publicJobService.apply(currentCandidateService.require(), slug, name, phone, note, consent, resume);
    }

    @GetMapping("/api/v1/candidate/interviews")
    public List<CandidateInterviewResponse> myInterviews() {
        return interviewService.forCandidateEmail(currentCandidateService.require().getEmail());
    }

    @GetMapping("/api/v1/candidate/profile")
    public CandidateProfile myProfile() {
        return candidateProfileService.myProfile(currentCandidateService.require());
    }

    @PatchMapping("/api/v1/candidate/profile")
    public CandidateProfile updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return candidateProfileService.updateMine(currentCandidateService.require(), request);
    }

    @GetMapping("/api/v1/candidate/documents")
    public MyDocumentsResponse myDocuments() {
        return documentService.myDocuments(currentCandidateService.require());
    }

    @PostMapping(path = "/api/v1/candidate/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MyDocument uploadMine(@RequestParam DocumentKind kind, @RequestParam("file") MultipartFile file)
            throws IOException {
        return documentService.candidateUpload(currentCandidateService.require(), kind, file);
    }

    @GetMapping("/api/v1/candidate/me")
    public CandidateMeResponse me() {
        CandidateAccount account = currentCandidateService.require();
        return new CandidateMeResponse(account.getId(), account.getEmail(), account.getName());
    }
}
