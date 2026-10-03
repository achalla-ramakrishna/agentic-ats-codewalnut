package com.codewalnut.ats.bank;

import static com.codewalnut.ats.domain.Assessment.Category.APTITUDE;

import com.codewalnut.ats.domain.AssessmentQuestion.Kind;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeWalnut's built-in aptitude bank for freshers (ADR-0014), modelled on the common campus-test
 * pattern (TCS NQT, Infosys, Wipro NLTH, Cognizant GenC, Accenture): 26 topics across numerical
 * ability, logical reasoning and verbal ability, {@value #PER_TOPIC} questions each (17 easy, 17
 * medium, 16 hard). Numbers, pictures and logic puzzles are generated from seeded values and checked
 * by small solvers, so every answer is right by construction; verbal questions come from curated
 * lists. Generation is deterministic: the same keys always give the same questions.
 */
public final class AptitudeBank {

    /** Part of every key. v2 replaced the first 173-question bank (v1 rows are archived on load). */
    public static final String VERSION = "v2";
    public static final int PER_TOPIC = 50;

    /** Makes one question of a topic at a difficulty from a seeded random source. */
    @FunctionalInterface
    interface Gen {
        Seed make(String key, Difficulty d, Random r);
    }

    /** A topic: what it covers (shown in the topic guide) and how its questions are made. */
    public record Topic(String id, Section section, String name, String covers, String example, Gen gen) {}

