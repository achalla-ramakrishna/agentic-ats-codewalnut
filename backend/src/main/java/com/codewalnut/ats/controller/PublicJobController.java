package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.TrackerDtos.PublicJobResponse;
import com.codewalnut.ats.service.PublicJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, no sign-in: the job page behind a shared link. */
@RestController
@RequiredArgsConstructor
public class PublicJobController {

    private final PublicJobService publicJobService;

    @GetMapping("/api/v1/public/jobs/{slug}")
    public PublicJobResponse job(@PathVariable String slug) {
        return publicJobService.publicJob(slug);
    }
}
