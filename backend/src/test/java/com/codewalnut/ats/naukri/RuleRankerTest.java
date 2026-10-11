package com.codewalnut.ats.naukri;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-0026: without AI, profiles are ranked by the opening's skills, experience, title, location and notice. */
class RuleRankerTest {

    static final String JD = """
            We need a Senior Java Developer with 5-8 years of experience in Java, Spring Boot, microservices,
            Kafka, MySQL and AWS. Location: Bengaluru.""";

    private static NaukriProfile profile(String headline, Double years, String designation, String location, String notice) {
        return new NaukriProfile(2, "Test", "t@example.com", null, location, location, years, years + " years", "Example",
                designation, null, notice, headline, null, null, null, null, null, null);
    }

    @Test
    void aMatchingProfileOutranksAnUnrelatedOne() {
        RuleRanker.Score good = RuleRanker.score("Senior Java Developer", JD, "Bengaluru",
                profile("Java Spring Boot microservices developer, Kafka and AWS", 6.0, "Senior Software Engineer", "Bengaluru", "15 Days or less"));
        RuleRanker.Score javascript = RuleRanker.score("Senior Java Developer", JD, "Bengaluru",
                profile("JavaScript and React developer", 6.0, "Frontend Developer", "Bengaluru", "15 Days or less"));
        RuleRanker.Score junior = RuleRanker.score("Senior Java Developer", JD, "Bengaluru",
                profile("Java Spring Boot developer", 1.0, "Software Engineer", "Pune", "3 Months"));

        assertThat(good.percent()).isGreaterThan(80);
        assertThat(javascript.percent()).isLessThan(50);
        assertThat(junior.percent()).isLessThan(good.percent());
        assertThat(good.strengths()).anyMatch(s -> s.contains("spring boot"));
        assertThat(javascript.gaps()).anyMatch(s -> s.contains("java"));
        assertThat(junior.gaps()).anyMatch(s -> s.contains("asked 5–8"));
    }

    @Test
    void readsSkillsAndYearsFromTheOpening() {
        assertThat(RuleRanker.skillsIn(JD)).contains("java", "spring boot", "microservices", "kafka", "mysql", "aws")
                .doesNotContain("spring", "javascript");
        assertThat(RuleRanker.yearsIn(JD)).containsExactly(5, 8);
        assertThat(RuleRanker.yearsIn("Minimum 4+ years")).containsExactly(4, -1);
        assertThat(RuleRanker.yearsIn("No years mentioned")).isNull();
    }
}
