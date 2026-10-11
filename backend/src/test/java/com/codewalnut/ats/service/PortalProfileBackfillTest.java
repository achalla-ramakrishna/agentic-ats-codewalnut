package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.dto.NaukriDtos.ImportItem;
import com.codewalnut.ats.dto.TrackerDtos.PortalProfile;
import com.codewalnut.ats.naukri.NaukriProfile;
import org.junit.jupiter.api.Test;

/** ADR-0026: candidates imported before the details were stored get them back from their first note. Fake data. */
class PortalProfileBackfillTest {

    private static NaukriProfile profile(String keySkills) {
        return new NaukriProfile(2, "Test Person", "t@example.com", "9876543210", "Bengaluru", "Bengaluru, Pune", 6.4,
                "6 Year(s) 5 Month(s)", "Example Tech", "Senior Software Engineer", "Rs 12 Lakhs", "15 Days or less",
                "Java, Spring Boot and microservices developer with AWS", keySkills, "B.Tech", "Example Institute", null, null,
                "09-Oct-2026");
    }

    @Test
    void readsBackWhatTheImportWrote() {
        NaukriProfile p = profile(null);
        ImportItem item = new ImportItem(p, 83, "Strong Java and Spring Boot match");
        PortalProfile fromNote = PortalProfileBackfill.fromNote(NaukriImportService.note(p, item));

        assertThat(fromNote).isEqualTo(NaukriImportService.portalProfile(p, item));
        assertThat(fromNote.skills()).containsExactly("Java", "Spring Boot", "Microservices", "AWS");
        assertThat(fromNote.currentRole()).isEqualTo("Senior Software Engineer at Example Tech");
        assertThat(fromNote.location()).isEqualTo("Bengaluru");
        assertThat(fromNote.experienceYears()).isEqualTo(6.4);
        assertThat(fromNote.matchPercent()).isEqualTo(83);
    }

    @Test
    void keySkillsAndMissingMatchAreHandled() {
        NaukriProfile p = profile("Java, Kafka; Docker");
        PortalProfile fromNote = PortalProfileBackfill.fromNote(NaukriImportService.note(p, new ImportItem(p, null, null)));
        assertThat(fromNote.skills()).containsExactly("Java", "Kafka", "Docker");
        assertThat(fromNote.matchPercent()).isNull();
        assertThat(fromNote.summary()).isNull();
        assertThat(PortalProfileBackfill.fromNote("Imported")).isNull();
        // A rule-based reason isn't a summary; the table shows the candidate's headline instead.
        assertThat(PortalProfileBackfill.fromNote(NaukriImportService.note(p, new ImportItem(p, 70, "Mentions java, kafka"))).summary()).isNull();
    }
}