    public static final List<Topic> TOPICS = List.of(
            // Numerical ability
            new Topic("percent", Section.QUANT, "Percentages", "Percent of a number, one value as a percent of another, successive increases and decreases, finding the original value.",
                    "After a 25% increase a price is ₹1,250. What was it before?", Quant::percentages),
            new Topic("profit", Section.QUANT, "Profit and loss", "Cost price, selling price, profit and loss %, marked price and discount, selling to reach a target gain.",
                    "Marked ₹1,500, sold at 20% off, cost ₹1,000: profit %?", Quant::profitLoss),
            new Topic("work", Section.QUANT, "Time and work", "Working together, one worker leaving midway, finding one person's time, pipes filling and emptying tanks.",
                    "A takes 12 days, B 24 days: together?", Quant::timeWork),
            new Topic("speed", Section.QUANT, "Speed, time and distance", "km/h ↔ m/s, trains crossing poles and platforms (with a picture), two trains crossing, average speed.",
                    "A 150 m train passes a pole in 15 s: speed in km/h?", Quant::speed),
            new Topic("interest", Section.QUANT, "Simple and compound interest", "Simple interest, compound interest for 2 years, rate or time from interest, CI − SI difference.",
                    "SI on ₹5,000 at 8% for 3 years?", Quant::interest),
            new Topic("ratio", Section.QUANT, "Ratio and proportion", "Dividing amounts in a ratio, three-way shares, ages in a ratio now and later, combining ratios.",
                    "₹1,200 shared 3 : 5: the larger share?", Quant::ratio),
            new Topic("average", Section.QUANT, "Averages", "Average of numbers, a new member changing the average, overlapping groups, replacing one value.",
                    "Average of 20 students is 50; one joins and it becomes 51: new student's marks?", Quant::averages),
            new Topic("series", Section.QUANT, "Number series", "Next term of arithmetic, geometric, square/cube, growing-difference, alternating and mixed-operation series.",
                    "2, 6, 12, 20, 30, ?", Quant::numberSeries),
            new Topic("probability", Section.QUANT, "Probability", "Coins, dice, cards and balls in a bag; single events, sums, at least one, drawing without replacement.",
                    "Two dice: probability the total is 8?", Quant::probability),
            new Topic("di", Section.QUANT, "Data interpretation", "Reading bar charts, line charts, pie charts and tables (pictures): highest/lowest, % change, averages, differences, angles.",
                    "From the bar chart: % increase in sales from 2022 to 2023?", Quant::dataInterpretation),
            // Logical reasoning
            new Topic("coding", Section.LOGICAL, "Coding-decoding", "Letter shifts, reversed words with shifts, opposite-letter (A↔Z) codes and letter-position number codes.",
                    "CODE is written FRGH; how is WORK written?", Logic::coding),
            new Topic("direction", Section.LOGICAL, "Direction sense", "Following walks with left/right turns; final distance (Pythagoras) and direction from the start.",
                    "5 km north, right 12 km: how far from start?", Logic::directions),
            new Topic("clock", Section.LOGICAL, "Clocks", "Reading an analogue clock (picture), angle between the hands, mirror image of the time.",
                    "Angle between the hands at 3:30?", Logic::clocks),
            new Topic("venn", Section.LOGICAL, "Venn diagrams", "Reading two- and three-set Venn diagrams (pictures): only one, both, exactly two, at least one, total, neither.",
                    "How many like tea but not coffee?", Logic::venn),
            new Topic("blood", Section.LOGICAL, "Blood relations", "Working out family relations from chains of statements: grandparents, uncles and aunts, nephews and nieces, cousins, in-laws.",
                    "A is B's sister; B is C's father. How is A related to C?", Reasoning::bloodRelations),
            new Topic("syllogism", Section.LOGICAL, "Syllogisms", "Two statements (All / No / Some / Some … not) and two conclusions: which follow? Checked by a logic solver.",
                    "All cars are bikes. No bike is a truck. I: No car is a truck.", Reasoning::syllogisms),
            new Topic("arrange", Section.LOGICAL, "Arrangements and ranking", "Seating five people in a row from clues (solver-checked, one answer), positions in a queue, ranks from both ends, height order.",
                    "C is in the middle, A is to the immediate left of C …: who is at the left end?", Reasoning::arrangements),
            new Topic("nv-series", Section.LOGICAL, "Non-verbal: figure series", "Which figure comes next: rotating arrows, alternating turns, growing numbers of dots or sides (pictures, picture options).",
                    "An arrow turns 90° each step: which comes next?", NonVerbal::figureSeries),
            new Topic("nv-mirror", Section.LOGICAL, "Non-verbal: mirror and water images", "Mirror images (mirror on the right) and water images (water below) of letters and numbers (picture options).",
                    "Which is the mirror image of R7KP?", NonVerbal::mirror),
            new Topic("nv-matrix", Section.LOGICAL, "Non-verbal: pattern matrix", "Complete a 3 × 3 grid of shapes that follows a row/column rule (picture options).",
                    "Each step right or down adds a dot: what replaces ?", NonVerbal::matrix),
            new Topic("nv-odd", Section.LOGICAL, "Non-verbal: odd one out", "Spot the figure that breaks the rule: number of sides, even vs odd, filled vs empty, dot counts (picture options).",
                    "Three triangles and a square: which is odd?", NonVerbal::oddOneOut),
            // Verbal ability
            new Topic("synonym", Section.VERBAL, "Synonyms", "Choosing the word closest in meaning; everyday words (easy) to less common, test-style vocabulary (hard).",
                    "Closest in meaning to CANDID?", Verbal::synonyms),
            new Topic("antonym", Section.VERBAL, "Antonyms", "Choosing the word most opposite in meaning.", "Most opposite to FRUGAL?", Verbal::antonyms),
            new Topic("completion", Section.VERBAL, "Sentence completion", "Filling the blank with the right preposition, tense, agreement, connector or word.",
                    "She has been working here ___ 2022.", Verbal::completion),
            new Topic("error", Section.VERBAL, "Error spotting", "Finding the part of a sentence with a grammatical error (or no error): agreement, tense, articles, pronouns, comparison.",
                    "(A) Each of the students / (B) have submitted / (C) the assignment.", Verbal::errors),
            new Topic("rc", Section.VERBAL, "Reading comprehension", "Short workplace and general-interest passages: facts, main idea, inference, vocabulary in context, tone.",
                    "According to the passage, why has solar power become cheaper?", Verbal::reading));

    private AptitudeBank() {}

