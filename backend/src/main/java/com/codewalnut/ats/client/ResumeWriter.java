package com.codewalnut.ats.client;

/** Rewrites an original résumé into CodeWalnut's client-facing format (ADR-0012). */
public interface ResumeWriter {

    /** job: the opening the résumé is tailored to. */
    BrandedResume write(ResumeAnalyzer.Job job, ResumeAnalyzer.ResumeFile file);

    boolean available();

    String model();
}
