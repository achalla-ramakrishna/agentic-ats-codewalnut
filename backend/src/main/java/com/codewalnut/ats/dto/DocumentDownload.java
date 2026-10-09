package com.codewalnut.ats.dto;

/** Internal controller result; the HTTP response contains bytes, never the persistence entity. */
public record DocumentDownload(String fileName, String contentType, byte[] data) {}
