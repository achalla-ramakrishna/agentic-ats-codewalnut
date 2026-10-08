package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.DocumentStorageCleanupDtos.Request;
import com.codewalnut.ats.dto.DocumentStorageCleanupDtos.Result;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.DocumentStorageCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentStorageCleanupController {
    private final CurrentUserService users;
    private final DocumentStorageCleanupService cleanup;

    @PostMapping("/api/v1/admin/document-storage/cleanup")
    public Result cleanup(@RequestBody Request request) { return cleanup.cleanup(users.require(), request); }
}
