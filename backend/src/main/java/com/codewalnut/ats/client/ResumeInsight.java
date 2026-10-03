package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the AI read from one résumé, judged against one opening (ADR-0010). The JSON schema sent
 * to Claude (structured output) is derived from these records. Advisory only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeInsight(
        @JsonPropertyDescription("The candidate's full name as written on the résumé, or empty")
        String name,
        @JsonPropertyDescription("Email address on the résumé, or empty")
        String email,
        @JsonPropertyDescription("Phone number on the résumé, digits and a leading + only, or empty")
        String phone,
        @JsonPropertyDescription("City / location on the résumé, or empty")
        String location,
        @JsonPropertyDescription("Current or most recent role and organisation, e.g. \"Intern, Acme Labs\", or \"Student\", or empty")
        String currentRole,
        @JsonPropertyDescription("Total months of work and internship experience, 0 if none. Count overlapping periods once.")
        Integer experienceMonths,
        @JsonPropertyDescription("Year of graduation (past or expected), or 0 if not stated")
        Integer graduationYear,
        @JsonPropertyDescription("Highest education, e.g. \"B.E. Computer Science, RV College (2026)\", or empty")
        String education,
        @JsonPropertyDescription("College or university of the highest degree, e.g. \"RV College of Engineering, Bengaluru\", or empty")
        String college,
        @JsonPropertyDescription("The highest degree and branch, e.g. \"B.E. Computer Science\", or empty")
        String degree,
        @JsonPropertyDescription("LinkedIn profile link exactly as written on the résumé, or empty")
        String linkedinUrl,
        @JsonPropertyDescription("Postal address exactly as written on the résumé, or empty if only a city is given")
        String address,
        @JsonPropertyDescription("Technical skills shown on the résumé, most relevant to the opening first, at most 20")
        List<String> skills,
        @JsonPropertyDescription("Work and internship history, most recent first, at most 6")
        List<Experience> experience,
        @JsonPropertyDescription("Notable projects, at most 5")
        List<Project> projects,
        @JsonPropertyDescription("One neutral sentence (at most 25 words) a recruiter can scan, e.g. \"Final-year CSE student with two React internships and a Spring Boot project.\"")
        String headline,
        @JsonPropertyDescription("Each requirement of the opening and how well the résumé shows it. Empty if the opening lists none.")
        List<Requirement> requirements,
        @JsonPropertyDescription("Up to 4 short, specific strengths for this opening, each tied to something in the résumé")
        List<String> strengths,
        @JsonPropertyDescription("Up to 4 things the résumé does not show that matter for this opening, worded as 'not evident in résumé' (never as a judgement of the person)")
        List<String> gaps,
        @JsonPropertyDescription("Up to 4 questions worth asking the candidate in a first call to confirm the gaps")
        List<String> questionsToAsk) {

    public record Experience(
            @JsonPropertyDescription("Role or title") String role,
            @JsonPropertyDescription("Organisation") String organisation,
            @JsonPropertyDescription("Period as written, e.g. \"Jan 2025 – Jun 2025\"") String period,
            @JsonPropertyDescription("One line on what they did") String summary) {}

    public record Project(
            @JsonPropertyDescription("Project name") String name,
            @JsonPropertyDescription("One line: what it does and the technologies used") String summary) {}

    public record Requirement(
            @JsonPropertyDescription("The requirement, copied or briefly paraphrased from the opening") String requirement,
            @JsonPropertyDescription("MET, PARTIAL or NOT_EVIDENT") String assessment,
            @JsonPropertyDescription("The résumé evidence in a few words, or 'not evident in résumé'") String evidence) {}
}
