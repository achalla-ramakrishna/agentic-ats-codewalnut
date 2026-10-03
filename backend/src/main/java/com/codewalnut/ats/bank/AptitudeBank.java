package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.AssessmentQuestion.Kind;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * CodeWalnut's built-in aptitude bank for freshers (ADR-0014), modelled on the common campus-test
 * pattern (TCS NQT, Infosys, Wipro NLTH, Cognizant GenC, Accenture): numerical ability with data
 * interpretation, logical reasoning with picture-based non-verbal questions, and verbal ability.
 * Numeric questions are generated from seeded numbers so the picture, the options and the answer
 * always agree; each template yields two easy, two medium and two hard questions.
 */
public final class AptitudeBank {

    /** Bump to regenerate keys if templates change in a way that alters existing questions. */
    static final String VERSION = "v1";
    static final int PER_TEMPLATE = 6;

    private AptitudeBank() {}

    public static List<Seed> all() {
        List<Seed> out = new ArrayList<>();
        generate(out, "percent", AptitudeBank::percentages);
        generate(out, "profit", AptitudeBank::profitLoss);
        generate(out, "work", AptitudeBank::timeWork);
        generate(out, "train", AptitudeBank::trains);
        generate(out, "interest", AptitudeBank::interest);
        generate(out, "ratio", AptitudeBank::ratio);
        generate(out, "average", AptitudeBank::averages);
        generate(out, "series", AptitudeBank::numberSeries);
        generate(out, "probability", AptitudeBank::probability);
        generate(out, "di-bar", AptitudeBank::barChart);
        generate(out, "di-pie", AptitudeBank::pieChart);
        generate(out, "di-line", AptitudeBank::lineChart);
        generate(out, "di-table", AptitudeBank::table);
        generate(out, "coding", AptitudeBank::coding);
        generate(out, "direction", AptitudeBank::directions);
        generate(out, "clock", AptitudeBank::clock);
        generate(out, "venn", AptitudeBank::venn);
        generate(out, "nv-series", AptitudeBank::figureSeries);
        generate(out, "nv-mirror", AptitudeBank::mirror);
        generate(out, "nv-matrix", AptitudeBank::matrix);
        generate(out, "nv-odd", AptitudeBank::oddOneOut);
        out.addAll(AptitudeWritten.all());
        return out;
    }

    private static void generate(List<Seed> out, String name, BiFunction<String, Integer, Seed> template) {
        for (int i = 0; i < PER_TEMPLATE; i++) {
            out.add(template.apply(VERSION + ":" + name + ":" + i, i));
        }
    }

    static Difficulty level(int i) {
        return i < 2 ? Difficulty.EASY : i < 4 ? Difficulty.MEDIUM : Difficulty.HARD;
    }

    static Random random(String key) {
        return new Random(key.hashCode() * 31L + 7);
    }

