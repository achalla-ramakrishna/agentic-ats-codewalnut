package com.codewalnut.ats.naukri;

import jakarta.validation.constraints.Size;

/**
 * One applicant row from a job-portal export (Naukri), with only the job-related columns. Gender,
 * date of birth and home town are never read (ADR-0026). Also the shape the page sends back for the
 * rows chosen to import.
 */
public record NaukriProfile(
        int line,
        @Size(max = 200) String name,
        @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Size(max = 200) String currentLocation,
        @Size(max = 500) String preferredLocations,
        Double experienceYears,
        @Size(max = 100) String experience,
        @Size(max = 200) String company,
        @Size(max = 200) String designation,
        @Size(max = 100) String salary,
        @Size(max = 100) String noticePeriod,
        @Size(max = 2000) String headline,
        @Size(max = 2000) String keySkills,
        @Size(max = 200) String ugDegree,
        @Size(max = 300) String ugCollege,
        @Size(max = 200) String pgDegree,
        @Size(max = 300) String pgCollege,
        @Size(max = 50) String appliedOn) {

    /** The profile as plain text for matching and for the AI. */
    public String text() {
        StringBuilder b = new StringBuilder();
        append(b, "Headline", headline);
        append(b, "Key skills", keySkills);
        append(b, "Current role", designation == null ? null : designation + (company == null ? "" : " at " + company));
        append(b, "Experience", experience);
        append(b, "Location", currentLocation);
        append(b, "Preferred locations", preferredLocations);
        append(b, "Notice period", noticePeriod);
        append(b, "Current salary", salary);
        append(b, "Graduation", join(ugDegree, ugCollege));
        append(b, "Post-graduation", join(pgDegree, pgCollege));
        return b.toString();
    }

    private static void append(StringBuilder b, String label, String value) {
        if (value != null && !value.isBlank()) {
            b.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + ", " + b;
    }
}
