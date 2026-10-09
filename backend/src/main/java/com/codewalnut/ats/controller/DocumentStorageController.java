package com.codewalnut.ats.controller;

import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.DocumentStorageOperations;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Disabled-by-default operator API; session/CSRF, admin permission and ViewAs filter all apply. */
@RestController
@RequestMapping("/api/v1/admin/document-storage")
@RequiredArgsConstructor
public class DocumentStorageController {
    private final DocumentStorageOperations operations;
    private final CurrentUserService users;

    @GetMapping
    public Map<String, Long> status() { return operations.status(users.require()); }

    @PostMapping("/migrate")
    public Map<String, Integer> migrate(@RequestParam(defaultValue = "10") int limit) {
        return operations.enqueue(users.require(), limit);
    }

    @PostMapping("/retry")
    public Map<String, Integer> retry() { return operations.retry(users.require()); }
}