    /** A single-answer choice question with the right answer shuffled among distinct distractors. */
    static Seed choice(String key, Section section, String topic, int i, String prompt, String figure, String answer,
            List<String> distractors, String explanation) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(answer);
        for (String d : distractors) {
            if (options.size() == 4) {
                break;
            }
            options.add(d);
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
        Collections.shuffle(shuffled, random(key + "#options"));
        return new Seed(key, section, topic, level(i), Kind.SINGLE_CHOICE, prompt, figure, shuffled, null,
                List.of(shuffled.indexOf(answer)), List.of(), explanation);
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d[\\d,]*(\\.\\d+)?");

    /** The answer with its number nudged (same units and format), to top up options that coincided. */
    static List<String> nearby(String answer) {
        java.util.regex.Matcher m = NUMBER.matcher(answer);
        if (!m.find()) {
            return List.of();
        }
        String raw = m.group();
        boolean commas = raw.contains(",");
        double value = Double.parseDouble(raw.replace(",", ""));
        boolean decimal = raw.contains(".");
        List<String> out = new ArrayList<>();
        for (double delta : new double[] {value * 0.1, value * 0.2, 1, 2, 5}) {
            double v = value + (decimal ? delta : Math.max(1, Math.round(delta)));
            String text = decimal ? String.format(java.util.Locale.ROOT, "%.1f", v)
                    : commas ? String.format(java.util.Locale.ROOT, "%,d", (long) v) : String.valueOf((long) v);
            out.add(answer.substring(0, m.start()) + text + answer.substring(m.end()));
        }
        return out;
    }

    /** A choice question whose options are pictures (labelled A–D). */
    static Seed pictureChoice(String key, Section section, String topic, int i, String prompt, String figure,
            String answerFigure, List<String> distractorFigures, String explanation) {
        List<String> figures = new ArrayList<>();
        figures.add(answerFigure);
        figures.addAll(distractorFigures.subList(0, 3));
        if (new LinkedHashSet<>(figures).size() != 4) {
            throw new IllegalStateException("Option pictures must differ for " + key);
        }
        Collections.shuffle(figures, random(key + "#options"));
        return new Seed(key, section, topic, level(i), Kind.SINGLE_CHOICE, prompt, figure,
                List.of("Figure A", "Figure B", "Figure C", "Figure D"), figures, List.of(figures.indexOf(answerFigure)),
                List.of(), explanation);
    }

    static String rupees(long amount) {
        return "₹" + String.format(java.util.Locale.ROOT, "%,d", amount);
    }

    static String fraction(long num, long den) {
        long g = BigInteger.valueOf(num).gcd(BigInteger.valueOf(den)).longValue();
        return (num / g) + "/" + (den / g);
    }

    static <T> T pick(Random r, List<T> list) {
        return list.get(r.nextInt(list.size()));
    }

    // ---------------- Numerical ability ----------------

    static Seed percentages(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Percentages";
        if (i < 2) {
            int p = pick(r, List.of(10, 20, 25, 40, 50, 75));
            int n = 20 * (2 + r.nextInt(19));
            int ans = p * n / 100;
            return choice(key, s, topic, i, "What is " + p + "% of " + n + "?", null, String.valueOf(ans),
                    List.of(String.valueOf(ans + n / 10), String.valueOf(ans * 2), String.valueOf(Math.max(1, ans - n / 20)),
                            String.valueOf(ans + 5)), p + "% of " + n + " = " + n + " × " + p + " / 100 = " + ans + ".");
        }
        if (i < 4) {
            int a = pick(r, List.of(10, 20, 30, 40, 50));
            int x = a * a / 100;
            return choice(key, s, topic, i, "The price of a phone is increased by " + a + "% and then decreased by " + a
                    + "%. What is the net change in its price?", null, x + "% decrease",
                    List.of("No change", x + "% increase", (2 * x) + "% decrease"),
                    "Net change = −(" + a + "×" + a + ")/100 = " + x + "%, a decrease.");
        }
        int p = pick(r, List.of(20, 25, 40, 50));
        int original = 100 * (4 + r.nextInt(12));
        int now = original * (100 + p) / 100;
        double wrong = now * (100 - p) / 100.0;
        return choice(key, s, topic, i, "After a " + p + "% increase, the price of a bicycle is " + rupees(now)
                + ". What was the price before the increase?", null, rupees(original),
                List.of(wrong == Math.rint(wrong) ? rupees((long) wrong) : "₹" + String.format(java.util.Locale.ROOT, "%,.2f", wrong),
                        rupees(original + 100), rupees(now - original / 2)),
                "Original × " + (100 + p) + "/100 = " + now + ", so original = " + now + " × 100/" + (100 + p) + " = " + original + ".");
    }

    static Seed profitLoss(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Profit and loss";
        if (i < 2) {
            int cp = 100 * pick(r, List.of(2, 3, 4, 5, 6, 8));
            int p = pick(r, List.of(10, 20, 25, 40));
            int sp = cp * (100 + p) / 100;
            return choice(key, s, topic, i, "A shopkeeper buys a bag for " + rupees(cp) + " and sells it for " + rupees(sp)
                    + ". What is his profit percentage?", null, p + "%", List.of((p + 5) + "%", (p * 2) + "%", (p - 5) + "%"),
                    "Profit = " + (sp - cp) + "; profit % = " + (sp - cp) + "/" + cp + " × 100 = " + p + "%.");
        }
        if (i < 4) {
            while (true) {
                int cp = 100 * (3 + r.nextInt(10));
                int p = pick(r, List.of(10, 20, 25, 50));
                int d = pick(r, List.of(10, 20, 25));
                int sp = cp * (100 + p) / 100;
                if ((sp * 100) % (100 - d) != 0) {
                    continue;
                }
                int mp = sp * 100 / (100 - d);
                return choice(key, s, topic, i, "An item costing " + rupees(cp) + " is marked at " + rupees(mp)
                        + " and sold at a " + d + "% discount. What is the profit percentage?", null, p + "%",
                        List.of((p + d) + "%", d + "%", (p + 5) + "%", (p - 5) + "%"),
                        "Selling price = " + mp + " × " + (100 - d) + "/100 = " + sp + "; profit = " + (sp - cp) + " on " + cp + " = " + p + "%.");
            }
        }
        while (true) {
            int cp = 100 * (2 + r.nextInt(15));
            int l = pick(r, List.of(10, 20, 25));
            int g = pick(r, List.of(10, 20, 25, 30));
            if ((cp * (100 - l)) % 100 != 0 || (cp * (100 + g)) % 100 != 0) {
                continue;
            }
            int sell = cp * (100 - l) / 100;
            int target = cp * (100 + g) / 100;
            return choice(key, s, topic, i, "By selling a watch for " + rupees(sell) + ", a seller loses " + l
                    + "%. At what price should it be sold to gain " + g + "%?", null, rupees(target),
                    List.of(rupees(sell * (100 + g) / 100), rupees(target + 50), rupees(cp)),
                    "Cost = " + sell + " × 100/" + (100 - l) + " = " + cp + "; price for " + g + "% gain = " + cp + " × " + (100 + g)
                            + "/100 = " + target + ".");
        }
    }

    static Seed timeWork(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Time and work";
        if (i < 2) {
            int[] t = pick(r, List.of(new int[] {12, 24, 8}, new int[] {10, 15, 6}, new int[] {20, 30, 12}, new int[] {6, 12, 4},
                    new int[] {15, 30, 10}, new int[] {12, 36, 9}));
            return choice(key, s, topic, i, "Asha can finish a task in " + t[0] + " days and Ravi in " + t[1]
                    + " days. In how many days can they finish it working together?", null, t[2] + " days",
                    List.of(((t[0] + t[1]) / 2) + " days", (t[2] + 2) + " days", (t[1] - t[0]) + " days", (t[2] - 1) + " days"),
                    "Together per day: 1/" + t[0] + " + 1/" + t[1] + " = 1/" + t[2] + ", so " + t[2] + " days.");
        }
        if (i < 4) {
            int[] t = pick(r, List.of(new int[] {12, 8, 24}, new int[] {15, 6, 10}, new int[] {30, 12, 20}, new int[] {12, 4, 6},
                    new int[] {20, 12, 30}, new int[] {36, 9, 12}));
            return choice(key, s, topic, i, "A and B together can build a wall in " + t[1] + " days. A alone can build it in "
                    + t[0] + " days. How long will B alone take?", null, t[2] + " days",
                    List.of((t[0] - t[1]) + " days", (t[0] + t[1]) + " days", (t[2] + 4) + " days"),
                    "B per day = 1/" + t[1] + " − 1/" + t[0] + " = 1/" + t[2] + ".");
        }
        int[] t = pick(r, List.of(new int[] {12, 24, 4, 12}, new int[] {20, 30, 6, 15}, new int[] {6, 12, 2, 6}));
        return choice(key, s, topic, i, "A can do a job in " + t[0] + " days and B in " + t[1] + " days. They work together for "
                + t[2] + " days, then A leaves. How many more days does B take to finish the job?", null, t[3] + " days",
                List.of((t[3] + t[2]) + " days", (t[1] / 4) + " days", (t[3] + 3) + " days", (t[3] - 2) + " days"),
                "Together they finish " + t[2] + " × (1/" + t[0] + " + 1/" + t[1] + ") = 1/2 of the job; B does the other half in "
                        + t[1] + "/2 = " + t[3] + " days.");
    }

    static Seed trains(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Speed, time and distance";
        int kmh = pick(r, List.of(36, 54, 72, 90, 108));
        int ms = kmh * 5 / 18;
        if (i < 2) {
            int t = pick(r, List.of(8, 10, 12, 15, 20));
            int len = ms * t;
            return choice(key, s, topic, i, "The train in the picture passes the pole in " + t + " seconds. What is its speed?",
                    Svg.train(len, 0), kmh + " km/h", List.of(ms + " km/h", (kmh + 18) + " km/h", (kmh - 18) + " km/h"),
                    "Speed = " + len + "/" + t + " = " + ms + " m/s = " + ms + " × 18/5 = " + kmh + " km/h.");
        }
        int t = pick(r, List.of(12, 15, 18, 20, 24));
        int total = ms * t;
        int len = (total / 2 / 10) * 10;
        int platform = total - len;
        if (i < 4) {
            return choice(key, s, topic, i, "The train in the picture crosses the platform completely in " + t
                    + " seconds. What is its speed?", Svg.train(len, platform), kmh + " km/h",
                    List.of((len * 18 / 5 / t) + " km/h", ms + " km/h", (kmh + 18) + " km/h", (kmh + 9) + " km/h"),
                    "It must cover train + platform = " + total + " m in " + t + " s: " + ms + " m/s = " + kmh + " km/h.");
        }
        return choice(key, s, topic, i, "The train in the picture runs at " + kmh
                + " km/h. How long does it take to cross the platform completely?", Svg.train(len, platform), t + " seconds",
                List.of((platform / ms) + " seconds", (len / ms) + " seconds", (t + 6) + " seconds", (t + 3) + " seconds"),
                kmh + " km/h = " + ms + " m/s; distance = " + len + " + " + platform + " = " + total + " m; time = " + t + " s.");
    }

    static Seed interest(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Simple and compound interest";
        if (i < 2) {
            int p = 1000 * (2 + r.nextInt(9));
            int rate = pick(r, List.of(5, 8, 10, 12));
            int years = 2 + r.nextInt(3);
            int si = p * rate * years / 100;
            return choice(key, s, topic, i, "What is the simple interest on " + rupees(p) + " at " + rate + "% per year for "
                    + years + " years?", null, rupees(si), List.of(rupees(p * rate / 100), rupees(si + p / 10), rupees(si * 2)),
                    "SI = P × R × T / 100 = " + p + " × " + rate + " × " + years + " / 100 = " + si + ".");
        }
        if (i < 4) {
            int p = pick(r, List.of(5000, 8000, 10000, 12000, 15000));
            int rate = pick(r, List.of(10, 20));
            int ci = rate == 10 ? p * 21 / 100 : p * 44 / 100;
            return choice(key, s, topic, i, "What is the compound interest on " + rupees(p) + " at " + rate
                    + "% per year for 2 years, compounded yearly?", null, rupees(ci),
                    List.of(rupees(p * rate * 2 / 100), rupees(ci + p / 100), rupees(ci - p / 50)),
                    "Amount = " + p + " × (1 + " + rate + "/100)² = " + (p + ci) + "; CI = " + ci + ".");
        }
        int[] t = pick(r, List.of(new int[] {10, 5000, 50}, new int[] {5, 8000, 20}, new int[] {20, 2500, 100}, new int[] {10, 12000, 120}));
        return choice(key, s, topic, i, "The difference between compound and simple interest on a sum for 2 years at " + t[0]
                + "% per year is " + rupees(t[2]) + ". What is the sum?", null, rupees(t[1]),
                List.of(rupees(t[1] * 2), rupees(t[2] * 100 / t[0]), rupees(t[1] + 1000)),
                "For 2 years, CI − SI = P × (R/100)² so P = " + t[2] + " × 10000/" + (t[0] * t[0]) + " = " + t[1] + ".");
    }

    static Seed ratio(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Ratio and proportion";
        if (i < 2) {
            int a = 2 + r.nextInt(4);
            int b = a + 1 + r.nextInt(3);
            int total = (a + b) * 100 * (1 + r.nextInt(5));
            int share = total * a / (a + b);
            return choice(key, s, topic, i, rupees(total) + " is divided between Meena and Kiran in the ratio " + a + " : " + b
                    + ". How much does Meena get?", null, rupees(share),
                    List.of(rupees(total * b / (a + b)), rupees(total / 2), rupees(share + 100)),
                    "Meena's share = " + total + " × " + a + "/" + (a + b) + " = " + share + ".");
        }
        if (i < 4) {
            int a = 2;
            int b = 3 + r.nextInt(2);
            int c = b + 2;
            int k = 100 * (1 + r.nextInt(6));
            int diff = (c - a) * k;
            return choice(key, s, topic, i, "A sum is shared among three friends in the ratio " + a + " : " + b + " : " + c
                    + ". The largest share is " + rupees(diff) + " more than the smallest. What is the total sum?", null,
                    rupees((long) (a + b + c) * k), List.of(rupees((long) c * k), rupees((long) diff * 2), rupees((long) (a + b + c) * k + k)),
                    "Difference = (" + c + " − " + a + ") parts = " + diff + ", so one part = " + k + "; total = " + (a + b + c) + " parts = "
                            + (a + b + c) * k + ".");
        }
        int[] t = pick(r, List.of(new int[] {3, 4, 5, 4, 5, 15}, new int[] {5, 7, 4, 3, 4, 20}, new int[] {2, 3, 10, 3, 4, 20},
                new int[] {4, 5, 6, 5, 6, 24}));
        return choice(key, s, topic, i, "The ages of A and B are in the ratio " + t[0] + " : " + t[1] + ". After " + t[2]
                + " years the ratio will be " + t[3] + " : " + t[4] + ". What is A's present age?", null, t[5] + " years",
                List.of((t[5] + t[2]) + " years", (t[5] / t[0] * t[1]) + " years", (t[5] - 3) + " years", (t[5] + 5) + " years"),
                "Let ages be " + t[0] + "k and " + t[1] + "k: (" + t[0] + "k + " + t[2] + ")/(" + t[1] + "k + " + t[2] + ") = " + t[3] + "/" + t[4]
                        + " gives k = " + t[5] / t[0] + ", so A = " + t[5] + ".");
    }

    static Seed averages(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Averages";
        if (i < 2) {
            List<Integer> nums = new ArrayList<>();
            int sum = 0;
            for (int k = 0; k < 4; k++) {
                int n = 10 + r.nextInt(60);
                nums.add(n);
                sum += n;
            }
            int fifth = 10 + r.nextInt(60);
            fifth += (5 - (sum + fifth) % 5) % 5;
            nums.add(fifth);
            sum += fifth;
            int avg = sum / 5;
            return choice(key, s, topic, i, "What is the average of " + nums.toString().replace("[", "").replace("]", "") + "?",
                    null, String.valueOf(avg), List.of(String.valueOf(avg + 2), String.valueOf(avg - 3), String.valueOf(sum / 4)),
                    "Sum = " + sum + "; average = " + sum + "/5 = " + avg + ".");
        }
        if (i < 4) {
            int n = pick(r, List.of(20, 24, 30, 35));
            int avg = 40 + r.nextInt(20);
            int newcomer = avg + n + 1;
            return choice(key, s, topic, i, "The average mark of " + n + " students is " + avg
                    + ". When a new student joins, the average rises by 1. What did the new student score?", null,
                    String.valueOf(newcomer), List.of(String.valueOf(avg + 1), String.valueOf(newcomer - 1), String.valueOf(avg + n)),
                    "New total = " + (n + 1) + " × " + (avg + 1) + " = " + (n + 1) * (avg + 1) + "; old total = " + n * avg + "; difference = "
                            + newcomer + ".");
        }
        while (true) {
            int x = 40 + r.nextInt(20);
            int y = x - 3 + r.nextInt(7);
            int z = x - 3 + r.nextInt(7);
            int sixth = 6 * y + 6 * z - 11 * x;
            if (sixth < 20 || sixth > 99) {
                continue;
            }
            return choice(key, s, topic, i, "The average of 11 results is " + x + ". The average of the first six is " + y
                    + " and of the last six is " + z + ". What is the sixth result?", null, String.valueOf(sixth),
                    List.of(String.valueOf(sixth + 6), String.valueOf((y + z) / 2), String.valueOf(sixth - 5)),
                    "6 × " + y + " + 6 × " + z + " − 11 × " + x + " = " + sixth + " (the sixth result is counted twice).");
        }
    }

    static Seed numberSeries(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Number series";
        List<Long> terms = new ArrayList<>();
        long next;
        String rule;
        if (i < 2) {
            long a = 3 + r.nextInt(10);
            long d = 3 + r.nextInt(9);
            for (int k = 0; k < 5; k++) {
                terms.add(a + d * k);
            }
            next = a + d * 5;
            rule = "Each term adds " + d + ".";
        } else if (i < 4) {
            if (i == 2) {
                long a = 2 + r.nextInt(5);
                for (int k = 0; k < 5; k++) {
                    terms.add(a + (long) k * (k + 1));
                }
                next = a + 30;
                rule = "Differences are 2, 4, 6, 8, so the next difference is 10.";
            } else {
                long a = 2 + r.nextInt(4);
                long m = 2 + r.nextInt(2);
                long t = a;
                for (int k = 0; k < 5; k++) {
                    terms.add(t);
                    t *= m;
                }
                next = t;
                rule = "Each term is multiplied by " + m + ".";
            }
        } else if (i == 4) {
            long t = 1 + r.nextInt(3);
            for (int k = 0; k < 5; k++) {
                terms.add(t);
                t = t * 2 + 1;
            }
            next = t;
            rule = "Each term is doubled, then 1 is added.";
        } else {
            int start = 2 + r.nextInt(3);
            for (int k = 0; k < 5; k++) {
                long n = start + k;
                terms.add(n * n * n + 1);
            }
            long n = start + 5;
            next = n * n * n + 1;
            rule = "The terms are n³ + 1 for n = " + start + ", " + (start + 1) + ", …";
        }
        String shown = terms.toString().replace("[", "").replace("]", "") + ", ?";
        return choice(key, s, topic, i, "What comes next in the series: " + shown, null, String.valueOf(next),
                List.of(String.valueOf(next + 2), String.valueOf(next - 3), String.valueOf(next + terms.get(1) - terms.get(0))),
                rule);
    }

    static Seed probability(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Probability";
        if (i < 2) {
            int red = 2 + r.nextInt(6);
            int blue = 2 + r.nextInt(6);
            return choice(key, s, topic, i, "A bag has " + red + " red and " + blue
                    + " blue balls. One ball is picked at random. What is the probability that it is red?", null,
                    fraction(red, red + blue), List.of(fraction(blue, red + blue), "1/" + red, fraction(red, blue == red ? red + 1 : blue),
                            "1/2"), red + " of the " + (red + blue) + " balls are red.");
        }
        if (i < 4) {
            int sum = pick(r, List.of(5, 6, 8, 9, 10));
            int ways = 6 - Math.abs(7 - sum);
            return choice(key, s, topic, i, "Two fair dice are thrown. What is the probability that the total is " + sum + "?",
                    null, fraction(ways, 36), List.of(fraction(ways + 1, 36), "1/6", fraction(ways, 12), "1/" + sum),
                    ways + " of the 36 outcomes add up to " + sum + ".");
        }
        int red = 3 + r.nextInt(4);
        int blue = 2 + r.nextInt(4);
        int n = red + blue;
        return choice(key, s, topic, i, "A box has " + red + " red and " + blue
                + " blue pens. Two pens are taken out together at random. What is the probability that both are red?", null,
                fraction((long) red * (red - 1), (long) n * (n - 1)),
                List.of(fraction((long) red * red, (long) n * n), fraction(red, n), fraction((long) red * (red - 1), (long) n * n), "1/4"),
                "C(" + red + ",2) / C(" + n + ",2) = " + red * (red - 1) / 2 + "/" + n * (n - 1) / 2 + ".");
    }

    // ---------------- Data interpretation (pictures) ----------------

    static final List<String> YEARS = List.of("2021", "2022", "2023", "2024", "2025");

    static Seed barChart(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Data interpretation";
        while (true) {
            List<Integer> v = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                v.add(10 * (4 + r.nextInt(10)));
            }
            String figure = Svg.barChart("Laptops sold by a store (in units)", YEARS, v, "Units");
            int max = Collections.max(v);
            if (Collections.frequency(v, max) > 1) {
                continue;
            }
            if (i < 2) {
                int at = v.indexOf(max);
                List<String> others = new ArrayList<>(YEARS);
                others.remove(at);
                return choice(key, s, topic, i, "Look at the chart. In which year were the most laptops sold?", figure,
                        YEARS.get(at), others, YEARS.get(at) + " has the tallest bar (" + max + " units).");
            }
            if (i < 4) {
                for (int a = 0; a < 4; a++) {
                    int from = v.get(a);
                    int to = v.get(a + 1);
                    if (to > from && ((to - from) * 100) % from == 0) {
                        int pct = (to - from) * 100 / from;
                        return choice(key, s, topic, i, "Look at the chart. What was the percentage increase in sales from "
                                + YEARS.get(a) + " to " + YEARS.get(a + 1) + "?", figure, pct + "%",
                                List.of((to - from) + "%", (pct + 10) + "%", ((to - from) * 100 / to) + "%", (pct / 2) + "%"),
                                "(" + to + " − " + from + ") / " + from + " × 100 = " + pct + "%.");
                    }
                }
                continue;
            }
            int first = v.get(0) + v.get(1);
            int last = v.get(3) + v.get(4);
            if (last <= first || ((last - first) * 100) % first != 0) {
                continue;
            }
            int pct = (last - first) * 100 / first;
            return choice(key, s, topic, i, "Look at the chart. By what percentage were the total sales of 2024 and 2025 more "
                    + "than the total sales of 2021 and 2022?", figure, pct + "%",
                    List.of((last - first) + "%", (pct + 5) + "%", ((last - first) * 100 / last) + "%", (pct * 2) + "%"),
                    "2021+2022 = " + first + ", 2024+2025 = " + last + "; (" + last + " − " + first + ")/" + first + " × 100 = " + pct + "%.");
        }
    }

