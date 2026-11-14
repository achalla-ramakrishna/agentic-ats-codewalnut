package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AiReviewDtos.Queue;
import com.codewalnut.ats.dto.AiReviewDtos.ReviewPage;
import com.codewalnut.ats.dto.AiReviewDtos.VerdictRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AiReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reviewers' verdicts on completed AI interviews (AINT-21). */
@RestController
@RequestMapping("/api/v1/ai-reviews")
@RequiredArgsConstructor
public class AiReviewController {

    private final AiReviewService reviews;
    private final CurrentUserService currentUserService;

    @GetMapping
    public Queue queue() {
        return reviews.queue(currentUserService.require());
    }

    @GetMapping("/{inviteId}")
    public ReviewPage page(@PathVariable UUID inviteId) {
        return reviews.page(currentUserService.require(), inviteId);
    }

    /** Save (or change) my verdict. */
    @PutMapping("/{inviteId}")
    public ReviewPage decide(@PathVariable UUID inviteId, @Valid @RequestBody VerdictRequest request) {
        return reviews.decide(currentUserService.require(), inviteId, request);
    }
}
