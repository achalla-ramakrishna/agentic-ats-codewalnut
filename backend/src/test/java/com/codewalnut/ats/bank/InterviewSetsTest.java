package com.codewalnut.ats.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.bank.InterviewSets.Track;
import com.codewalnut.ats.client.InterviewAgent.DraftQuestion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The hand-written AI interview sets are complete and well formed; openings get the right ones. */
class InterviewSetsTest {

    @Test
    void openingsGetTheirOwnSets() {
        assertThat(Track.forOpening("sabre front end engineer intern. null", false)).isEqualTo(Track.FRONT_END_INTERN);
        assertThat(Track.forOpening("software engineering intern (react, java)", false)).isEqualTo(Track.FRONT_END_INTERN);
        assertThat(Track.forOpening("senior java developer", true)).isEqualTo(Track.SENIOR_JAVA);
        assertThat(Track.forOpening("python developer", false)).isNull();
        assertThat(Track.forOpening("hr intern", false)).isNull();
        assertThat(Track.forOpening("internal tools front end developer", false)).isNull(); // "internal" isn't "intern"
    }

    @Test
    void everyInternSetIsACompleteInterview() {
        Set<String> prompts = new HashSet<>();
        for (int set = 1; set <= 4; set++) {
            List<DraftQuestion> q = Track.FRONT_END_INTERN.questions(set);
            assertThat(q).as("set %d", set).hasSizeBetween(28, 34);
            assertThat(q.stream().map(DraftQuestion::phase).distinct())
                    .containsExactlyInAnyOrder("INTRO", "EXPERIENCE", "PROJECTS", "SKILLS", "TECHNICAL", "ARCHITECTURE", "SOFT_SKILLS",
                            "READINESS");
            assertThat(q.stream().filter(d -> d.kind().equals("WRITTEN") && d.phase().equals("TECHNICAL"))).as("coding").hasSize(2);
            assertThat(q.stream().filter(d -> d.kind().equals("MULTIPLE_CHOICE"))).hasSizeGreaterThanOrEqualTo(8);
            for (DraftQuestion d : q) {
                if (d.kind().equals("MULTIPLE_CHOICE")) {
                    assertThat(d.correctOption()).as(d.prompt()).isBetween(0, d.options().size() - 1);
                    assertThat(new HashSet<>(d.options())).as(d.prompt()).hasSameSizeAs(d.options());
                }
                if (d.kind().equals("FILL_BLANK")) {
                    assertThat(d.prompt()).contains("____");
                    assertThat(d.acceptedAnswers()).isNotEmpty();
                }
                if (d.kind().equals("VIDEO")) {
                    assertThat(d.maxSeconds()).isBetween(60, 150);
                    assertThat(d.goodAnswer()).as(d.prompt()).isNotBlank();
                }
                if (!d.phase().equals("INTRO") && !d.phase().equals("READINESS")) {
                    assertThat(prompts.add(d.prompt() + d.code())).as("repeated across sets: %s", d.prompt()).isTrue();
                }
            }
        }
    }
}