    static Seed pieChart(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Data interpretation";
        List<String> labels = List.of("Food", "Rent", "Education", "Transport", "Savings", "Others");
        while (true) {
            List<Integer> p = new ArrayList<>();
            int left = 100;
            for (int k = 0; k < 5; k++) {
                int share = 5 * (1 + r.nextInt(5));
                p.add(share);
                left -= share;
            }
            if (left < 5 || left > 30 || new LinkedHashSet<>(p).size() < 4) {
                continue;
            }
            p.add(left);
            int total = 1000 * pick(r, List.of(40, 50, 60, 80));
            String figure = Svg.pieChart("Monthly budget of a family: " + rupees(total), labels, p);
            if (i < 2) {
                int amount = p.get(0) * total / 100;
                return choice(key, s, topic, i, "Look at the chart. How much does the family spend on Food each month?", figure,
                        rupees(amount), List.of(rupees(p.get(1) * total / 100), rupees(amount + 1000), rupees(p.get(0) * 100L)),
                        p.get(0) + "% of " + total + " = " + amount + ".");
            }
            if (i < 4) {
                int diff = (p.get(1) - p.get(3)) * total / 100;
                if (diff <= 0) {
                    continue;
                }
                return choice(key, s, topic, i, "Look at the chart. How much more is spent on Rent than on Transport?", figure,
                        rupees(diff), List.of(rupees(p.get(1) * total / 100), rupees(diff + 2000), rupees((long) (p.get(1) - p.get(3)) * 100)),
                        "(" + p.get(1) + "% − " + p.get(3) + "%) of " + total + " = " + diff + ".");
            }
            int share = p.get(2);
            int angle = share * 360 / 100;
            return choice(key, s, topic, i, "Look at the chart. What is the angle of the Education sector at the centre of the pie?",
                    figure, angle + "°", List.of((share * 3) + "°", (angle + 18) + "°", (angle - 18) + "°", share + "°"),
                    share + "% of 360° = " + angle + "°.");
        }
    }

