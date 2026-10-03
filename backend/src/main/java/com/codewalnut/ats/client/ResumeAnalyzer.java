package com.codewalnut.ats.client;

/** Reads a résumé and judges it against an opening's description (ADR-0010). Advisory only. */
public interface ResumeAnalyzer {

    record Job(String title, String description) {}

    record ResumeFile(String fileName, String contentType, byte[] data) {}

    boolean available();

    /** Model name recorded with each reading, so readings from different models can be told apart. */
    String model();

    /** @throws CalendarException with a message to show when the résumé can't be read */
    ResumeInsight analyze(Job job, ResumeFile file);
}
