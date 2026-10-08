package com.codewalnut.ats.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Deliberately excludes upstream bodies, URLs, tokens and candidate data. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DocumentStorageUnavailableException extends RuntimeException {
    public DocumentStorageUnavailableException() {
        super("Document storage is temporarily unavailable. Please try again.");
    }
}
