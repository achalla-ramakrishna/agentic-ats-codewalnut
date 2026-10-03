package com.codewalnut.ats.bank;

import static com.codewalnut.ats.bank.AptitudeBank.between;
import static com.codewalnut.ats.bank.AptitudeBank.choice;
import static com.codewalnut.ats.bank.AptitudeBank.num;
import static com.codewalnut.ats.bank.AptitudeBank.pick;
import static com.codewalnut.ats.bank.AptitudeBank.shift;

import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Logical-reasoning generators: coding-decoding, directions, clocks and Venn diagrams (ADR-0014). */
final class Logic {

    private static final Section S = Section.LOGICAL;
    private static final List<String> WORDS = List.of("CODE", "WORK", "TEAM", "GAME", "BOOK", "LAMP", "MILK", "ROAD", "SHIP", "TREE", "GOLD",
            "FISH", "BIRD", "DESK", "PARK", "RAIN", "STAR", "WIND", "COLD", "HAND", "BLUE", "PLAN", "DATA", "JAVA", "NOTE", "CAKE", "KING", "MOON",
            "LION", "PATH", "BRAIN", "CLOUD", "PAPER", "TRAIN", "MOUSE", "LIGHT", "WATER", "PLANT", "SMART", "TABLE", "CHAIR", "RIVER",
            "STONE", "OCEAN", "TIGER", "MANGO", "QUICK", "HOUSE", "FRAME", "GLOBE");
    private static final List<String> PEOPLE = List.of("Ravi", "Asha", "Kiran", "Meena", "Arjun", "Divya", "Sameer", "Priya", "Rahul", "Neha",
            "Vikram", "Anu", "Farhan", "Lakshmi", "Joseph", "Gita");

    private Logic() {}

    // ---------------- Coding-decoding ----------------

