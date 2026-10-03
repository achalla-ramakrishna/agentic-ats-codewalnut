package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * A candidate's résumé rewritten in CodeWalnut's format for a client (ADR-0012). The JSON schema
 * sent to Claude (structured output) is derived from these records; people edit it before use.
 * It never holds a phone number: clients reach candidates through CodeWalnut.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrandedResume(
        @JsonPropertyDescription("The candidate's full name as on the résumé")
        String name,
        @JsonPropertyDescription("A short title line suited to the opening, built only from the résumé, e.g. \"Full Stack Developer | MERN / PERN\"")
        String headline,
        @JsonPropertyDescription("City and country only, e.g. \"Bengaluru, India\", or empty")
        String location,
        @JsonPropertyDescription("The candidate's email from the résumé, or empty")
        String email,
        @JsonPropertyDescription("2–3 sentences for the client: what the candidate does well and why they fit the opening, using only facts from the résumé")
        String summary,
        @JsonPropertyDescription("Technical skills in 2–5 labelled groups, e.g. {label: \"Backend\", items: [\"Java\", \"Spring Boot\"]}")
        List<SkillGroup> skills,
        @JsonPropertyDescription("The résumé's sections in this order where present: Experience, Selected projects, Education, Achievements, Certifications, Additional information (soft skills, languages)")
        List<Section> sections) {

    public record SkillGroup(
            @JsonPropertyDescription("Group label, e.g. \"Backend / core\"") String label,
            @JsonPropertyDescription("Skills in this group") List<String> items) {}

    public record Section(
            @JsonPropertyDescription("Section title, e.g. \"Experience\"") String title,
            @JsonPropertyDescription("Entries in this section") List<Entry> entries) {}

    public record Entry(
            @JsonPropertyDescription("Main line, e.g. a role, project name, degree or achievement; empty for a plain list") String title,
            @JsonPropertyDescription("Second part of the main line, e.g. organisation, tech stack or institution; or empty") String subtitle,
            @JsonPropertyDescription("Dates as written, e.g. \"Jan 2025 – Jun 2025\", or empty") String period,
            @JsonPropertyDescription("Bullet points or detail lines, each one sentence") List<String> bullets) {}
}
