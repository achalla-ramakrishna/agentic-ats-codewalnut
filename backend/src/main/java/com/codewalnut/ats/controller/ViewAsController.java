package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.ViewAsDtos.Info;
import com.codewalnut.ats.dto.ViewAsDtos.Options;
import com.codewalnut.ats.dto.ViewAsDtos.StartRequest;
import com.codewalnut.ats.service.ViewAsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "View as" for admins (ADR-0013). */
@RestController
@RequiredArgsConstructor
public class ViewAsController {

    private final ViewAsService viewAsService;

    @GetMapping("/api/v1/admin/view-as/options")
    public Options options(@RequestParam(required = false) String q) {
        return viewAsService.options(q);
    }

    @PostMapping("/api/v1/admin/view-as")
    public Info start(@Valid @RequestBody StartRequest request, HttpServletRequest http) {
        return viewAsService.start(http.getSession(true), request);
    }

    @PostMapping("/api/v1/admin/view-as/stop")
    public ResponseEntity<Void> stop(HttpServletRequest http) {
        var session = http.getSession(false);
        if (session != null) {
            viewAsService.stop(session);
        }
        return ResponseEntity.noContent().build();
    }
}
