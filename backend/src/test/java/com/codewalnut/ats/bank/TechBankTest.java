package com.codewalnut.ats.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The hand-written technical banks parse, cover every band and level, and are well formed. */
class TechBankTest {

    private final List<Seed> bank = TechBank.all();

    @Test
    void everyAreaHasTopicsInEveryBandWithEasyMediumAndHardQuestions() {
        for (Category area : TechBank.AREAS) {
            List<TechBank.Topic> topics = TechBank.topics(area);
            if (area == Category.WEB_API) {
                // A small fresher-only area (ASMT-39): basics for interns, no advanced band.
                assertThat(topics).as(area.name()).hasSizeGreaterThanOrEqualTo(3).allMatch(t -> t.section() == Section.FUNDAMENTALS);
            } else {
                assertThat(topics).as(area.name()).hasSizeGreaterThanOrEqualTo(10);
                for (Section band : List.of(Section.FUNDAMENTALS, Section.PRACTICAL, Section.ADVANCED)) {
                    assertThat(topics.stream().filter(t -> t.section() == band)).as(area + " " + band).hasSizeGreaterThanOrEqualTo(3);
                }
            }
            for (TechBank.Topic t : topics) {
                assertThat(t.covers()).isNotBlank();
                assertThat(t.example()).isNotBlank();
                Map<Difficulty, Long> levels = bank.stream().filter(q -> q.area() == area && q.topic().equals(t.name()))
                        .collect(Collectors.groupingBy(Seed::difficulty, Collectors.counting()));
                for (Difficulty d : Difficulty.values()) {
                    assertThat(levels.getOrDefault(d, 0L)).as(area + " / " + t.name() + " " + d).isGreaterThanOrEqualTo(4L);
                }
            }
        }
    }

    @Test
    void everyQuestionIsWellFormed() {
        Set<String> keys = new HashSet<>();
        for (Seed q : bank) {
            assertThat(keys.add(q.key())).as("unique key %s", q.key()).isTrue();
            assertThat(q.key().length()).isLessThanOrEqualTo(80);
            assertThat(q.section().isAptitude()).isFalse();
            assertThat(q.options()).as(q.prompt()).hasSize(4).doesNotHaveDuplicates();
            assertThat(q.correct()).hasSize(1).allMatch(c -> c >= 0 && c < 4);
            assertThat(q.explanation()).isNotBlank();
            assertThat(q.topic().length()).isLessThanOrEqualTo(60);
        }
        assertThat(bank).hasSizeGreaterThan(700);
        assertThat(bank.stream().filter(q -> q.code() != null).count()).isGreaterThan(100);
    }

    @Test
    void theRightAnswerIsNotGivenAwayByBeingTheLongestOption() {
        long clearlyLongest = bank.stream().filter(q -> {
            String right = q.options().get(q.correct().get(0));
            int others = q.options().stream().filter(o -> !o.equals(right)).mapToInt(String::length).max().orElse(0);
            return right.length() >= 20 && right.length() >= 1.25 * others;
        }).count();
        assertThat(clearlyLongest).isLessThan(bank.size() / 10);
    }

    @Test
    void keysFollowTheContentAndGenerationIsStable() {
        assertThat(TechBank.all()).isEqualTo(bank);
        assertThat(bank).allMatch(q -> q.key().startsWith("tech:" + q.area().name().toLowerCase() + ":"));
    }

    @Test
    void mistakesInTheFilesFailLoudly() {
        assertThatThrownBy(() -> TechBank.parse(Category.JAVA, """
                @topic t | FUNDAMENTALS | T | covers
                Q E Only two options?
                + yes
                - no
                = because
                """, "test.txt")).hasMessageContaining("test.txt:6").hasMessageContaining("one + and three -");
        assertThatThrownBy(() -> TechBank.parse(Category.JAVA, "@topic t | QUANT | T | covers\n", "test.txt"))
                .hasMessageContaining("FUNDAMENTALS");
    }
}
