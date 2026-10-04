package com.codewalnut.ats.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codewalnut.ats.client.CodeRunner;
import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import com.codewalnut.ats.service.CodingSpecs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** ASMT-36: the built-in coding problems parse, cover every topic and level, and their tests match the reference solutions. */
class CodingBankTest {

    @Test
    void problemsCoverEveryTopicAndThePresets() {
        List<CodingBank.Problem> problems = CodingBank.problems();
        assertThat(problems).hasSizeGreaterThanOrEqualTo(60);
        assertThat(problems.stream().map(CodingBank.Problem::id).collect(Collectors.toSet())).hasSameSizeAs(problems);
        for (TechBank.Topic t : CodingBank.topics()) {
            assertThat(problems.stream().filter(p -> p.topicId().equals(t.id()))).as(t.name()).hasSizeGreaterThanOrEqualTo(4);
        }
        Map<String, Long> by = CodingBank.all().stream()
                .collect(Collectors.groupingBy(s -> s.section() + "/" + s.difficulty(), Collectors.counting()));
        // Freshers' preset (2 easy fundamentals) and the harder role levels can always be filled, with room to vary.
        assertThat(by.get(Section.FUNDAMENTALS + "/" + Difficulty.EASY)).isGreaterThanOrEqualTo(6);
        assertThat(by.get(Section.FUNDAMENTALS + "/" + Difficulty.MEDIUM)).isGreaterThanOrEqualTo(4);
        assertThat(by.get(Section.PRACTICAL + "/" + Difficulty.MEDIUM)).isGreaterThanOrEqualTo(6);
        assertThat(by.get(Section.ADVANCED + "/" + Difficulty.MEDIUM)).isGreaterThanOrEqualTo(4);
        assertThat(by.get(Section.ADVANCED + "/" + Difficulty.HARD)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void everyProblemIsWellFormed() {
        Set<String> keys = new HashSet<>();
        for (Seed s : CodingBank.all()) {
            assertThat(keys.add(s.key())).isTrue();
            assertThat(s.key().length()).isLessThanOrEqualTo(80);
            assertThat(s.area()).isEqualTo(Category.CODING);
            assertThat(s.kind()).isEqualTo(AssessmentQuestion.Kind.CODING);
            assertThat(s.points()).isIn(5, 10, 15);
            assertThat(s.codingJson()).contains("\"samples\"");
            assertThat(s.topic().length()).isLessThanOrEqualTo(60);
        }
        for (CodingBank.Problem p : CodingBank.problems()) {
            assertThat(p.spec().languages()).containsExactlyElementsOf(CodeRunner.LANGUAGES);
            assertThat(p.spec().starter().keySet()).containsExactlyInAnyOrderElementsOf(CodeRunner.LANGUAGES);
            assertThat(p.tests()).as(p.id()).hasSizeGreaterThanOrEqualTo(5);
            assertThat(p.spec().inputFormat()).as(p.id()).isNotBlank();
            assertThat(p.spec().outputFormat()).as(p.id()).isNotBlank();
        }
    }

    /** Hidden tests must be right: every expected output is what the reference solution prints (skipped without python3). */
    @Test
    void expectedOutputsMatchTheReferenceSolutions() throws Exception {
        assumeTrue(python() != null, "python3 is not installed");
        for (CodingBank.Problem p : CodingBank.problems()) {
            String solution;
            try (InputStream in = getClass().getResourceAsStream("/coding-solutions/" + p.id() + ".py")) {
                assertThat(in).as("reference solution for " + p.id()).isNotNull();
                solution = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            List<TestCase> all = new java.util.ArrayList<>(p.spec().samples());
            all.addAll(p.tests());
            for (TestCase t : all) {
                assertThat(CodingSpecs.matches(run(solution, t.input()), t.output())).as("%s on input %.60s", p.id(), t.input()).isTrue();
            }
        }
    }

    @Test
    void badProblemsFailLoudly() {
        assertThatThrownBy(() -> CodingBank.parse("id: x\ntopic: nope\ndifficulty: EASY\ntitle: X\nstatement: s\n", "x.yml"))
                .hasMessageContaining("unknown topic");
        assertThatThrownBy(() -> CodingBank.parse("""
                id: x
                topic: code-basics
                difficulty: EASY
                title: X
                statement: s
                samples:
                  - input: "1"
                    output: "1"
                tests:
                  - input: "1"
                    output: "1"
                """, "x.yml")).hasMessageContaining("five hidden tests");
    }

    private static String python() {
        try {
            Process p = new ProcessBuilder("python3", "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 ? "python3" : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String run(String program, String input) throws Exception {
        Process p = new ProcessBuilder("python3", "-c", program).redirectErrorStream(true).start();
        p.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return out;
    }
}