    static final List<String> MONTHS = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun");

    static Seed lineChart(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Data interpretation";
        while (true) {
            List<Integer> v = new ArrayList<>();
            for (int k = 0; k < 6; k++) {
                v.add(5 * (4 + r.nextInt(14)));
            }
            int min = Collections.min(v);
            if (Collections.frequency(v, min) > 1) {
                continue;
            }
            String figure = Svg.lineChart("Bikes made by a factory (in thousands)", MONTHS, v, "Thousands");
            if (i < 2) {
                List<String> others = new ArrayList<>(MONTHS);
                others.remove(v.indexOf(min));
                Collections.shuffle(others, r);
                return choice(key, s, topic, i, "Look at the chart. In which month was production the lowest?", figure,
                        MONTHS.get(v.indexOf(min)), others, MONTHS.get(v.indexOf(min)) + " has the lowest point (" + min + ").");
            }
            if (i < 4) {
                int best = -1;
                int bestRise = 0;
                boolean tie = false;
                for (int k = 0; k < 5; k++) {
                    int rise = v.get(k + 1) - v.get(k);
                    if (rise > bestRise) {
                        bestRise = rise;
                        best = k;
                        tie = false;
                    } else if (rise == bestRise && rise > 0) {
                        tie = true;
                    }
                }
                if (best < 0 || tie) {
                    continue;
                }
                List<String> pairs = new ArrayList<>();
                for (int k = 0; k < 5; k++) {
                    pairs.add(MONTHS.get(k) + " to " + MONTHS.get(k + 1));
                }
                String answer = pairs.remove(best);
                Collections.shuffle(pairs, r);
                return choice(key, s, topic, i, "Look at the chart. Between which two months did production rise the most?",
                        figure, answer, pairs, answer + ": a rise of " + bestRise + " thousand.");
            }
            int total = v.stream().mapToInt(Integer::intValue).sum();
            return choice(key, s, topic, i, "Look at the chart. How many bikes (in thousands) were made from January to June in total?",
                    figure, String.valueOf(total), List.of(String.valueOf(total + 10), String.valueOf(total - 15),
                            String.valueOf(total - v.get(5))), "Add all six points: " + total + " thousand.");
        }
    }

