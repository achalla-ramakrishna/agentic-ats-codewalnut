package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.InterviewFeedback.Recommendation;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AssessmentDtos.InviteView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** "Closest to selection" ranking (AI-32): interview feedback counts most. */
class ReadinessTest {

    private static Application at(Stage stage) {
        return Application.builder().stage(stage).build();
    }

    private static InviteView test(int percent) {
        return new InviteView(null, null, null, "Java", null, null, null, null, null, null, null, null, null, percent,
                percent >= 60, 60, 0, null, false, false, null);
    }

    @Test
    void strongInterviewFeedbackOutranksABetterResume() {
        Integer hired = ResumeIntelligenceService.readiness(at(Stage.INTERVIEWED), List.of(Recommendation.STRONG_YES, Recommendation.YES),
                test(70), 60);
        Integer onPaper = ResumeIntelligenceService.readiness(at(Stage.INTERVIEWED), null, test(70), 90);
        assertThat(hired).isGreaterThan(onPaper);
    }

    @Test
    void usesWhateverEvidenceThereIsAndAddsALittleForShortlisted() {
        // No feedback yet counts as neutral (50).
        assertThat(ResumeIntelligenceService.readiness(at(Stage.INTERVIEWED), null, null, 80)).isEqualTo(65);
        assertThat(ResumeIntelligenceService.readiness(at(Stage.SHORTLISTED), null, null, 80)).isEqualTo(70);
        assertThat(ResumeIntelligenceService.readiness(at(Stage.SHORTLISTED), null, null, null)).isNull();
        assertThat(ResumeIntelligenceService.readiness(at(Stage.SHORTLISTED), List.of(Recommendation.STRONG_YES), test(100), 100))
                .isEqualTo(100);
    }

    @Test
    void aPanelThatSaidNoKeepsThemOffTheList() {
        assertThat(ResumeIntelligenceService.panelAgainst(List.of(Recommendation.NO, Recommendation.YES))).isFalse();
        assertThat(ResumeIntelligenceService.panelAgainst(List.of(Recommendation.NO, Recommendation.STRONG_NO))).isTrue();
        assertThat(ResumeIntelligenceService.panelAgainst(List.of())).isFalse();
        assertThat(ResumeIntelligenceService.panelAgainst(null)).isFalse();
    }
}
