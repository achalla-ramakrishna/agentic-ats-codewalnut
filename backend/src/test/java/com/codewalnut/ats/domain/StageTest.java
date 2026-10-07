package com.codewalnut.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Seven stages in use; the retired ones map to their replacements (PIPE-15). */
class StageTest {

    @Test
    void sevenStagesInPipelineOrder() {
        assertThat(Stage.inUse()).containsExactly(Stage.SOURCED, Stage.INTERVIEWED, Stage.SHORTLISTED, Stage.OFFER_SENT,
                Stage.JOINED, Stage.ON_HOLD, Stage.REJECTED);
    }

    @Test
    void retiredStagesMapToTheirReplacements() {
        assertThat(Stage.SCREENING.current()).isEqualTo(Stage.SOURCED);
        assertThat(Stage.SUBMITTED_TO_CLIENT.current()).isEqualTo(Stage.SHORTLISTED);
        assertThat(Stage.CLIENT_INTERVIEW.current()).isEqualTo(Stage.SHORTLISTED);
        assertThat(Stage.SELECTED.current()).isEqualTo(Stage.OFFER_SENT);
        assertThat(Stage.OFFER_ACCEPTED.current()).isEqualTo(Stage.OFFER_SENT);
        assertThat(Stage.WITHDRAWN.current()).isEqualTo(Stage.REJECTED);
        Stage.inUse().forEach(s -> assertThat(s.current()).isEqualTo(s));
    }
}
