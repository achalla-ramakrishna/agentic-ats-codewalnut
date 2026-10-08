package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.DocumentDownload;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.dto.ProfileDtos.DocumentKindOption;
import com.codewalnut.ats.dto.ProfileDtos.DocumentRequestResponse;
import com.codewalnut.ats.dto.ProfileDtos.RequestDocumentsRequest;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.DocumentService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserService currentUserService;

    @GetMapping("/candidates/{id}/documents")
    public List<CandidateDocumentRepository.Info> list(@PathVariable UUID id) {
        return documentService.list(currentUserService.require(), id);
    }

    @PostMapping(path = "/candidates/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDocumentRepository.Info upload(
            @PathVariable UUID id, @RequestParam DocumentKind kind, @RequestParam("file") MultipartFile file)
            throws IOException {
        return documentService.upload(currentUserService.require(), id, kind, file);
    }

    @GetMapping("/document-kinds")
    public List<DocumentKindOption> kinds() {
        return java.util.Arrays.stream(DocumentKind.values())
                .map(k -> new DocumentKindOption(k, k.getLabel(), k.isSensitive(), k.isCandidateUploadable()))
                .toList();
    }

    @GetMapping("/candidates/{id}/document-requests")
    public List<DocumentRequestResponse> requests(@PathVariable UUID id) {
        return documentService.requests(currentUserService.require(), id);
    }

    @PostMapping("/candidates/{id}/document-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DocumentRequestResponse> request(@PathVariable UUID id, @Valid @RequestBody RequestDocumentsRequest body) {
        return documentService.request(currentUserService.require(), id, body.kinds());
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline) {
        return file(documentService.download(currentUserService.require(), id), inline);
    }

    /** The file as a download (or inline view), never cached, never content-sniffed. */
    static ResponseEntity<byte[]> file(DocumentDownload document, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(document.fileName())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.data());
    }
}
