package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.CodeWalnutResumeDtos.DraftResponse;
import com.codewalnut.ats.dto.CodeWalnutResumeDtos.SavedResponse;
import com.codewalnut.ats.dto.CodeWalnutResumeDtos.UpdateRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.CodeWalnutResumeService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** CodeWalnut-branded résumés: AI draft, edits, PDF/Word, save to documents (ADR-0012). */
@RestController
@RequiredArgsConstructor
public class CodeWalnutResumeController {

    private final CodeWalnutResumeService service;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/applications/{id}/codewalnut-resume")
    public DraftResponse get(@PathVariable UUID id) {
        return service.get(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/applications/{id}/codewalnut-resume/generate")
    public DraftResponse generate(@PathVariable UUID id) {
        return service.generate(currentUserService.require(), id);
    }

    @PutMapping("/api/v1/applications/{id}/codewalnut-resume")
    public DraftResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/applications/{id}/codewalnut-resume/save")
    public SavedResponse save(@PathVariable UUID id) {
        return service.save(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/applications/{id}/codewalnut-resume.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.pdf(currentUserService.require(), id);
        return file(bytes, service.fileNameFor(id) + ".pdf", MediaType.APPLICATION_PDF, true);
    }

    @GetMapping("/api/v1/applications/{id}/codewalnut-resume.docx")
    public ResponseEntity<byte[]> docx(@PathVariable UUID id) {
        byte[] bytes = service.docx(currentUserService.require(), id);
        return file(bytes, service.fileNameFor(id) + ".docx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), false);
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, String name, MediaType type, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
