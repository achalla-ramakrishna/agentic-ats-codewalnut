package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AuditLogResponse;
import com.codewalnut.ats.dto.PageResponse;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditService auditService;
    private final AccessPolicy accessPolicy;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/audit-log")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        accessPolicy.require(currentUserService.require(), Capability.VIEW_AUDIT_LOG);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(auditService.list(request), AuditLogResponse::from);
    }
}
