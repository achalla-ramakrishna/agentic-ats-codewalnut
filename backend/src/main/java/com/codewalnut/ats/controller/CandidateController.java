package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.dto.CandidateMeResponse;
import com.codewalnut.ats.security.CurrentCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Candidate-facing API. Everything under /api/v1/candidate is for candidate sessions only. */
@RestController
@RequiredArgsConstructor
public class CandidateController {

    private final CurrentCandidateService currentCandidateService;

    @GetMapping("/api/v1/candidate/me")
    public CandidateMeResponse me() {
        CandidateAccount account = currentCandidateService.require();
        return new CandidateMeResponse(account.getId(), account.getEmail(), account.getName());
    }
}
