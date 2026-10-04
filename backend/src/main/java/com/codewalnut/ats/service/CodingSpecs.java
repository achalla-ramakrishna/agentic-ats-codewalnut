package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CodeRunner;
import com.codewalnut.ats.dto.AssessmentDtos.CodingRequest;
import com.codewalnut.ats.dto.AssessmentDtos.CodingSpec;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Reading, checking and comparing coding questions (ADR-0016). Programs read the input from stdin
 * and print the answer; output matches when it is the same after trailing spaces and blank lines
 * are ignored. The spec (samples, starter code, limits) is public; hidden tests are not.
 */
@Component
@RequiredArgsConstructor
public class CodingSpecs {

    static final double DEFAULT_SECONDS = 2;
    static final int DEFAULT_MEMORY_MB = 256;
    static final int MAX_SOURCE = 50_000;

    /** Slower runtimes get more CPU time than the question's base limit (common judge practice). */
    private static final Map<String, Double> TIME_FACTOR = Map.of("cpp", 1.0, "java", 2.0, "javascript", 2.0, "python", 3.0);

    public static final Map<String, String> LANGUAGE_NAMES = Map.of("java", "Java", "python", "Python", "javascript", "JavaScript",
            "cpp", "C++");

    private final ObjectMapper objectMapper;

    /** A checked coding question: the public spec and hidden tests, as stored. */
    public record Stored(String specJson, String testsJson) {}

    public Stored normalise(CodingRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("coding: add sample and hidden test cases");
        }
        List<String> languages = r.languages() == null || r.languages().isEmpty() ? CodeRunner.LANGUAGES
                : r.languages().stream().map(l -> l == null ? "" : l.strip().toLowerCase(Locale.ROOT)).distinct().toList();
        for (String l : languages) {
            if (!CodeRunner.LANGUAGES.contains(l)) {
                throw new IllegalArgumentException("coding.languages: choose from " + String.join(", ", CodeRunner.LANGUAGES));
            }
        }
        Map<String, String> starter = new LinkedHashMap<>();
        for (String l : languages) {
            String given = r.starter() == null ? null : r.starter().get(l);
            starter.put(l, StringUtils.hasText(given) ? given.replace("\r\n", "\n") : defaultStarter(l));
        }
        List<TestCase> samples = cases(r.samples());
        List<TestCase> tests = cases(r.tests());
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("coding.samples: add at least one sample test candidates can see");
        }
        if (tests.isEmpty()) {
            throw new IllegalArgumentException("coding.tests: add at least one hidden test");
        }
        double seconds = r.timeLimitSeconds() == null ? DEFAULT_SECONDS : r.timeLimitSeconds();
        if (seconds < 0.5 || seconds > 10) {
            throw new IllegalArgumentException("coding.timeLimitSeconds: between 0.5 and 10 seconds");
        }
        int memory = r.memoryMb() == null ? DEFAULT_MEMORY_MB : r.memoryMb();
        if (memory < 64 || memory > 512) {
            throw new IllegalArgumentException("coding.memoryMb: between 64 and 512 MB");
        }
        CodingSpec spec = new CodingSpec(languages, starter, samples, seconds, memory, blankToNull(r.inputFormat()),
                blankToNull(r.outputFormat()), blankToNull(r.constraints()));
        return new Stored(write(spec), write(tests));
    }

    public CodingSpec spec(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CodingSpec.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<TestCase> tests(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TestCase>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Samples first, then hidden tests: everything a submission is graded on. */
    public List<TestCase> allTests(CodingSpec spec, String testsJson) {
        List<TestCase> all = new ArrayList<>(spec.samples());
        all.addAll(tests(testsJson));
        return all;
    }

    public CodeRunner.Limits limits(CodingSpec spec, String language) {
        return new CodeRunner.Limits(spec.timeLimitSeconds() * TIME_FACTOR.getOrDefault(language, 1.0), spec.memoryMb());
    }

    /** Same output, ignoring \r, trailing spaces on each line and trailing blank lines. */
    public static boolean matches(String actual, String expected) {
        return tidy(actual).equals(tidy(expected));
    }

    static String tidy(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line.stripTrailing()).append('\n');
        }
        return out.toString().strip().isEmpty() ? "" : out.toString().replaceAll("\\s+$", "");
    }

    /** Points for passing some of the tests: points × passed ÷ total, rounded. */
    public static int earned(int points, int passed, int total) {
        return total == 0 ? 0 : Math.round(points * (float) passed / total);
    }

    /** A program skeleton that reads all of stdin, for when the author gives no starter code. */
    public static String defaultStarter(String language) {
        return switch (language) {
            case "java" -> """
                    import java.io.*;
                    import java.util.*;

                    public class Main {
                        public static void main(String[] args) throws IOException {
                            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                            // Read the input, solve, and print the answer.
                            String line = in.readLine();
                            System.out.println(line);
                        }
                    }
                    """;
            case "python" -> """
                    import sys

                    def main():
                        data = sys.stdin.read().split()
                        # Read the input, solve, and print the answer.
                        print(" ".join(data))

                    main()
                    """;
            case "javascript" -> """
                    const data = require("fs").readFileSync(0, "utf8").trim().split(/\\s+/);
                    // Read the input, solve, and print the answer.
                    console.log(data.join(" "));
                    """;
            case "cpp" -> """
                    #include <bits/stdc++.h>
                    using namespace std;

                    int main() {
                        ios::sync_with_stdio(false);
                        cin.tie(nullptr);
                        // Read the input, solve, and print the answer.
                        string s;
                        getline(cin, s);
                        cout << s << "\\n";
                        return 0;
                    }
                    """;
            default -> "";
        };
    }

    private List<TestCase> cases(List<TestCase> given) {
        if (given == null) {
            return List.of();
        }
        List<TestCase> out = new ArrayList<>();
        for (TestCase t : given) {
            if (t == null || t.output() == null || t.output().isBlank() && (t.input() == null || t.input().isBlank())) {
                continue;
            }
            out.add(new TestCase(t.input() == null ? "" : t.input().replace("\r\n", "\n"), t.output().replace("\r\n", "\n")));
        }
        return out;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }
}
