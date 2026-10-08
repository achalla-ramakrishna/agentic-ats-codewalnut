package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.service.TechBackground.Track;
import org.junit.jupiter.api.Test;

/** ASMT-39: the résumé's stack decides which intern test a candidate is offered. */
class TechBackgroundTest {

    @Test
    void javaPythonAndMernAreToldApart() {
        assertThat(TechBackground.of("Java, Spring Boot, MySQL, HTML", "Built a library system with Hibernate").track())
                .isEqualTo(Track.JAVA);
        assertThat(TechBackground.of("Python, Django, Pandas", "ML mini project in Python").track()).isEqualTo(Track.PYTHON);
        assertThat(TechBackground.of("JavaScript, React.js, Node.js, Express, MongoDB", "MERN e-commerce site").track())
                .isEqualTo(Track.MERN);
    }

    @Test
    void javaScriptIsNotJava() {
        TechBackground.Result r = TechBackground.of("JavaScript, React, HTML, CSS", "");
        assertThat(r.track()).isEqualTo(Track.MERN);
        assertThat(r.evidence()).doesNotContain("Java");
    }

    @Test
    void aMinorMentionDoesNotChangeTheTrack() {
        // A Java developer who once used React a little stays Java.
        assertThat(TechBackground.of("Java, Spring Boot, Hibernate, Maven", "Used React for one admin page").track())
                .isEqualTo(Track.JAVA);
    }

    @Test
    void equalStrengthIsMixedAndNothingIsNull() {
        assertThat(TechBackground.of("Java, Spring Boot, Python, Django", "").track()).isEqualTo(Track.MIXED);
        assertThat(TechBackground.of("C, C++, Excel", "Tally accounting").track()).isNull();
        assertThat(TechBackground.of(null).track()).isNull();
    }
}
