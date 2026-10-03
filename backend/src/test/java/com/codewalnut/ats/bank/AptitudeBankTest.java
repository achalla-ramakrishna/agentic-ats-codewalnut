package com.codewalnut.ats.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.BankQuestion;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

/** Every built-in question is well formed: a valid answer, distinct options and parseable pictures. */
class AptitudeBankTest {

    private final List<Seed> bank = AptitudeBank.all();

    @Test
    void everyQuestionIsWellFormed() throws Exception {
        Set<String> keys = new HashSet<>();
        for (Seed q : bank) {
            assertThat(keys.add(q.key())).as("unique key %s", q.key()).isTrue();
            assertThat(q.key().length()).isLessThanOrEqualTo(80);
            assertThat(q.options()).as(q.key()).hasSize(4).doesNotHaveDuplicates();
            assertThat(q.correct()).as(q.key()).hasSize(1).allMatch(c -> c >= 0 && c < q.options().size());
            assertThat(q.prompt()).isNotBlank();
            assertThat(q.explanation()).isNotBlank();
            if (q.optionFigures() != null) {
                assertThat(q.optionFigures()).as(q.key()).hasSize(4).doesNotHaveDuplicates();
                for (String f : q.optionFigures()) {
                    parse(f);
                }
            }
            if (q.figure() != null) {
                parse(q.figure());
            }
        }
    }

    @Test
    void everyTopicHasFiftyDifferentQuestionsAcrossTheLevels() {
        assertThat(AptitudeBank.TOPICS).hasSize(26);
        assertThat(bank).hasSize(26 * AptitudeBank.PER_TOPIC);
        for (AptitudeBank.Topic topic : AptitudeBank.TOPICS) {
            List<Seed> mine = bank.stream().filter(q -> q.topic().equals(topic.name())).toList();
            assertThat(mine).as(topic.name()).hasSize(50).allMatch(q -> q.section() == topic.section());
            assertThat(mine.stream().map(Seed::prompt).distinct().count() + mine.stream().filter(q -> q.figure() != null || q.optionFigures() != null)
                    .map(q -> q.prompt() + q.figure() + q.optionFigures()).distinct().count()).as(topic.name()).isGreaterThanOrEqualTo(50);
            Map<BankQuestion.Difficulty, Long> levels = mine.stream().collect(Collectors.groupingBy(Seed::difficulty, Collectors.counting()));
            assertThat(levels).containsEntry(BankQuestion.Difficulty.EASY, 17L).containsEntry(BankQuestion.Difficulty.MEDIUM, 17L)
                    .containsEntry(BankQuestion.Difficulty.HARD, 16L);
            assertThat(topic.covers()).isNotBlank();
            assertThat(topic.example()).isNotBlank();
        }
        Map<BankQuestion.Section, Long> bySection = bank.stream().collect(Collectors.groupingBy(Seed::section, Collectors.counting()));
        assertThat(bySection).containsEntry(BankQuestion.Section.QUANT, 500L).containsEntry(BankQuestion.Section.LOGICAL, 550L)
                .containsEntry(BankQuestion.Section.VERBAL, 250L);
        long pictures = bank.stream().filter(q -> q.figure() != null || q.optionFigures() != null).count();
        assertThat(pictures).isGreaterThanOrEqualTo(350);
    }

    @Test
    void generationIsStable() {
        assertThat(AptitudeBank.all()).isEqualTo(bank);
    }

    @Test
    void knownAnswersAreRight() {
        Seed clock = bank.stream().filter(q -> q.key().equals(AptitudeBank.VERSION + ":clock:20")).findFirst().orElseThrow();
        assertThat(clock.options().get(clock.correct().get(0))).endsWith("°");
        assertThat(Logic.angle(3, 30)).isEqualTo(75);
        assertThat(Logic.angle(9, 0)).isEqualTo(90);
        assertThat(Logic.opposite("CODE")).isEqualTo("XLWV");
        assertThat(Logic.positionSum("CAT")).isEqualTo(24);
        assertThat(Logic.direction(3, -4)).isEqualTo("south-east");
        assertThat(AptitudeBank.shift("CODE", 3)).isEqualTo("FRGH");
        assertThat(AptitudeBank.shift("ZOO", 1)).isEqualTo("APP");
        assertThat(AptitudeBank.fraction(6, 36)).isEqualTo("1/6");
        assertThat(Svg.niceMax(130)).isEqualTo(250);
    }

    private static void parse(String svg) throws Exception {
        assertThat(svg).startsWith("<svg").doesNotContain("<script");
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    /** -Dbank.dump=/dir writes an HTML page of every question, to look at. */
    @Test
    void dumpForReview() throws Exception {
        String dir = System.getProperty("bank.dump");
        if (dir == null) {
            return;
        }
        StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif;max-width:900px'>");
        for (Seed q : bank) {
            html.append("<hr><p><b>").append(q.key()).append("</b> · ").append(q.section()).append(" · ").append(q.topic())
                    .append(" · ").append(q.difficulty()).append("</p><p style='white-space:pre-wrap'>").append(q.prompt()).append("</p>");
            if (q.figure() != null) {
                html.append(q.figure());
            }
            html.append("<ol type='A'>");
            for (int k = 0; k < 4; k++) {
                html.append("<li").append(q.correct().get(0) == k ? " style='color:green;font-weight:bold'" : "").append(">")
                        .append(q.optionFigures() != null ? q.optionFigures().get(k) : q.options().get(k)).append("</li>");
            }
            html.append("</ol><p><i>").append(q.explanation()).append("</i></p>");
        }
        Files.writeString(Path.of(dir, "bank.html"), html.append("</body></html>"));
    }
}