    /** Every built-in question: {@value #PER_TOPIC} per topic, unique within each topic. */
    public static List<Seed> all() {
        List<Seed> out = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Topic topic : TOPICS) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < PER_TOPIC; i++) {
                Difficulty d = i < 17 ? Difficulty.EASY : i < 34 ? Difficulty.MEDIUM : Difficulty.HARD;
                String key = VERSION + ":" + topic.id() + ":" + i;
                Seed made = null;
                for (int attempt = 0; attempt < 500 && made == null; attempt++) {
                    Seed s = topic.gen().make(key, d, new Random(key.hashCode() * 31L + attempt * 7919L + 7));
                    // The same prompt and picture is the same question, whatever the distractors or their order.
                    String signature = s.prompt() + "|" + s.figure() + "|"
                            + (s.optionFigures() == null ? "" : new java.util.TreeSet<>(s.optionFigures()));
                    if (seen.add(signature)) {
                        // The topic guide's name is the one shown everywhere.
                        made = new Seed(s.key(), APTITUDE, topic.section(), topic.name(), d, s.kind(), s.prompt(), null, s.figure(), s.options(),
                                s.optionFigures(), s.correct(), s.accepted(), s.explanation());
                    }
                }
                if (made == null) {
                    failed.add(key);
                } else {
                    out.add(made);
                }
            }
        }
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Could not make unique questions for " + failed);
        }
        return out;
    }

    // ---------------- helpers shared by the generators ----------------

    static <T> T pick(Random r, List<T> list) {
        return list.get(r.nextInt(list.size()));
    }

    static int between(Random r, int lo, int hi) {
        return lo + r.nextInt(hi - lo + 1);
    }

    /** A single-answer choice question with the right answer shuffled among distinct distractors. */
    static Seed choice(String key, Section section, String topic, Difficulty d, String prompt, String figure, String answer,
            List<String> distractors, String explanation) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(answer);
        for (String x : distractors) {
            if (options.size() == 4) {
                break;
            }
            if (x != null && !x.isBlank()) {
                options.add(x);
            }
        }
        for (String extra : nearby(answer)) {
            if (options.size() == 4) {
                break;
            }
            options.add(extra);
        }
        if (options.size() < 4) {
            throw new IllegalStateException("Not enough distinct options for " + key);
        }
        List<String> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled, new Random((key + "#options").hashCode()));
        return new Seed(key, APTITUDE, section, topic, d, Kind.SINGLE_CHOICE, prompt, null, figure, shuffled, null,
                List.of(shuffled.indexOf(answer)), List.of(), explanation);
    }

    /** A choice question whose four options are pictures (labelled Figure A–D). */
    static Seed pictureChoice(String key, Section section, String topic, Difficulty d, String prompt, String figure,
            String answerFigure, List<String> distractorFigures, String explanation) {
        List<String> figures = new ArrayList<>();
        figures.add(answerFigure);
        for (String f : distractorFigures) {
            if (figures.size() == 4) {
                break;
            }
            if (!figures.contains(f)) {
                figures.add(f);
            }
        }
        if (figures.size() != 4) {
            throw new IllegalStateException("Need four different option pictures for " + key);
        }
        Collections.shuffle(figures, new Random((key + "#options").hashCode()));
        return new Seed(key, APTITUDE, section, topic, d, Kind.SINGLE_CHOICE, prompt, null, figure,
                List.of("Figure A", "Figure B", "Figure C", "Figure D"), figures, List.of(figures.indexOf(answerFigure)),
                List.of(), explanation);
    }

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(\\.\\d+)?");

    /** The answer with its number nudged (same units and format), to top up options that coincided. */
    static List<String> nearby(String answer) {
        Matcher m = NUMBER.matcher(answer);
        if (!m.find()) {
            return List.of();
        }
        String raw = m.group();
        boolean commas = raw.contains(",");
        boolean decimal = raw.contains(".");
        double value = Double.parseDouble(raw.replace(",", ""));
        List<String> out = new ArrayList<>();
        for (double delta : new double[] {value * 0.1, value * 0.2, 1, 2, 5, 10}) {
            double v = value + (decimal ? Math.max(0.5, delta) : Math.max(1, Math.round(delta)));
            String text = decimal ? String.format(Locale.ROOT, "%." + (raw.length() - raw.indexOf('.') - 1) + "f", v)
                    : commas ? String.format(Locale.ROOT, "%,d", (long) v) : String.valueOf((long) v);
            out.add(answer.substring(0, m.start()) + text + answer.substring(m.end()));
        }
        return out;
    }

    static String rupees(long amount) {
        return "₹" + String.format(Locale.ROOT, "%,d", amount);
    }

    /** A number with at most two decimals, trailing zeros dropped (e.g. 16.67, 20, 37.5). */
    static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }

    static String fraction(long num, long den) {
        long g = BigInteger.valueOf(num).gcd(BigInteger.valueOf(den)).longValue();
        return (num / g) + "/" + (den / g);
    }

    static String shift(String word, int k) {
        StringBuilder out = new StringBuilder();
        for (char c : word.toCharArray()) {
            out.append((char) ('A' + Math.floorMod(c - 'A' + k, 26)));
        }
        return out.toString();
    }
}
