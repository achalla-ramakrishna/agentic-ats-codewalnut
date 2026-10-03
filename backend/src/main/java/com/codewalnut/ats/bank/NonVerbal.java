package com.codewalnut.ats.bank;

import static com.codewalnut.ats.bank.AptitudeBank.between;
import static com.codewalnut.ats.bank.AptitudeBank.pick;
import static com.codewalnut.ats.bank.AptitudeBank.pictureChoice;

import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Non-verbal reasoning generators: every question and every option is a picture (ADR-0014). */
final class NonVerbal {

    private static final Section S = Section.LOGICAL;
    private static final String FILLED = Svg.COLORS[0];
    private static final String EMPTY = "#ffffff";
    /**
     * Letters and digits with no line of symmetry and no half-turn symmetry, so the mirror image, water
     * image, half-turn and reversed word all look different.
     */
    private static final String ASYMMETRIC = "FGJLPQR24579";

    private NonVerbal() {}

    // ---------------- Figure series ----------------

    static Seed figureSeries(String key, Difficulty d, Random r) {
        String t = "Figure series";
        String prompt = "Which figure comes next in the series?";
        if (d == Difficulty.EASY) {
            if (r.nextInt(3) > 0) {
                int start = 45 * r.nextInt(8);
                int step = pick(r, List.of(45, 90, 135, -45, -90));
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    cells.add(Svg.arrowCell(start + i * step));
                }
                cells.add(null);
                return pictureChoice(key, S, t, d, prompt, Svg.series(cells), cellArrow(start + 4 * step),
                        List.of(cellArrow(start + 3 * step), cellArrow(start + 5 * step), cellArrow(start + 4 * step + 180),
                                cellArrow(start + 4 * step + 90), mirroredArrow(start + 4 * step)),
                        "The arrow turns " + Math.abs(step) + "° " + (step > 0 ? "clockwise" : "anticlockwise") + " each step.");
            }
            int start = between(r, 1, 5);
            int step = start == 1 && r.nextBoolean() ? 2 : 1;
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                cells.add(Svg.dots(start + i * step));
            }
            cells.add(null);
            int next = start + 4 * step;
            return pictureChoice(key, S, t, d, prompt, Svg.series(cells), Svg.cell(Svg.dots(next)), dotOptions(next),
                    "One more " + (step == 1 ? "dot" : "pair of dots") + " each step: " + next + " dots.");
        }
        if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int start = 45 * r.nextInt(8);
                int a = pick(r, List.of(45, 90, -45, -90, 135));
                int b = pick(r, List.of(45, 90, 180, -45, -90));
                if (a == b) {
                    return figureSeries(key, d, r);
                }
                List<String> cells = new ArrayList<>();
                int angle = start;
                for (int i = 0; i < 5; i++) {
                    cells.add(Svg.arrowCell(angle));
                    angle += i % 2 == 0 ? a : b;
                }
                cells.add(null);
                return pictureChoice(key, S, t, d, prompt, Svg.series(cells), cellArrow(angle),
                        List.of(cellArrow(angle + a), cellArrow(angle - b), cellArrow(angle + 180), cellArrow(angle + 90), mirroredArrow(angle)),
                        "The turns alternate: " + turn(a) + ", then " + turn(b) + ". The next turn is " + turn(a) + ".");
            }
            int sides = 3;
            int rot = 15 * r.nextInt(6);
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                cells.add(Svg.polygon(sides + i, rot, i % 2 == 0 ? FILLED : EMPTY));
            }
            cells.add(null);
            int next = sides + 4;
            String fill = FILLED;
            return pictureChoice(key, S, t, d, prompt, Svg.series(cells), Svg.cell(Svg.polygon(next, rot, fill)),
                    List.of(Svg.cell(Svg.polygon(next, rot, EMPTY)), Svg.cell(Svg.polygon(next - 1, rot, fill)), Svg.cell(Svg.polygon(next - 2, rot, fill)),
                            Svg.cell(Svg.polygon(next - 1, rot, EMPTY))),
                    "Each figure has one more side than the one before, and the shading alternates: next is a shaded figure with " + next + " sides.");
        }
        if (r.nextBoolean()) {
            int start = 45 * r.nextInt(8);
            int base = pick(r, List.of(45, -45));
            List<String> cells = new ArrayList<>();
            int angle = start;
            for (int i = 0; i < 4; i++) {
                cells.add(Svg.arrowCell(angle));
                angle += base * (i + 1);
            }
            cells.add(null);
            return pictureChoice(key, S, t, d, prompt, Svg.series(cells), cellArrow(angle),
                    List.of(cellArrow(angle - base), cellArrow(angle + base), cellArrow(angle + 180), mirroredArrow(angle)),
                    "The turn grows each step: 45°, 90°, 135°, then 180° " + (base > 0 ? "clockwise" : "anticlockwise") + ".");
        }
        int dotsStart = between(r, 1, 3);
        int sides = 3;
        int rot = 15 * r.nextInt(6);
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cells.add(withDots(sides + i, rot, dotsStart + i));
        }
        cells.add(null);
        int next = sides + 4;
        int dots = dotsStart + 4;
        return pictureChoice(key, S, t, d, prompt, Svg.series(cells), Svg.cell(withDots(next, rot, dots)),
                List.of(Svg.cell(withDots(next, rot, dots - 1)), Svg.cell(withDots(next - 1, rot, dots)), Svg.cell(withDots(next + 1, rot, dots + 1)),
                        Svg.cell(withDots(next, rot, dots + 1))),
                "Two things change together: one more side and one more dot each step, so " + next + " sides and " + dots + " dots.");
    }

    private static String turn(int degrees) {
        return Math.abs(degrees) + "° " + (degrees > 0 ? "clockwise" : "anticlockwise");
    }

    private static String cellArrow(int angle) {
        return Svg.cell(Svg.arrowCell(angle));
    }

    /** The arrow flipped left-to-right: never one of the rotations of the original. */
    private static String mirroredArrow(int angle) {
        return Svg.cell("<g transform=\"translate(100 0) scale(-1 1)\">" + Svg.arrowCell(-angle) + "</g>");
    }

    /** A polygon with small dots in its middle. */
    private static String withDots(int sides, int rot, int dots) {
        return Svg.polygon(sides, rot, EMPTY) + "<g transform=\"translate(25 25) scale(0.5)\">" + Svg.dots(dots) + "</g>";
    }

    private static List<String> dotOptions(int n) {
        List<String> out = new ArrayList<>();
        for (int delta : new int[] {1, -1, 2, -2, 3, -3}) {
            int v = n + delta;
            if (v >= 1 && v <= 9) {
                out.add(Svg.cell(Svg.dots(v)));
            }
        }
        return out;
    }

    // ---------------- Mirror and water images ----------------

    static Seed mirror(String key, Difficulty d, Random r) {
        String t = "Mirror and water images";
        int length = d == Difficulty.EASY ? 3 : d == Difficulty.MEDIUM ? 4 : between(r, 5, 6);
        StringBuilder w = new StringBuilder();
        while (w.length() < length) {
            char c = ASYMMETRIC.charAt(r.nextInt(ASYMMETRIC.length()));
            if (w.indexOf(String.valueOf(c)) < 0) {
                w.append(c);
            }
        }
        String word = w.toString();
        String swapped = word.charAt(1) + "" + word.charAt(0) + word.substring(2);
        boolean water = d == Difficulty.HARD ? r.nextBoolean() : d == Difficulty.MEDIUM && r.nextInt(3) == 0;
        String mode = water ? "WATER" : "MIRROR";
        String other = water ? "MIRROR" : "WATER";
        return pictureChoice(key, S, t, d,
                water ? "Which option is the water image of " + word + " (water below the word)?"
                        : "Which option is the mirror image of " + word + " (mirror placed to the right)?",
                Svg.word(word, "PLAIN"), Svg.word(word, mode),
                List.of(Svg.word(word, other), Svg.word(swapped, mode), Svg.word(word, "ROTATE"), Svg.word(Logic.reverse(word), "PLAIN")),
                water ? "A water image flips the word top-to-bottom: letters keep their left-to-right order but turn upside down."
                        : "A mirror on the right flips the word left-to-right: the order of the letters reverses and each letter faces the other way.");
    }

    // ---------------- Pattern matrix ----------------

    static Seed matrix(String key, Difficulty d, Random r) {
        String t = "Pattern matrix";
        String prompt = "Which figure replaces the question mark?";
        int[][] g = new int[3][3];
        String rule;
        if (d == Difficulty.EASY) {
            boolean across = r.nextBoolean();
            boolean up = r.nextBoolean();
            int base = between(r, 1, 7);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int k = across ? j : i;
                    g[i][j] = up ? base + k : base + 2 - k;
                }
            }
            rule = "Each " + (across ? "row" : "column") + " has one dot " + (up ? "more" : "fewer") + " at each step "
                    + (across ? "to the right" : "downwards") + ".";
        } else if (d == Difficulty.MEDIUM) {
            int a;
            int rs;
            int cs;
            do {
                a = between(r, 1, 4);
                rs = between(r, 1, 3);
                cs = between(r, 1, 2);
            } while (a + 2 * rs + 2 * cs > 9 || rs == cs && r.nextBoolean());
            boolean flipRows = r.nextBoolean();
            boolean flipCols = r.nextBoolean();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    g[i][j] = a + (flipRows ? 2 - i : i) * rs + (flipCols ? 2 - j : j) * cs;
                }
            }
            rule = "Moving right " + (flipCols ? "removes " : "adds ") + cs + " dot" + (cs == 1 ? "" : "s") + "; moving down "
                    + (flipRows ? "removes " : "adds ") + rs + ".";
        } else {
            boolean sum = r.nextBoolean();
            for (int i = 0; i < 3; i++) {
                int x;
                int y;
                do {
                    x = between(r, 1, 8);
                    y = between(r, 1, 8);
                } while (sum ? x + y > 9 : x == y);
                g[i][0] = x;
                g[i][1] = y;
                g[i][2] = sum ? x + y : Math.abs(x - y);
            }
            rule = sum ? "In each row the third figure has as many dots as the first two together."
                    : "In each row the third figure has the difference between the first two.";
        }
        int answer = g[2][2];
        return pictureChoice(key, S, t, d, prompt, Svg.dotMatrix(g), Svg.cell(Svg.dots(answer)), dotOptions(answer),
                rule + " So the missing figure has " + answer + " dot" + (answer == 1 ? "" : "s") + ".");
    }

    // ---------------- Odd one out ----------------

    static Seed oddOneOut(String key, Difficulty d, Random r) {
        String t = "Odd one out";
        String prompt = "Which figure is the odd one out?";
        List<String> same = new ArrayList<>();
        String odd;
        String why;
        if (d == Difficulty.EASY) {
            int sides = between(r, 3, 6);
            int other;
            do {
                other = between(r, 3, 7);
            } while (other == sides);
            String fill = r.nextBoolean() ? FILLED : EMPTY;
            List<Integer> rots = rotations(r, 4);
            for (int i = 0; i < 3; i++) {
                same.add(Svg.cell(Svg.polygon(sides, rots.get(i), fill)));
            }
            odd = Svg.cell(Svg.polygon(other, rots.get(3), fill));
            why = "Three figures have " + sides + " sides; the odd one has " + other + ".";
        } else if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int sides = between(r, 3, 6);
                boolean oddFilled = r.nextBoolean();
                List<Integer> rots = rotations(r, 4);
                for (int i = 0; i < 3; i++) {
                    same.add(Svg.cell(Svg.polygon(sides, rots.get(i), oddFilled ? EMPTY : FILLED)));
                }
                odd = Svg.cell(Svg.polygon(sides, rots.get(3), oddFilled ? FILLED : EMPTY));
                why = "Three figures are " + (oddFilled ? "empty" : "shaded") + "; the odd one is " + (oddFilled ? "shaded" : "empty") + ".";
            } else {
                boolean even = r.nextBoolean();
                List<Integer> pool = new ArrayList<>(even ? List.of(2, 4, 6, 8) : List.of(1, 3, 5, 7, 9));
                java.util.Collections.shuffle(pool, r);
                for (int i = 0; i < 3; i++) {
                    same.add(Svg.cell(Svg.dots(pool.get(i))));
                }
                int o = even ? pick(r, List.of(1, 3, 5, 7, 9)) : pick(r, List.of(2, 4, 6, 8));
                odd = Svg.cell(Svg.dots(o));
                why = "Three figures have an " + (even ? "even" : "odd") + " number of dots; the odd one has " + o + ".";
            }
        } else {
            if (r.nextBoolean()) {
                List<Integer> angles = new ArrayList<>(List.of(0, 45, 90, 135, 180, 225, 270, 315));
                java.util.Collections.shuffle(angles, r);
                for (int i = 0; i < 3; i++) {
                    same.add(cellArrow(angles.get(i)));
                }
                odd = mirroredArrow(angles.get(3));
                why = "Three figures are the same arrow turned round; the odd one is its mirror image (the dot is on the other side) and cannot be made by turning.";
            } else {
                List<Integer> sidesPool = new ArrayList<>(List.of(3, 4, 5, 6, 7));
                java.util.Collections.shuffle(sidesPool, r);
                int rot = 15 * r.nextInt(6);
                for (int i = 0; i < 3; i++) {
                    int n = sidesPool.get(i);
                    same.add(Svg.cell(withDots(n, rot, n)));
                }
                int n = sidesPool.get(3);
                int wrong = n + (r.nextBoolean() ? 1 : -1);
                odd = Svg.cell(withDots(n, rot, wrong));
                why = "In three figures the number of dots equals the number of sides; the odd one has " + n + " sides but " + wrong + " dots.";
            }
        }
        return pictureChoice(key, S, t, d, prompt, null, odd, same, why);
    }

    private static List<Integer> rotations(Random r, int n) {
        List<Integer> rots = new ArrayList<>(List.of(0, 10, 20, 30, 40, 50));
        java.util.Collections.shuffle(rots, r);
        return rots.subList(0, n);
    }
}
