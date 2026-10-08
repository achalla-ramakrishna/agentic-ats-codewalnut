package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.InsightDtos.AnalyzeResult;
import com.codewalnut.ats.dto.InsightDtos.Background;
import com.codewalnut.ats.dto.InsightDtos.InsightDetail;
import com.codewalnut.ats.dto.InsightDtos.InsightsResponse;
import com.codewalnut.ats.dto.InsightDtos.IntakeProgress;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.ResumeIntelligenceService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Bulk résumé upload, AI readings and suggestions for an opening (ADR-0010). Advisory only. */
@RestController
@RequiredArgsConstructor
public class ResumeIntelligenceController {

    private final ResumeIntelligenceService service;
    private final CurrentUserService currentUserService;

    @PostMapping(value = "/api/v1/jobs/{id}/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntakeProgress upload(@PathVariable UUID id, @RequestPart("files") List<MultipartFile> files)
            throws IOException {
        return service.upload(currentUserService.require(), id, files);
    }

    @GetMapping("/api/v1/jobs/{id}/resumes")
    public IntakeProgress progress(@PathVariable UUID id) {
        return service.progress(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/jobs/{id}/insights")
    public InsightsResponse insights(@PathVariable UUID id) {
        return service.insights(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/jobs/{id}/backgrounds")
    public List<Background> backgrounds(@PathVariable UUID id) {
        return service.backgrounds(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/jobs/{id}/insights/analyze")
    public AnalyzeResult analyzeAll(@PathVariable UUID id) {
        return service.analyzeAll(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/applications/{id}/insight")
    public InsightDetail insight(@PathVariable UUID id) {
        return service.insight(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/applications/{id}/insight")
    public InsightDetail reanalyze(@PathVariable UUID id) {
        return service.reanalyze(currentUserService.require(), id);
    }
}