    static Seed table(String key, int i) {
        Random r = random(key);
        Section s = Section.QUANT;
        String topic = "Data interpretation";
        List<String> names = List.of("Anu", "Bala", "Chitra", "Dev");
        while (true) {
            int[][] m = new int[4][3];
            int[] totals = new int[4];
            for (int a = 0; a < 4; a++) {
                for (int b = 0; b < 3; b++) {
                    m[a][b] = 50 + r.nextInt(50);
                    totals[a] += m[a][b];
                }
            }
            int top = 0;
            for (int a = 1; a < 4; a++) {
                if (totals[a] > totals[top]) {
                    top = a;
                }
            }
            int topCount = 0;
            for (int t : totals) {
                topCount += t == totals[top] ? 1 : 0;
            }
            if (topCount > 1) {
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            for (int a = 0; a < 4; a++) {
                rows.add(List.of(names.get(a), String.valueOf(m[a][0]), String.valueOf(m[a][1]), String.valueOf(m[a][2])));
            }
            String figure = Svg.table("Marks out of 100", List.of("Student", "Maths", "Science", "English"), rows);
            if (i < 2) {
                List<String> others = new ArrayList<>(names);
                others.remove(top);
                return choice(key, s, topic, i, "Look at the table. Who has the highest total marks?", figure, names.get(top),
                        others, names.get(top) + " has the highest total (" + totals[top] + ").");
            }
            int who = r.nextInt(4);
            if (totals[who] % 3 != 0) {
                continue;
            }
            if (i < 4) {
                int avg = totals[who] / 3;
                return choice(key, s, topic, i, "Look at the table. What is " + names.get(who) + "'s average mark?", figure,
                        String.valueOf(avg), List.of(String.valueOf(avg + 2), String.valueOf(avg - 3), String.valueOf(m[who][0])),
                        "(" + m[who][0] + " + " + m[who][1] + " + " + m[who][2] + ") / 3 = " + avg + ".");
            }
            int above = 0;
            for (int a = 0; a < 4; a++) {
                if (m[a][0] > 70 && m[a][1] > 70 && m[a][2] > 70) {
                    above++;
                }
            }
            List<String> wrong = new ArrayList<>();
            for (int k = 0; k <= 4; k++) {
                if (k != above) {
                    wrong.add(String.valueOf(k));
                }
            }
            return choice(key, s, topic, i, "Look at the table. How many students scored more than 70 in every subject?", figure,
                    String.valueOf(above), wrong, "Check each row: " + above + " student(s) scored above 70 in all three.");
        }
    }

    // ---------------- Logical reasoning ----------------

    static final List<String> WORDS = List.of("CODE", "JAVA", "TEAM", "WORK", "BLUE", "MIND", "GOLD", "PLAN", "SHIP", "FAST", "LAMP", "DESK");

    static String shift(String word, int k) {
        StringBuilder out = new StringBuilder();
        for (char c : word.toCharArray()) {
            out.append((char) ('A' + Math.floorMod(c - 'A' + k, 26)));
        }
        return out.toString();
    }

    static Seed coding(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Coding-decoding";
        List<String> words = new ArrayList<>(WORDS);
        Collections.shuffle(words, r);
        String example = words.get(0);
        String target = words.get(1);
        int k = i < 2 ? 1 + r.nextInt(2) : i < 4 ? pick(r, List.of(3, -2, 4)) : pick(r, List.of(2, 3, -1));
        boolean reverse = i >= 4;
        String exampleCode = shift(reverse ? new StringBuilder(example).reverse().toString() : example, k);
        String answer = shift(reverse ? new StringBuilder(target).reverse().toString() : target, k);
        return choice(key, s, topic, i, "In a certain code, " + example + " is written as " + exampleCode + ". How is " + target
                + " written in that code?", null, answer,
                List.of(shift(target, k), shift(reverse ? new StringBuilder(target).reverse().toString() : target, k + 1),
                        shift(target, -k), shift(target, k + 2)),
                (reverse ? "The word is reversed and " : "") + "each letter moves " + Math.abs(k) + " place(s) "
                        + (k > 0 ? "forward" : "back") + " in the alphabet.");
    }

    static Seed directions(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Direction sense";
        int[] t = pick(r, List.of(new int[] {3, 4, 5}, new int[] {6, 8, 10}, new int[] {5, 12, 13}, new int[] {8, 6, 10}, new int[] {9, 12, 15}));
        if (i < 2) {
            return choice(key, s, topic, i, "Rahul walks " + t[0] + " km north and then " + t[1]
                    + " km east. How far is he from his starting point?", null, t[2] + " km",
                    List.of((t[0] + t[1]) + " km", Math.abs(t[1] - t[0]) + " km", (t[2] + 1) + " km"),
                    "√(" + t[0] + "² + " + t[1] + "²) = " + t[2] + " km.");
        }
        int extra = 2 + r.nextInt(4);
        if (i < 4) {
            return choice(key, s, topic, i, "Priya walks " + (t[0] + extra) + " km north, turns right and walks " + t[1]
                    + " km, then turns right again and walks " + extra + " km. How far is she from where she started?", null,
                    t[2] + " km", List.of((t[0] + extra + t[1] + extra) + " km", (t[1] + extra) + " km", (t[2] + 2) + " km"),
                    "She ends " + t[0] + " km north and " + t[1] + " km east of the start: √(" + t[0] + "² + " + t[1] + "²) = " + t[2] + " km.");
        }
        return choice(key, s, topic, i, "From his house, Arun walks " + t[1] + " km west, turns left and walks " + (t[0] + extra)
                + " km, then turns left again and walks " + t[1] + " km, and finally turns left and walks " + extra
                + " km. In which direction is he now from his house, and how far?", null, t[0] + " km south",
                List.of(t[0] + " km north", (t[0] + extra) + " km south", t[1] + " km west", extra + " km east"),
                "West and east cancel out; he went " + (t[0] + extra) + " km south and " + extra + " km back north: " + t[0] + " km south.");
    }

    static String angleText(double a) {
        return (a == Math.rint(a) ? String.valueOf((int) a) : String.format(java.util.Locale.ROOT, "%.1f", a)) + "°";
    }

    static Seed clock(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Clocks";
        int h;
        int m;
        if (i < 2) {
            h = pick(r, List.of(2, 3, 4, 5, 8, 10));
            m = 0;
        } else if (i < 4) {
            h = pick(r, List.of(2, 3, 4, 9));
            m = pick(r, List.of(20, 30, 40));
        } else {
            h = pick(r, List.of(4, 7, 8, 10));
            m = pick(r, List.of(15, 25, 35, 50));
        }
        double raw = Math.abs(30 * h - 5.5 * m);
        double angle = Math.min(raw, 360 - raw);
        double naive = Math.min(Math.abs(30 * h - 6.0 * m), 360 - Math.abs(30 * h - 6.0 * m));
        return choice(key, s, topic, i, "What is the smaller angle between the hour hand and the minute hand of the clock shown?",
                Svg.clock(h, m), angleText(angle),
                List.of(angleText(naive), angleText(angle + 15), angleText(360 - angle), angleText(angle + 30),
                        angleText(Math.max(5, angle - 10))),
                "Angle = |30 × " + h + " − 5.5 × " + m + "| = " + angleText(raw) + (raw > 180 ? "; the smaller angle is 360° − that = " + angleText(angle) : "")
                        + ". (The hour hand also moves 0.5° each minute.)");
    }

    static Seed venn(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Venn diagrams";
        if (i < 4) {
            int onlyA = 10 + r.nextInt(30);
            int both = 5 + r.nextInt(20);
            int onlyB = 10 + r.nextInt(30);
            int neither = 3 + r.nextInt(12);
            String figure = Svg.venn2("Tea", "Coffee", onlyA, both, onlyB, neither);
            if (i < 2) {
                return choice(key, s, topic, i, "The diagram shows how many people in an office like tea and coffee. "
                        + "How many like tea but not coffee?", figure, String.valueOf(onlyA),
                        List.of(String.valueOf(onlyA + both), String.valueOf(both), String.valueOf(onlyB)),
                        "Only the part of the Tea circle outside Coffee: " + onlyA + ".");
            }
            int atLeastOne = onlyA + both + onlyB;
            return choice(key, s, topic, i, "The diagram shows how many people in an office like tea and coffee. "
                    + "How many people were asked in total?", figure, String.valueOf(atLeastOne + neither),
                    List.of(String.valueOf(atLeastOne), String.valueOf(atLeastOne + neither + both), String.valueOf(onlyA + onlyB + neither)),
                    onlyA + " + " + both + " + " + onlyB + " + " + neither + " (outside both circles) = " + (atLeastOne + neither) + ".");
        }
        int[] reg = new int[7];
        for (int k = 0; k < 7; k++) {
            reg[k] = 2 + r.nextInt(15);
        }
        String figure = Svg.venn3("Cricket", "Football", "Chess", reg);
        if (i == 4) {
            int exactlyTwo = reg[3] + reg[4] + reg[5];
            return choice(key, s, topic, i, "The diagram shows how many students play cricket, football and chess. "
                    + "How many students play exactly two of these games?", figure, String.valueOf(exactlyTwo),
                    List.of(String.valueOf(exactlyTwo + reg[6]), String.valueOf(reg[6]), String.valueOf(reg[0] + reg[1] + reg[2])),
                    "Add the three regions where exactly two circles overlap: " + reg[3] + " + " + reg[4] + " + " + reg[5] + " = " + exactlyTwo + ".");
        }
        int football = reg[1] + reg[3] + reg[4] + reg[6];
        return choice(key, s, topic, i, "The diagram shows how many students play cricket, football and chess. "
                + "How many students play football?", figure, String.valueOf(football),
                List.of(String.valueOf(reg[1]), String.valueOf(football - reg[6]), String.valueOf(football + reg[5]),
                        String.valueOf(football + 3)),
                "Everything inside the Football circle: " + reg[1] + " + " + reg[3] + " + " + reg[4] + " + " + reg[6] + " = " + football + ".");
    }

    // ---------------- Non-verbal reasoning (pictures in the options) ----------------

    static Seed figureSeries(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Non-verbal: figure series";
        int start = 45 * r.nextInt(8);
        List<Integer> angles = new ArrayList<>();
        int answer;
        String rule;
        if (i < 2) {
            int step = 90;
            for (int k = 0; k < 4; k++) {
                angles.add(start + step * k);
            }
            answer = start + step * 4;
            rule = "The arrow turns 90° clockwise each time.";
        } else if (i < 4) {
            int step = i == 2 ? 45 : -45;
            for (int k = 0; k < 4; k++) {
                angles.add(start + step * k);
            }
            answer = start + step * 4;
            rule = "The arrow turns 45° " + (step > 0 ? "clockwise" : "anticlockwise") + " each time.";
        } else {
            int[] steps = {45, 90, 45, 90};
            int a = start;
            angles.add(a);
            for (int k = 0; k < 3; k++) {
                a += steps[k];
                angles.add(a);
            }
            answer = a + steps[3];
            rule = "The arrow turns 45°, then 90°, then 45°, so next it turns 90° clockwise.";
        }
        List<String> cells = new ArrayList<>();
        angles.forEach(a -> cells.add(Svg.arrowCell(a)));
        cells.add(null);
        int last = angles.get(angles.size() - 1);
        List<String> distractors = new ArrayList<>();
        for (int wrong : new int[] {answer + 180, last, answer + 90, answer - 90, answer + 45}) {
            if (Math.floorMod(wrong - answer, 360) != 0) {
                String fig = Svg.cell(Svg.arrowCell(wrong));
                if (!distractors.contains(fig)) {
                    distractors.add(fig);
                }
            }
        }
        return pictureChoice(key, s, topic, i, "Which figure comes next in the series?", Svg.series(cells),
                Svg.cell(Svg.arrowCell(answer)), distractors, rule);
    }

    static final List<String> MIRROR_WORDS = List.of("JAVA", "CODE", "BRICK", "FROG", "PLANET", "SKY42", "R7KP", "GL0BE", "DRUM");

    static Seed mirror(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Non-verbal: mirror and water images";
        String word = MIRROR_WORDS.get(Math.floorMod(key.hashCode(), MIRROR_WORDS.size()));
        boolean water = i == 2 || i == 3 || i == 5;
        String prompt = water
                ? "Which figure is the water image of the word shown (as if the water is below it)?"
                : "Which figure is the mirror image of the word shown (as if the mirror is on its right)?";
        String answer = Svg.word(word, water ? "WATER" : "MIRROR");
        List<String> distractors = new ArrayList<>(List.of(Svg.word(word, water ? "MIRROR" : "WATER"), Svg.word(word, "ROTATE"),
                Svg.word(word, "PLAIN")));
        Collections.shuffle(distractors, r);
        return pictureChoice(key, s, topic, i, prompt, Svg.word(word, "PLAIN"), answer, distractors,
                water ? "A water image flips the word upside down; the left–right order stays the same."
                        : "A mirror on the right reverses left and right; top and bottom stay the same.");
    }

    static Seed matrix(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Non-verbal: pattern matrix";
        int[][] counts = new int[3][3];
        String rule;
        if (i < 2) {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    counts[a][b] = a + b + 1;
                }
            }
            rule = "Each step right or down adds one dot.";
        } else if (i < 4) {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    counts[a][b] = 1 + a * 3 + b;
                }
            }
            rule = "Counting left to right, top to bottom, the dots go 1, 2, 3 … 9.";
        } else {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    counts[a][b] = (a + 1) * (b + 1);
                }
            }
            rule = "Each cell has (row number × column number) dots: 3 × 3 = 9.";
        }
        int answer = counts[2][2];
        List<String> distractors = new ArrayList<>();
        for (int wrong : new int[] {answer - 1, answer - 2, answer + 1, answer - 3}) {
            if (wrong >= 1 && wrong <= 9) {
                distractors.add(Svg.cell(Svg.dots(wrong)));
            }
        }
        Collections.shuffle(distractors, r);
        return pictureChoice(key, s, topic, i, "Which figure completes the pattern (replaces the ?)?", Svg.dotMatrix(counts),
                Svg.cell(Svg.dots(answer)), distractors, rule);
    }

    static Seed oddOneOut(String key, int i) {
        Random r = random(key);
        Section s = Section.LOGICAL;
        String topic = "Non-verbal: odd one out";
        String fill = Svg.COLORS[r.nextInt(3)];
        int odd;
        String rule;
        List<Integer> sides = new ArrayList<>();
        if (i < 2) {
            odd = 4;
            sides.addAll(List.of(3, 3, 3));
            rule = "Three are triangles; the odd one has four sides.";
        } else if (i < 4) {
            odd = 5;
            sides.addAll(List.of(6, 6, 6));
            rule = "Three are hexagons (6 sides); the odd one has 5 sides.";
        } else {
            odd = 7;
            sides.addAll(List.of(4, 6, 8));
            rule = "Three shapes have an even number of sides (4, 6, 8); the odd one has 7.";
        }
        List<String> distractors = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            distractors.add(Svg.cell(Svg.polygon(sides.get(k), 15.0 * k + r.nextInt(10), fill)));
        }
        return pictureChoice(key, s, topic, i, "Which figure is the odd one out?", null,
                Svg.cell(Svg.polygon(odd, r.nextInt(20), fill)), distractors, rule);
    }
}
