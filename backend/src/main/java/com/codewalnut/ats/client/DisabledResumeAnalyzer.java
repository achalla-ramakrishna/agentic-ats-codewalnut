package com.codewalnut.ats.client;

/** No ANTHROPIC_API_KEY configured: résumé reading is switched off. */
public class DisabledResumeAnalyzer implements ResumeAnalyzer {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String model() {
        return "none";
    }

    @Override
    public ResumeInsight analyze(Job job, ResumeFile file) {
        throw new CalendarException("AI résumé reading isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
    }
}
