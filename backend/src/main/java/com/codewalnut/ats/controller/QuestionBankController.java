package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.BankQuestion;
import com.codewalnut.ats.dto.AssessmentDtos.AssessmentDetail;
import com.codewalnut.ats.dto.BankDtos.AddFromBankRequest;
import com.codewalnut.ats.dto.BankDtos.BankDraftRequest;
import com.codewalnut.ats.dto.BankDtos.BankDraftResult;
import com.codewalnut.ats.dto.BankDtos.BankOverview;
import com.codewalnut.ats.dto.BankDtos.BankPage;
import com.codewalnut.ats.dto.BankDtos.BankQuestionRequest;
import com.codewalnut.ats.dto.BankDtos.BankQuestionView;
import com.codewalnut.ats.dto.BankDtos.BuildRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.QuestionBankService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The question bank and the test-paper builder (ADR-0014). */
@RestController
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService service;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/question-bank/overview")
    public BankOverview overview(@RequestParam(required = false) Assessment.Category area) {
        return service.overview(currentUserService.require(), area);
    }

    @GetMapping("/api/v1/question-bank")
    public BankPage list(@RequestParam(required = false) Assessment.Category area,
            @RequestParam(required = false) BankQuestion.Section section,
            @RequestParam(required = false) BankQuestion.Difficulty difficulty,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "false") boolean pictures,
            @RequestParam(required = false) BankQuestion.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(currentUserService.require(), area, section, difficulty, topic, pictures, status, q, page,
                Math.max(1, Math.min(100, size)));
    }

    @PostMapping("/api/v1/question-bank")
    @ResponseStatus(HttpStatus.CREATED)
    public BankQuestionView create(@Valid @RequestBody BankQuestionRequest request) {
        return service.create(currentUserService.require(), request);
    }

    @PutMapping("/api/v1/question-bank/{id}")
    public BankQuestionView update(@PathVariable UUID id, @Valid @RequestBody BankQuestionRequest request) {
        return service.update(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/question-bank/{id}/approve")
    public BankQuestionView approve(@PathVariable UUID id) {
        return service.setStatus(currentUserService.require(), id, BankQuestion.Status.ACTIVE);
    }

    @PostMapping("/api/v1/question-bank/{id}/archive")
    public BankQuestionView archive(@PathVariable UUID id) {
        return service.setStatus(currentUserService.require(), id, BankQuestion.Status.ARCHIVED);
    }

    @PostMapping("/api/v1/question-bank/draft")
    public BankDraftResult draft(@Valid @RequestBody BankDraftRequest request) {
        return service.draft(currentUserService.require(), request);
    }

    @PostMapping("/api/v1/question-bank/build")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDetail build(@Valid @RequestBody BuildRequest request) {
        return service.build(currentUserService.require(), request);
    }

    @PostMapping("/api/v1/assessments/{id}/from-bank")
    public AssessmentDetail addToTest(@PathVariable UUID id, @Valid @RequestBody AddFromBankRequest request) {
        return service.addToTest(currentUserService.require(), id, request);
    }
}
