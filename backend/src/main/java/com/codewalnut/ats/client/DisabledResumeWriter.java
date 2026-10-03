package com.codewalnut.ats.client;

/** No ANTHROPIC_API_KEY configured: CodeWalnut résumés are made by hand. */
public class DisabledResumeWriter implements ResumeWriter {

    @Override
    public BrandedResume write(ResumeAnalyzer.Job job, ResumeAnalyzer.ResumeFile file) {
        throw new CalendarException("AI résumé writing isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String model() {
        return "none";
    }
}
