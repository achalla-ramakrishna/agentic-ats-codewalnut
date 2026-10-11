package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.NaukriDtos.ImportRequest;
import com.codewalnut.ats.dto.NaukriDtos.ImportResult;
import com.codewalnut.ats.dto.NaukriDtos.RankResult;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.NaukriImportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Naukri applicant exports: rank against the opening, then add the chosen people (ADR-0026). */
@RestController
@RequiredArgsConstructor
public class NaukriController {

    private final NaukriImportService service;
    private final CurrentUserService currentUserService;

    @PostMapping(value = "/api/v1/jobs/{id}/naukri/rank", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RankResult rank(@PathVariable UUID id, @RequestPart("file") MultipartFile file) throws IOException {
        return service.rank(currentUserService.require(), id, file);
    }

    @PostMapping("/api/v1/jobs/{id}/naukri/import")
    public ImportResult importRows(@PathVariable UUID id, @Valid @RequestBody ImportRequest request) {
        return service.importRows(currentUserService.require(), id, request);
    }
}
