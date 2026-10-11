package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Judges short job-portal profiles (headline, role, experience; no résumé) against an opening
 * (ADR-0026). Advisory only: it ranks, people choose whom to contact.
 */
public interface ProfileRanker {

    record Job(String title, String description, String location) {}

    /** id: the caller's number for the profile; text: the profile as plain text. */
    record Profile(int id, String text) {}

    record Scores(List<Score> scores) {}

    record Score(
            @JsonPropertyDescription("The profile's id, as given") int id,
            @JsonPropertyDescription("How well the profile matches the opening, 0 to 100") int matchPercent,
            @JsonPropertyDescription("One short sentence for the recruiter: why this person is or isn't a fit") String summary,
            @JsonPropertyDescription("Up to 3 short job-related reasons in favour") List<String> strengths,
            @JsonPropertyDescription("Up to 3 short things to check, phrased as 'not evident' rather than 'lacks'") List<String> gaps) {}

    boolean available();

    String model();

    /** @throws CalendarException with a message to show when the AI can't score this batch */
    List<Score> rank(Job job, List<Profile> profiles);
}