    static Seed coding(String key, Difficulty d, Random r) {
        String t = "Coding-decoding";
        String a = pick(r, WORDS);
        String b;
        do {
            b = pick(r, WORDS);
        } while (b.equals(a) || b.length() != a.length());
        if (d == Difficulty.EASY) {
            int k = pick(r, List.of(-3, -2, -1, 1, 2, 3, 4, 5));
            return choice(key, S, t, d, "In a certain code, " + a + " is written as " + shift(a, k) + ". How is " + b + " written in that code?",
                    null, shift(b, k), List.of(shift(b, k + Integer.signum(k)), shift(b, -k), reverse(shift(b, k)), shift(b, 2 * k + Integer.signum(k))),
                    "Each letter moves " + Math.abs(k) + " place" + (Math.abs(k) == 1 ? "" : "s") + (k > 0 ? " forward" : " back")
                            + " in the alphabet: " + b + " → " + shift(b, k) + ".");
        }
        if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int k = pick(r, List.of(-2, -1, 1, 2, 3));
                String code = reverse(shift(a, k));
                return choice(key, S, t, d, "In a certain code, " + a + " is written as " + code + ". How is " + b + " written in that code?",
                        null, reverse(shift(b, k)), List.of(shift(b, k), reverse(shift(b, k + Integer.signum(k))), reverse(shift(b, -k)), reverse(shift(b, 2 * k))),
                        "The word is reversed and each letter moves " + Math.abs(k) + (k > 0 ? " forward" : " back") + ": " + b + " → "
                                + reverse(shift(b, k)) + ".");
            }
            return choice(key, S, t, d, "In a certain code, " + a + " is written as " + opposite(a) + ". How is " + b + " written in that code?",
                    null, opposite(b), List.of(reverse(opposite(b)), shift(opposite(b), 1), shift(b, 13), shift(opposite(b), -1)),
                    "Each letter is replaced by its opposite (A↔Z, B↔Y, C↔X …): " + b + " → " + opposite(b) + ".");
        }
        if (r.nextBoolean()) {
            return choice(key, S, t, d, "If " + a + " is coded as " + positionSum(a) + " (adding the alphabet positions of its letters), what is the code for "
                    + b + "?", null, String.valueOf(positionSum(b)),
                    List.of(String.valueOf(positionSum(b) + 2), String.valueOf(positionSum(b) - 3), String.valueOf(positionSum(b) + 5)),
                    "Add the positions: " + spell(b) + " = " + positionSum(b) + ".");
        }
        boolean down = r.nextBoolean();
        return choice(key, S, t, d, "In a certain code, " + a + " is written as " + growing(a, down) + ". How is " + b + " written in that code?",
                null, growing(b, down), List.of(shift(b, 1), growing(b, !down), reverse(growing(b, down)), shift(growing(b, down), 1)),
                "The 1st letter moves " + (down ? "back" : "forward") + " 1, the 2nd 2, the 3rd 3 and so on: " + b + " → " + growing(b, down) + ".");
    }

    static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    static String opposite(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append((char) ('Z' - (c - 'A')));
        }
        return out.toString();
    }

    static int positionSum(String s) {
        int sum = 0;
        for (char c : s.toCharArray()) {
            sum += c - 'A' + 1;
        }
        return sum;
    }

    private static String spell(String s) {
        List<String> parts = new ArrayList<>();
        for (char c : s.toCharArray()) {
            parts.add(String.valueOf(c - 'A' + 1));
        }
        return String.join(" + ", parts);
    }

    private static String growing(String s, boolean down) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            out.append(shift(String.valueOf(s.charAt(i)), down ? -(i + 1) : i + 1));
        }
        return out.toString();
    }

    // ---------------- Direction sense ----------------

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

    static Seed directions(String key, Difficulty d, Random r) {
        String t = "Direction sense";
        String who = pick(r, PEOPLE);
        int legs = d == Difficulty.EASY ? 2 : d == Difficulty.MEDIUM ? 3 : 4;
        while (true) {
            int facing = r.nextInt(4);
            int x = 0;
            int y = 0;
            StringBuilder walk = new StringBuilder(who + " walks ");
            for (int i = 0; i < legs; i++) {
                int km = between(r, 2, 15);
                if (i > 0) {
                    boolean right = r.nextBoolean();
                    facing = Math.floorMod(facing + (right ? 1 : -1), 4);
                    walk.append(i == legs - 1 && legs > 2 ? ", and finally turns " : i == 1 ? ", then turns " : ", turns ").append(right ? "right" : "left").append(" and walks ");
                }
                walk.append(km).append(" km");
                if (i == 0) {
                    walk.append(" ").append(DIRS[facing]);
                }
                x += STEP[facing][0] * km;
                y += STEP[facing][1] * km;
            }
            double dist = Math.hypot(x, y);
            if (dist == 0 || Math.abs(dist - Math.rint(dist)) > 1e-9) {
                continue;
            }
            String where = direction(x, y);
            String text = walk + ".";
            String km = num(dist) + " km";
            if (d == Difficulty.EASY || (d == Difficulty.MEDIUM && r.nextBoolean())) {
                return choice(key, S, t, d, text + " How far is " + who + " from the starting point?", null, km,
                        List.of(num(Math.abs(x) + Math.abs(y)) + " km", num(dist + 2) + " km", num(Math.max(1, dist - 3)) + " km",
                                num(Math.abs(Math.abs(x) - Math.abs(y)) + 1) + " km"),
                        "Net movement: " + Math.abs(x) + " km " + (x >= 0 ? "east" : "west") + " and " + Math.abs(y) + " km "
                                + (y >= 0 ? "north" : "south") + "; distance = √(" + x * x + " + " + y * y + ") = " + km + ".");
            }
            if (d == Difficulty.MEDIUM) {
                return choice(key, S, t, d, text + " In which direction is " + who + " now from the starting point?", null, cap(where),
                        List.of(cap(direction(-x, -y)), cap(direction(y, -x)), cap(direction(-y, x)), cap(direction(x, -y))),
                        "Net movement: " + Math.abs(x) + " km " + (x >= 0 ? "east" : "west") + " and " + Math.abs(y) + " km "
                                + (y >= 0 ? "north" : "south") + ", so " + where + " of the start.");
            }
            String ans = km + ", " + cap(where);
            return choice(key, S, t, d, text + " How far and in which direction is " + who + " from the starting point?", null, ans,
                    List.of(km + ", " + cap(direction(-x, -y)), num(Math.abs(x) + Math.abs(y)) + " km, " + cap(where),
                            km + ", " + cap(direction(y, -x)), num(dist + 1) + " km, " + cap(direction(-y, x))),
                    "Net movement: " + Math.abs(x) + " km " + (x >= 0 ? "east" : "west") + " and " + Math.abs(y) + " km "
                            + (y >= 0 ? "north" : "south") + "; distance = √(" + x * x + " + " + y * y + ") = " + km + ", towards the "
                            + where  + ".");
        }
    }

    static String direction(int x, int y) {
        String ns = y > 0 ? "north" : y < 0 ? "south" : "";
        String ew = x > 0 ? "east" : x < 0 ? "west" : "";
        return ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
    }

    private static String cap(String s) {
        StringBuilder out = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            out.append(up ? Character.toUpperCase(c) : c);
            up = c == '-';
        }
        return out.toString();
    }

    // ---------------- Clocks ----------------

    static Seed clocks(String key, Difficulty d, Random r) {
        String t = "Clocks";
        if (d == Difficulty.EASY) {
            int h = between(r, 1, 12);
            int m = 5 * r.nextInt(12);
            int swappedH = m == 0 ? 12 : m / 5;
            int swappedM = (h % 12) * 5;
            return choice(key, S, t, d, "What time does the clock show?", Svg.clock(h, m), time(h, m),
                    List.of(time(swappedH, swappedM), time(h % 12 + 1, m), time(h, (m + 30) % 60), time(h == 1 ? 12 : h - 1, (60 - m) % 60)),
                    "The short hand points to the hour (" + h + ") and the long hand to the minutes (" + m + ").");
        }
        if (d == Difficulty.MEDIUM) {
            int h = between(r, 1, 12);
            int m = 5 * r.nextInt(12);
            double angle = angle(h, m);
            boolean picture = r.nextBoolean();
            String ans = num(angle) + "°";
            return choice(key, S, t, d, picture ? "What is the smaller angle between the hands of this clock?"
                    : "What is the angle between the hour and minute hands of a clock at " + time(h, m) + "?",
                    picture ? Svg.clock(h, m) : null, ans,
                    List.of(num(Math.abs(30 * (h % 12) - 6 * m) % 360 > 180 ? 360 - Math.abs(30 * (h % 12) - 6 * m) % 360
                            : Math.abs(30 * (h % 12) - 6 * m) % 360) + "°", num(360 - angle) + "°", num(angle + 15) + "°", num(Math.abs(angle - 7.5)) + "°"),
                    "Angle = |30 × h − 5.5 × m| = |30 × " + (h % 12) + " − 5.5 × " + m + "| = " + num(Math.abs(30 * (h % 12) - 5.5 * m))
                            + "°" + (Math.abs(30 * (h % 12) - 5.5 * m) > 180 ? ", and the smaller angle is 360° − that = " + ans : "") + ".");
        }
        int kind = r.nextInt(3);
        if (kind == 0) {
            int h = between(r, 1, 12);
            int m = between(r, 1, 59);
            int total = 12 * 60 - ((h % 12) * 60 + m);
            int ah = total / 60 == 0 ? 12 : total / 60;
            int am = total % 60;
            return choice(key, S, t, d, "Looking at a clock in a mirror, the time appears to be " + time(h, m) + ". What is the actual time?", null,
                    time(ah, am), List.of(time(h, 60 - m == 60 ? 0 : 60 - m), time(12 - h == 0 ? 12 : 12 - h, m), time(ah == 12 ? 1 : ah + 1, am),
                            time(ah, (am + 30) % 60)),
                    "Actual time = 11:60 − mirror time = 11:60 − " + time(h, m) + " = " + time(ah, am) + ".");
        }
        if (kind == 1) {
            int h = between(r, 1, 10);
            int num = 60 * h;
            String ans = (num / 11) + (num % 11 == 0 ? "" : " " + (num % 11) + "/11") + " minutes past " + h;
            return choice(key, S, t, d, "At what time between " + h + " and " + (h + 1) + " o'clock are the hands of a clock together?", null, ans,
                    List.of((5 * h) + " minutes past " + h, (5 * h + 1) + " minutes past " + h, ((num + 60) / 11) + " " + ((num + 60) % 11) + "/11 minutes past " + h,
                            (num / 11) + " " + Math.max(1, (num % 11) - 1) + "/11 minutes past " + h),
                    "The minute hand gains 5.5° a minute on the hour hand and must catch up 30 × " + h + " = " + 30 * h + "°: " + 30 * h
                            + " / 5.5 = " + ans.replace(" minutes past " + h, "") + " minutes.");
        }
        int h = between(r, 1, 12);
        int m = between(r, 1, 59);
        double angle = angle(h, m);
        String ans = num(angle) + "°";
        return choice(key, S, t, d, "What is the smaller angle between the hands of a clock at " + time(h, m) + "?", null, ans,
                List.of(num(Math.abs(30 * (h % 12) - 6 * m) % 360 > 180 ? 360 - Math.abs(30 * (h % 12) - 6 * m) % 360
                        : Math.abs(30 * (h % 12) - 6 * m) % 360) + "°", num(Math.abs(angle - 10)) + "°", num(angle + 12.5) + "°", num(360 - angle) + "°"),
                "Angle = |30 × " + (h % 12) + " − 5.5 × " + m + "| = " + num(Math.abs(30 * (h % 12) - 5.5 * m)) + "°"
                        + (Math.abs(30 * (h % 12) - 5.5 * m) > 180 ? "; the smaller angle is 360° − that = " + ans : "") + ".");
    }

    static double angle(int h, int m) {
        double a = Math.abs(30 * (h % 12) - 5.5 * m);
        return a > 180 ? 360 - a : a;
    }

    static String time(int h, int m) {
        return h + ":" + (m < 10 ? "0" : "") + m;
    }

    // ---------------- Venn diagrams ----------------

    private static final List<String[]> PAIRS = List.of(new String[] {"Tea", "Coffee"}, new String[] {"Cricket", "Football"},
            new String[] {"Java", "Python"}, new String[] {"Maths", "Science"}, new String[] {"Hindi", "English"}, new String[] {"Music", "Dance"},
            new String[] {"Reading", "Travel"}, new String[] {"Bus", "Metro"}, new String[] {"Apples", "Mangoes"}, new String[] {"Chess", "Carrom"});
    private static final List<String[]> TRIPLES = List.of(new String[] {"Java", "Python", "SQL"}, new String[] {"Tea", "Coffee", "Juice"},
            new String[] {"Cricket", "Football", "Hockey"}, new String[] {"Hindi", "English", "Tamil"}, new String[] {"Maths", "Physics", "Chemistry"},
            new String[] {"Bus", "Metro", "Bike"}, new String[] {"Music", "Dance", "Drama"}, new String[] {"React", "Angular", "Vue"});

    static Seed venn(String key, Difficulty d, Random r) {
        String t = "Venn diagrams";
        if (d == Difficulty.EASY) {
            String[] p = pick(r, PAIRS);
            int a = between(r, 5, 60);
            int both = between(r, 3, 40);
            int b = between(r, 5, 60);
            int none = between(r, 2, 30);
            String fig = Svg.venn2(p[0], p[1], a, both, b, none);
            String intro = "The diagram shows how many people in a group like " + p[0] + " and " + p[1] + " (the number outside the circles likes neither). ";
            return switch (r.nextInt(5)) {
                case 0 -> choice(key, S, t, d, intro + "How many like " + p[0] + " but not " + p[1] + "?", fig, String.valueOf(a),
                        List.of(String.valueOf(a + both), String.valueOf(both), String.valueOf(b)), "Only the " + p[0] + "-only region: " + a + ".");
                case 1 -> choice(key, S, t, d, intro + "How many like " + p[1] + " in all?", fig, String.valueOf(b + both),
                        List.of(String.valueOf(b), String.valueOf(a + both), String.valueOf(a + b + both)), b + " + " + both + " = " + (b + both) + ".");
                case 2 -> choice(key, S, t, d, intro + "How many like both?", fig, String.valueOf(both),
                        List.of(String.valueOf(a + b), String.valueOf(a), String.valueOf(none)), "The overlap: " + both + ".");
                case 3 -> choice(key, S, t, d, intro + "How many people are in the group?", fig, String.valueOf(a + b + both + none),
                        List.of(String.valueOf(a + b + both), String.valueOf(a + b + 2 * both + none), String.valueOf(a + b + none)),
                        a + " + " + both + " + " + b + " + " + none + " = " + (a + b + both + none) + ".");
                default -> choice(key, S, t, d, intro + "How many like exactly one of the two?", fig, String.valueOf(a + b),
                        List.of(String.valueOf(a + b + both), String.valueOf(both), String.valueOf(a + b - both)), a + " + " + b + " = " + (a + b) + ".");
            };
        }
        if (d == Difficulty.MEDIUM) {
            String[] p = pick(r, TRIPLES);
            int[] g = new int[7];
            for (int i = 0; i < 7; i++) {
                g[i] = between(r, i < 3 ? 6 : 2, i < 3 ? 40 : 15);
            }
            String fig = Svg.venn3(p[0], p[1], p[2], g);
            String intro = "The diagram shows how many students picked " + p[0] + ", " + p[1] + " and " + p[2] + " in a survey (some picked more than one). ";
            int exactlyOne = g[0] + g[1] + g[2];
            int exactlyTwo = g[3] + g[4] + g[5];
            return switch (r.nextInt(5)) {
                case 0 -> choice(key, S, t, d, intro + "How many picked exactly one of the three?", fig, String.valueOf(exactlyOne),
                        List.of(String.valueOf(exactlyOne + g[6]), String.valueOf(exactlyTwo), String.valueOf(exactlyOne + exactlyTwo)),
                        g[0] + " + " + g[1] + " + " + g[2] + " = " + exactlyOne + ".");
                case 1 -> choice(key, S, t, d, intro + "How many picked exactly two of the three?", fig, String.valueOf(exactlyTwo),
                        List.of(String.valueOf(exactlyTwo + g[6]), String.valueOf(exactlyTwo + 3 * g[6]), String.valueOf(exactlyOne)),
                        g[3] + " + " + g[4] + " + " + g[5] + " = " + exactlyTwo + ".");
                case 2 -> choice(key, S, t, d, intro + "How many picked " + p[0] + " in all?", fig, String.valueOf(g[0] + g[3] + g[5] + g[6]),
                        List.of(String.valueOf(g[0]), String.valueOf(g[0] + g[3] + g[5]), String.valueOf(g[0] + g[6])),
                        g[0] + " + " + g[3] + " + " + g[5] + " + " + g[6] + " = " + (g[0] + g[3] + g[5] + g[6]) + ".");
                case 3 -> choice(key, S, t, d, intro + "How many picked " + p[0] + " and " + p[1] + " but not " + p[2] + "?", fig, String.valueOf(g[3]),
                        List.of(String.valueOf(g[3] + g[6]), String.valueOf(g[6]), String.valueOf(g[4])), "The " + p[0] + "–" + p[1] + " overlap outside "
                                + p[2] + ": " + g[3] + ".");
                default -> choice(key, S, t, d, intro + "How many picked " + p[1] + " but not " + p[2] + "?", fig, String.valueOf(g[1] + g[3]),
                        List.of(String.valueOf(g[1]), String.valueOf(g[1] + g[3] + g[4]), String.valueOf(g[1] + g[4])),
                        p[1] + " only (" + g[1] + ") + " + p[0] + " and " + p[1] + " only (" + g[3] + ") = " + (g[1] + g[3]) + ".");
            };
        }
        if (r.nextBoolean()) {
            String[] p = pick(r, PAIRS);
            int total = 10 * between(r, 6, 30);
            int a = between(r, total / 4, total / 2 + 10);
            int b = between(r, total / 4, total / 2 + 10);
            int both = between(r, Math.max(1, a + b - total + 1), Math.min(a, b) - 1);
            int neither = total - (a + b - both);
            return choice(key, S, t, d, "In a group of " + total + " people, " + a + " like " + p[0] + ", " + b + " like " + p[1] + " and " + both
                    + " like both. How many like neither?", null, String.valueOf(neither),
                    List.of(String.valueOf(total - a - b < 0 ? neither + both : total - a - b), String.valueOf(neither + both), String.valueOf(Math.max(0, neither - both))),
                    "At least one = " + a + " + " + b + " − " + both + " = " + (a + b - both) + "; neither = " + total + " − " + (a + b - both) + " = "
                            + neither + ".");
        }
        String[] p = pick(r, TRIPLES);
        int[] g = new int[7];
        for (int i = 0; i < 7; i++) {
            g[i] = between(r, i < 3 ? 5 : 2, i < 3 ? 35 : 14);
        }
        String fig = Svg.venn3(p[0], p[1], p[2], g);
        String intro = "The diagram shows how many students picked " + p[0] + ", " + p[1] + " and " + p[2] + " in a survey (some picked more than one). ";
        int atLeastTwo = g[3] + g[4] + g[5] + g[6];
        int sum = 0;
        for (int v : g) {
            sum += v;
        }
        return switch (r.nextInt(3)) {
            case 0 -> choice(key, S, t, d, intro + "How many picked at least two of the three?", fig, String.valueOf(atLeastTwo),
                    List.of(String.valueOf(atLeastTwo - g[6]), String.valueOf(atLeastTwo + g[6] * 2), String.valueOf(sum - atLeastTwo)),
                    g[3] + " + " + g[4] + " + " + g[5] + " + " + g[6] + " = " + atLeastTwo + ".");
            case 1 -> choice(key, S, t, d, intro + "How many picked " + p[0] + " or " + p[1] + " but not " + p[2] + "?", fig,
                    String.valueOf(g[0] + g[1] + g[3]), List.of(String.valueOf(g[0] + g[1]), String.valueOf(g[0] + g[1] + g[3] + g[6]),
                            String.valueOf(sum - g[2])),
                    g[0] + " + " + g[1] + " + " + g[3] + " = " + (g[0] + g[1] + g[3]) + ".");
            default -> {
                int a = g[0] + g[3] + g[5] + g[6];
                yield choice(key, S, t, d, intro + "What percentage of the students who picked " + p[0] + " also picked " + p[2] + "?", fig,
                        num((g[5] + g[6]) * 100.0 / a) + "%", List.of(num(g[5] * 100.0 / a) + "%", num((g[5] + g[6]) * 100.0 / sum) + "%",
                                num(g[6] * 100.0 / a) + "%", num((g[5] + g[6]) * 100.0 / a + 5) + "%"),
                        p[0] + " in all = " + a + "; of them " + (g[5] + g[6]) + " also picked " + p[2] + ": " + (g[5] + g[6]) + " / " + a + " × 100 = "
                                + num((g[5] + g[6]) * 100.0 / a) + "%.");
            }
        };
    }
}
