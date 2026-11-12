package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AiInterviewDtos.CreateInterviewRequest;
import com.codewalnut.ats.dto.AiInterviewDtos.CreateInterviewResult;
import com.codewalnut.ats.dto.AiInterviewDtos.JobInterviews;
import com.codewalnut.ats.dto.AiInterviewDtos.VideoSaved;
import com.codewalnut.ats.security.CurrentCandidateService;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AiInterviewService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** AI interviews (ADR-0030): build one for an opening, video answers, the ranking and the recorded videos. */
@RestController
@RequiredArgsConstructor
public class AiInterviewController {

    private final AiInterviewService interviews;
    private final CurrentUserService currentUserService;
    private final CurrentCandidateService currentCandidateService;

    @PostMapping("/api/v1/ai-interviews")
    public CreateInterviewResult create(@Valid @RequestBody CreateInterviewRequest request) {
        return interviews.create(currentUserService.require(), request);
    }

    @GetMapping("/api/v1/jobs/{id}/ai-interviews")
    public JobInterviews ranking(@PathVariable UUID id) {
        return interviews.ranking(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/tests/{id}/review-again")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewAgain(@PathVariable UUID id) {
        interviews.reviewAgain(currentUserService.require(), id);
    }

    /** Let the candidate finish: reopens a submitted interview for the given minutes, keeping saved answers. */
    @PostMapping("/api/v1/tests/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable UUID id, @RequestParam(value = "minutes", defaultValue = "60") int minutes) {
        interviews.reopen(currentUserService.require(), id, minutes);
    }

    /** A recorded answer; supports byte ranges so the player can seek. */
    @GetMapping("/api/v1/tests/{id}/videos/{videoId}")
    public ResponseEntity<Resource> video(@PathVariable UUID id, @PathVariable UUID videoId) {
        AiInterviewService.VideoFile file = interviews.video(currentUserService.require(), id, videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(file.resource());
    }

    @PostMapping("/api/v1/candidate/tests/{id}/questions/{questionId}/video")
    public VideoSaved upload(@PathVariable UUID id, @PathVariable UUID questionId, @RequestPart("file") MultipartFile file,
            @RequestParam(value = "transcript", required = false) String transcript, @RequestParam("seconds") int seconds) throws IOException {
        return interviews.upload(currentCandidateService.require(), id, questionId, file, transcript, seconds);
    }
}
