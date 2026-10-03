package com.codewalnut.ats.client;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Builds fake résumé files for tests in other packages. */
public final class ResumeTextTestSupport {

    private ResumeTextTestSupport() {}

    public static byte[] pdf(String... lines) {
        return ResumeTextTest.pdf(lines);
    }

    public static byte[] docx(String paragraphs) {
        try {
            return ResumeTextTest.docx(paragraphs);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
