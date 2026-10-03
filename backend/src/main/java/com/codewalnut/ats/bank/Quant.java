package com.codewalnut.ats.bank;

import static com.codewalnut.ats.bank.AptitudeBank.between;
import static com.codewalnut.ats.bank.AptitudeBank.choice;
import static com.codewalnut.ats.bank.AptitudeBank.fraction;
import static com.codewalnut.ats.bank.AptitudeBank.num;
import static com.codewalnut.ats.bank.AptitudeBank.pick;
import static com.codewalnut.ats.bank.AptitudeBank.rupees;

import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Numerical-ability generators for the built-in bank (ADR-0014). Answers are computed. */
final class Quant {

    private static final Section S = Section.QUANT;
    private static final List<String> NAMES = List.of("Asha", "Ravi", "Meena", "Kiran", "Arjun", "Divya", "Sameer", "Priya", "Rahul", "Neha",
            "Vikram", "Anu", "Farhan", "Lakshmi", "Joseph", "Gita");
    private static final List<String> ITEMS = List.of("bag", "phone", "watch", "chair", "lamp", "bicycle", "printer", "jacket", "kettle", "speaker");

    private Quant() {}

    static String two(Random r) {
        String a = pick(r, NAMES);
        String b;
        do {
            b = pick(r, NAMES);
        } while (b.equals(a));
        return a + "|" + b;
    }

    // ---------------- Percentages ----------------

    static Seed percentages(String key, Difficulty d, Random r) {
        String t = "Percentages";
        if (d == Difficulty.EASY) {
            if (r.nextBoolean()) {
                int p = pick(r, List.of(5, 10, 15, 20, 25, 30, 40, 50, 60, 75, 80, 120));
                int n = 20 * between(r, 2, 50);
                double ans = p * n / 100.0;
                return choice(key, S, t, d, "What is " + p + "% of " + n + "?", null, num(ans),
                        List.of(num(ans + n / 10.0), num(ans * 2), num(Math.max(1, ans - n / 20.0)), num(ans + 5)),
                        p + "% of " + n + " = " + n + " × " + p + " / 100 = " + num(ans) + ".");
            }
            int whole = 10 * between(r, 4, 60);
            int p = pick(r, List.of(10, 20, 25, 30, 40, 50, 60, 75));
            int part = whole * p / 100;
            return choice(key, S, t, d, part + " is what percent of " + whole + "?", null, p + "%",
                    List.of((p + 5) + "%", (p * 2) + "%", (100 - p) + "%", (p + 10) + "%"),
                    part + " / " + whole + " × 100 = " + p + "%.");
        }
        if (d == Difficulty.MEDIUM) {
            int kind = r.nextInt(3);
            if (kind == 0) {
                int a = 5 * between(r, 2, 10);
                int b = 5 * between(r, 2, 10);
                double net = a - b - a * b / 100.0;
                String ans = net == 0 ? "No change" : num(Math.abs(net)) + "% " + (net > 0 ? "increase" : "decrease");
                return choice(key, S, t, d, "A price is increased by " + a + "% and then decreased by " + b + "%. What is the net change?", null, ans,
                        List.of(num(Math.abs(a - b)) + "% " + (a >= b ? "increase" : "decrease"), "No change", num(Math.abs(net) + 1) + "% decrease",
                                num(Math.abs(net)) + "% " + (net > 0 ? "decrease" : "increase")),
                        "Net % = a − b − ab/100 = " + a + " − " + b + " − " + num(a * b / 100.0) + " = " + num(net) + "%.");
            }
            if (kind == 1) {
                int voters = 100 * between(r, 20, 200);
                int p = pick(r, List.of(40, 45, 55, 60, 65, 70));
                int winner = voters * p / 100;
                int margin = winner - (voters - winner);
                return choice(key, S, t, d, "In an election between two candidates, the winner got " + p + "% of " + voters
                        + " valid votes. By how many votes did the winner win?", null, String.valueOf(Math.abs(margin)),
                        List.of(String.valueOf(winner), String.valueOf(voters - winner), String.valueOf(Math.abs(margin) / 2)),
                        "Winner " + winner + ", loser " + (voters - winner) + "; margin = " + Math.abs(margin) + ".");
            }
            int[] pr = pick(r, List.of(new int[] {25, 20}, new int[] {20, 16}, new int[] {100, 50}, new int[] {60, 37}, new int[] {50, 33}));
            double cut = pr[0] * 100.0 / (100 + pr[0]);
            return choice(key, S, t, d, "The price of sugar rises by " + pr[0] + "%. By what percent must a family cut its use to keep spending the same?",
                    null, num(cut) + "%", List.of(pr[0] + "%", num(cut + 5) + "%", num(cut - 4) + "%", num(pr[0] / 2.0) + "%"),
                    "Cut = " + pr[0] + " / (100 + " + pr[0] + ") × 100 = " + num(cut) + "%.");
        }
        if (r.nextBoolean()) {
            int p = pick(r, List.of(20, 25, 40, 50, 60, 75, 80));
            int original = 100 * between(r, 3, 40);
            int now = original * (100 + p) / 100;
            return choice(key, S, t, d, "After a " + p + "% increase, the price of a " + pick(r, ITEMS) + " is " + rupees(now)
                    + ". What was the price before the increase?", null, rupees(original),
                    List.of(rupees(Math.round(now * (100 - p) / 100.0)), rupees(original + 100), rupees(now - original / 2), rupees(original - 100)),
                    "Original × " + (100 + p) + "/100 = " + now + ", so original = " + now + " × 100 / " + (100 + p) + " = " + original + ".");
        }
        int p = pick(r, List.of(20, 25, 50, 60, 100));
        double less = p * 100.0 / (100 + p);
        String[] ab = two(r).split("\\|");
        return choice(key, S, t, d, ab[0] + "'s salary is " + p + "% more than " + ab[1] + "'s. By what percent is " + ab[1]
                + "'s salary less than " + ab[0] + "'s?", null, num(less) + "%",
                List.of(p + "%", num(less + 5) + "%", num(100 - less) + "%", num(less - 3) + "%"),
                "Less by " + p + " / (100 + " + p + ") × 100 = " + num(less) + "%.");
    }

    // ---------------- Profit and loss ----------------

    static Seed profitLoss(String key, Difficulty d, Random r) {
        String t = "Profit and loss";
        String item = pick(r, ITEMS);
        if (d == Difficulty.EASY) {
            int cp = 100 * between(r, 2, 40);
            int p = pick(r, List.of(5, 10, 15, 20, 25, 30, 40, 50));
            boolean loss = r.nextInt(3) == 0;
            int sp = cp * (100 + (loss ? -p : p)) / 100;
            return choice(key, S, t, d, "A shopkeeper buys a " + item + " for " + rupees(cp) + " and sells it for " + rupees(sp)
                    + ". What is the " + (loss ? "loss" : "profit") + " percentage?", null, p + "%",
                    List.of((p + 5) + "%", (p * 2) + "%", Math.max(1, p - 5) + "%", num(Math.abs(sp - cp) * 100.0 / sp) + "%"),
                    (loss ? "Loss" : "Profit") + " = " + Math.abs(sp - cp) + " on cost " + cp + " = " + p + "%.");
        }
        if (d == Difficulty.MEDIUM) {
            while (true) {
                int cp = 100 * between(r, 3, 30);
                int p = pick(r, List.of(10, 20, 25, 50));
                int disc = pick(r, List.of(10, 20, 25, 40));
                int sp = cp * (100 + p) / 100;
                if ((sp * 100) % (100 - disc) != 0) {
                    continue;
                }
                int mp = sp * 100 / (100 - disc);
                return choice(key, S, t, d, "A " + item + " costing " + rupees(cp) + " is marked at " + rupees(mp) + " and sold at a " + disc
                        + "% discount. What is the profit percentage?", null, p + "%",
                        List.of((p + disc) + "%", disc + "%", (p + 5) + "%", Math.max(1, p - 5) + "%"),
                        "Selling price = " + mp + " × " + (100 - disc) + "/100 = " + sp + "; profit " + (sp - cp) + " on " + cp + " = " + p + "%.");
            }
        }
        if (r.nextBoolean()) {
            while (true) {
                int cp = 100 * between(r, 2, 40);
                int l = pick(r, List.of(10, 20, 25));
                int g = pick(r, List.of(10, 15, 20, 25, 30));
                if ((cp * (100 - l)) % 100 != 0 || (cp * (100 + g)) % 100 != 0) {
                    continue;
                }
                int sell = cp * (100 - l) / 100;
                int target = cp * (100 + g) / 100;
                return choice(key, S, t, d, "By selling a " + item + " for " + rupees(sell) + ", a seller loses " + l + "%. At what price should it be sold to gain "
                        + g + "%?", null, rupees(target), List.of(rupees(sell * (100 + g) / 100), rupees(target + 50), rupees(cp), rupees(target - 50)),
                        "Cost = " + sell + " × 100/" + (100 - l) + " = " + cp + "; for " + g + "% gain sell at " + cp + " × " + (100 + g) + "/100 = " + target + ".");
            }
        }
        int x = pick(r, List.of(10, 20, 25, 30, 40));
        int price = 100 * between(r, 10, 99);
        return choice(key, S, t, d, "Two " + item + "s are sold for " + rupees(price) + " each, one at a " + x + "% profit and the other at a " + x
                + "% loss. What is the overall result?", null, num(x * x / 100.0) + "% loss",
                List.of("No profit, no loss", num(x * x / 100.0) + "% profit", (x / 2) + "% loss", num(x * x / 50.0) + "% loss"),
                "Same selling price with equal % profit and loss always gives a loss of x²/100 = " + num(x * x / 100.0) + "%.");
    }

    // ---------------- Time and work ----------------

    static Seed timeWork(String key, Difficulty d, Random r) {
        String t = "Time and work";
        String[] ab = two(r).split("\\|");
        if (d == Difficulty.EASY || d == Difficulty.MEDIUM) {
            while (true) {
                int a = between(r, 4, 60);
                int b = between(r, a + 1, 90);
                if ((a * b) % (a + b) != 0) {
                    continue;
                }
                int together = a * b / (a + b);
                if (d == Difficulty.EASY) {
                    return choice(key, S, t, d, ab[0] + " can finish a task in " + a + " days and " + ab[1] + " in " + b
                            + " days. In how many days can they finish it working together?", null, together + " days",
                            List.of(((a + b) / 2) + " days", (together + 2) + " days", (b - a) + " days", (together - 1) + " days"),
                            "Per day: 1/" + a + " + 1/" + b + " = 1/" + together + ", so " + together + " days.");
                }
                return choice(key, S, t, d, ab[0] + " and " + ab[1] + " together can build a wall in " + together + " days. " + ab[0]
                        + " alone takes " + a + " days. How long will " + ab[1] + " alone take?", null, b + " days",
                        List.of((a - together) + " days", (a + together) + " days", (b + 4) + " days", (b - 3) + " days"),
                        ab[1] + " per day = 1/" + together + " − 1/" + a + " = 1/" + b + ".");
            }
        }
        if (r.nextBoolean()) {
            while (true) {
                int a = between(r, 6, 40);
                int b = between(r, a + 2, 60);
                int lcm = lcm(a, b);
                int k = between(r, 1, 8);
                int done = k * (lcm / a + lcm / b);
                if (done >= lcm || ((lcm - done) % (lcm / b)) != 0) {
                    continue;
                }
                int rest = (lcm - done) / (lcm / b);
                return choice(key, S, t, d, "A can do a job in " + a + " days and B in " + b + " days. They work together for " + k
                        + " days, then A leaves. How many more days does B take to finish?", null, rest + " days",
                        List.of((rest + k) + " days", (rest + 2) + " days", Math.max(1, rest - 2) + " days", (b - k) + " days"),
                        "Together they do " + k + " × (1/" + a + " + 1/" + b + ") of the work; B does the rest at 1/" + b + " a day: " + rest + " days.");
            }
        }
        while (true) {
            int fill = between(r, 3, 20);
            int empty = between(r, fill + 1, 40);
            if ((fill * empty) % (empty - fill) != 0) {
                continue;
            }
            int net = fill * empty / (empty - fill);
            return choice(key, S, t, d, "Pipe A fills a tank in " + fill + " hours; pipe B empties it in " + empty
                    + " hours. If both are opened together, how long does the empty tank take to fill?", null, net + " hours",
                    List.of((fill * empty / (fill + empty)) + " hours", (net + 3) + " hours", (empty - fill) + " hours", (net - 2) + " hours"),
                    "Net per hour = 1/" + fill + " − 1/" + empty + " = 1/" + net + ".");
        }
    }

    static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    // ---------------- Speed, time and distance ----------------

    static Seed speed(String key, Difficulty d, Random r) {
        String t = "Speed, time and distance";
        int kmh = 18 * between(r, 2, 8);
        int ms = kmh * 5 / 18;
        if (d == Difficulty.EASY) {
            if (r.nextInt(3) == 0) {
                int k = 18 * between(r, 1, 10);
                return choice(key, S, t, d, "What is " + k + " km/h in metres per second?", null, (k * 5 / 18) + " m/s",
                        List.of((k * 18 / 5) + " m/s", (k * 5 / 18 + 5) + " m/s", (k / 2) + " m/s", (k * 5 / 18 - 2) + " m/s"),
                        k + " × 5/18 = " + k * 5 / 18 + " m/s.");
            }
            int time = between(r, 5, 25);
            int len = ms * time;
            return choice(key, S, t, d, "The train in the picture passes the pole in " + time + " seconds. What is its speed?",
                    Svg.train(len, 0), kmh + " km/h", List.of(ms + " km/h", (kmh + 18) + " km/h", (kmh - 18 > 0 ? kmh - 18 : kmh + 36) + " km/h"),
                    "Speed = " + len + "/" + time + " = " + ms + " m/s = " + ms + " × 18/5 = " + kmh + " km/h.");
        }
        int time = between(r, 10, 40);
        int total = ms * time;
        int len = 10 * between(r, Math.max(2, total / 40), Math.max(3, total / 15));
        int platform = total - len;
        if (platform <= 0) {
            platform = 10 * between(r, 5, 20);
            total = len + platform;
            if (total % ms != 0) {
                total += ms - total % ms;
                platform = total - len;
            }
            time = total / ms;
        }
        if (d == Difficulty.MEDIUM) {
            return choice(key, S, t, d, "The train in the picture crosses the platform completely in " + time + " seconds. What is its speed?",
                    Svg.train(len, platform), kmh + " km/h",
                    List.of(num(len * 3.6 / time) + " km/h", ms + " km/h", (kmh + 18) + " km/h", (kmh + 9) + " km/h"),
                    "It covers train + platform = " + total + " m in " + time + " s: " + ms + " m/s = " + kmh + " km/h.");
        }
        int kind = r.nextInt(3);
        if (kind == 0) {
            return choice(key, S, t, d, "The train in the picture runs at " + kmh + " km/h. How long does it take to cross the platform completely?",
                    Svg.train(len, platform), time + " seconds",
                    List.of(num(platform / (double) ms) + " seconds", num(len / (double) ms) + " seconds", (time + 6) + " seconds", (time + 3) + " seconds"),
                    kmh + " km/h = " + ms + " m/s; distance " + len + " + " + platform + " = " + total + " m; time " + time + " s.");
        }
        if (kind == 1) {
            int v1 = 18 * between(r, 2, 6);
            int v2 = 18 * between(r, 1, 5);
            int rel = (v1 + v2) * 5 / 18;
            int secs = between(r, 6, 20);
            int l1 = 10 * between(r, 5, rel * secs / 20);
            int l2 = rel * secs - l1;
            if (l2 <= 0) {
                l2 = 50;
                secs = (l1 + l2) / rel;
                l2 = rel * secs - l1;
                if (l2 <= 0) {
                    return speed(key, Difficulty.MEDIUM, r);
                }
            }
            return choice(key, S, t, d, "Two trains, " + l1 + " m and " + l2 + " m long, run towards each other on parallel tracks at " + v1 + " km/h and "
                    + v2 + " km/h. How long do they take to cross each other?", null, secs + " seconds",
                    List.of(num((l1 + l2) / (Math.abs(v1 - v2) * 5 / 18.0 == 0 ? 1 : Math.abs(v1 - v2) * 5 / 18.0)) + " seconds", (secs + 4) + " seconds",
                            num((l1 + l2) / (double) (v1 + v2)) + " seconds", (secs + 2) + " seconds"),
                    "Relative speed = " + (v1 + v2) + " km/h = " + rel + " m/s; distance " + (l1 + l2) + " m; time " + secs + " s.");
        }
        int a = 10 * between(r, 3, 8);
        int b = 10 * between(r, 2, 9);
        if (a == b) {
            b += 10;
        }
        double avg = 2.0 * a * b / (a + b);
        return choice(key, S, t, d, "A car goes from city P to Q at " + a + " km/h and returns at " + b + " km/h. What is its average speed for the whole journey?",
                null, num(avg) + " km/h", List.of(num((a + b) / 2.0) + " km/h", num(avg + 2) + " km/h", num(avg - 3) + " km/h", Math.max(a, b) + " km/h"),
                "For equal distances, average speed = 2ab / (a + b) = " + num(avg) + " km/h (not the simple average).");
    }

    // ---------------- Simple and compound interest ----------------

    static Seed interest(String key, Difficulty d, Random r) {
        String t = "Simple and compound interest";
        if (d == Difficulty.EASY) {
            int p = 500 * between(r, 2, 40);
            int rate = pick(r, List.of(4, 5, 6, 8, 10, 12, 15));
            int years = between(r, 2, 6);
            int si = p * rate * years / 100;
            return choice(key, S, t, d, "What is the simple interest on " + rupees(p) + " at " + rate + "% per year for " + years + " years?", null,
                    rupees(si), List.of(rupees(p * rate / 100), rupees(si + p / 10), rupees(si * 2), rupees(si + 100)),
                    "SI = P × R × T / 100 = " + p + " × " + rate + " × " + years + " / 100 = " + si + ".");
        }
        if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int p = 1000 * between(r, 2, 30);
                int rate = pick(r, List.of(10, 20));
                int ci = rate == 10 ? p * 21 / 100 : p * 44 / 100;
                return choice(key, S, t, d, "What is the compound interest on " + rupees(p) + " at " + rate + "% per year for 2 years, compounded yearly?",
                        null, rupees(ci), List.of(rupees(p * rate * 2 / 100), rupees(ci + p / 100), rupees(ci - p / 50), rupees(ci + 100)),
                        "Amount = " + p + " × (1 + " + rate + "/100)² = " + (p + ci) + "; CI = " + ci + ".");
            }
            int p = 1000 * between(r, 2, 20);
            int rate = pick(r, List.of(4, 5, 8, 10, 12));
            int years = between(r, 2, 6);
            int si = p * rate * years / 100;
            return choice(key, S, t, d, "A sum of " + rupees(p) + " earns " + rupees(si) + " simple interest in " + years + " years. What is the rate per year?",
                    null, rate + "%", List.of((rate + 2) + "%", (rate * 2) + "%", Math.max(1, rate - 1) + "%", (rate + 1) + "%"),
                    "R = SI × 100 / (P × T) = " + si + " × 100 / (" + p + " × " + years + ") = " + rate + "%.");
        }
        if (r.nextBoolean()) {
            int rate = pick(r, List.of(5, 10, 20));
            int p = rate == 5 ? 4000 * between(r, 1, 10) : rate == 10 ? 1000 * between(r, 2, 30) : 500 * between(r, 2, 30);
            int diff = p * rate * rate / 10000;
            return choice(key, S, t, d, "The difference between compound and simple interest on a sum for 2 years at " + rate + "% per year is " + rupees(diff)
                    + ". What is the sum?", null, rupees(p), List.of(rupees(p * 2L), rupees(diff * 100L / rate), rupees(p + 1000L), rupees(p - 500L)),
                    "For 2 years, CI − SI = P × (R/100)², so P = " + diff + " × 10000 / " + rate * rate + " = " + p + ".");
        }
        int years = pick(r, List.of(5, 8, 10, 20, 25));
        int rate = 100 / years;
        return choice(key, S, t, d, "At what rate of simple interest per year will a sum double itself in " + years + " years?", null, rate + "%",
                List.of((rate * 2) + "%", (rate + 2) + "%", num(100.0 / (years * 2)) + "%", (years) + "%"),
                "Doubling means interest = principal, so R × " + years + " = 100: R = " + rate + "%.");
    }

    // ---------------- Ratio and proportion ----------------

    static Seed ratio(String key, Difficulty d, Random r) {
        String t = "Ratio and proportion";
        String[] ab = two(r).split("\\|");
        if (d == Difficulty.EASY) {
            int a = between(r, 1, 7);
            int b = between(r, 1, 9);
            if (a == b) {
                b++;
            }
            int total = (a + b) * 50 * between(r, 1, 20);
            int share = total * a / (a + b);
            return choice(key, S, t, d, rupees(total) + " is divided between " + ab[0] + " and " + ab[1] + " in the ratio " + a + " : " + b
                    + ". How much does " + ab[0] + " get?", null, rupees(share),
                    List.of(rupees(total * b / (a + b)), rupees(total / 2), rupees(share + 100), rupees(Math.abs(share - 100))),
                    ab[0] + "'s share = " + total + " × " + a + "/" + (a + b) + " = " + share + ".");
        }
        if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int a = between(r, 1, 4);
                int b = a + between(r, 1, 3);
                int c = b + between(r, 1, 3);
                int k = 50 * between(r, 1, 20);
                int diff = (c - a) * k;
                return choice(key, S, t, d, "A sum is shared among three friends in the ratio " + a + " : " + b + " : " + c + ". The largest share is "
                        + rupees(diff) + " more than the smallest. What is the total?", null, rupees((long) (a + b + c) * k),
                        List.of(rupees((long) c * k), rupees(diff * 2L), rupees((long) (a + b + c) * k + k), rupees((long) (a + b) * k)),
                        "(" + c + " − " + a + ") parts = " + diff + ", one part = " + k + "; total " + (a + b + c) + " parts = " + (a + b + c) * k + ".");
            }
            int a = between(r, 2, 5);
            int b = between(r, 2, 6);
            int c = between(r, 2, 6);
            int dd = between(r, 2, 7);
            int g1 = gcd(a * c, b * dd);
            return choice(key, S, t, d, "If A : B = " + a + " : " + b + " and B : C = " + c + " : " + dd + ", what is A : C?", null,
                    (a * c / g1) + " : " + (b * dd / g1), List.of(a + " : " + dd, (a * dd) + " : " + (b * c), (a + c) + " : " + (b + dd), c + " : " + a),
                    "A : C = (A/B) × (B/C) = (" + a + "/" + b + ") × (" + c + "/" + dd + ") = " + (a * c / g1) + " : " + (b * dd / g1) + ".");
        }
        while (true) {
            int a = between(r, 2, 7);
            int b = between(r, a + 1, 9);
            int n = between(r, 2, 12);
            int k = between(r, 2, 10);
            int futureA = a * k + n;
            int futureB = b * k + n;
            int g = gcd(futureA, futureB);
            int c = futureA / g;
            int e = futureB / g;
            if (c > 12 || e > 12 || (c == a && e == b) || gcd(a, b) != 1) {
                continue;
            }
            return choice(key, S, t, d, "The ages of " + ab[0] + " and " + ab[1] + " are in the ratio " + a + " : " + b + ". After " + n
                    + " years the ratio will be " + c + " : " + e + ". What is " + ab[0] + "'s present age?", null, (a * k) + " years",
                    List.of((a * k + n) + " years", (b * k) + " years", (a * k + 3) + " years", Math.max(1, a * k - 2) + " years"),
                    "Let ages be " + a + "k and " + b + "k: (" + a + "k + " + n + ")/(" + b + "k + " + n + ") = " + c + "/" + e + " gives k = " + k
                            + ", so " + ab[0] + " is " + a * k + ".");
        }
    }

    // ---------------- Averages ----------------

    static Seed averages(String key, Difficulty d, Random r) {
        String t = "Averages";
        if (d == Difficulty.EASY) {
            int count = between(r, 4, 6);
            List<Integer> nums = new ArrayList<>();
            int sum = 0;
            for (int k = 0; k < count - 1; k++) {
                int n = between(r, 10, 95);
                nums.add(n);
                sum += n;
            }
            int last = between(r, 10, 95);
            last += (count - (sum + last) % count) % count;
            nums.add(last);
            sum += last;
            int avg = sum / count;
            return choice(key, S, t, d, "What is the average of " + String.join(", ", nums.stream().map(String::valueOf).toList()) + "?", null,
                    String.valueOf(avg), List.of(String.valueOf(avg + 2), String.valueOf(avg - 3), num(sum / (count - 1.0)), String.valueOf(avg + 5)),
                    "Sum = " + sum + "; average = " + sum + " / " + count + " = " + avg + ".");
        }
        if (d == Difficulty.MEDIUM) {
            int n = between(r, 10, 40);
            int avg = between(r, 30, 70);
            int rise = between(r, 1, 3);
            int newcomer = avg + rise * (n + 1);
            if (r.nextBoolean()) {
                return choice(key, S, t, d, "The average mark of " + n + " students is " + avg + ". When a new student joins, the average rises by " + rise
                        + ". What did the new student score?", null, String.valueOf(newcomer),
                        List.of(String.valueOf(avg + rise), String.valueOf(newcomer - rise), String.valueOf(avg + n), String.valueOf(newcomer + 1)),
                        "New total = " + (n + 1) + " × " + (avg + rise) + " = " + (n + 1) * (avg + rise) + "; old total = " + n * avg + "; difference = " + newcomer + ".");
            }
            int old = between(r, 40, 70);
            int replaced = old + rise * n;
            return choice(key, S, t, d, "The average weight of " + n + " people rises by " + rise + " kg when one person weighing " + old
                    + " kg is replaced by a new person. What is the new person's weight?", null, replaced + " kg",
                    List.of((old + rise) + " kg", (replaced - rise) + " kg", (old + n) + " kg", (replaced + n) + " kg"),
                    "The total rises by " + n + " × " + rise + " = " + rise * n + " kg, so the new person weighs " + old + " + " + rise * n + " = " + replaced + " kg.");
        }
        while (true) {
            int x = between(r, 35, 70);
            int y = x - 4 + r.nextInt(9);
            int z = x - 4 + r.nextInt(9);
            int sixth = 6 * y + 6 * z - 11 * x;
            if (sixth < 15 || sixth > 120) {
                continue;
            }
            return choice(key, S, t, d, "The average of 11 results is " + x + ". The average of the first six is " + y + " and of the last six is " + z
                    + ". What is the sixth result?", null, String.valueOf(sixth),
                    List.of(String.valueOf(sixth + 6), String.valueOf((y + z) / 2), String.valueOf(Math.max(1, sixth - 5)), String.valueOf(x)),
                    "6 × " + y + " + 6 × " + z + " − 11 × " + x + " = " + sixth + " (the sixth result is counted twice).");
        }
    }

    // ---------------- Number series ----------------

    static Seed numberSeries(String key, Difficulty d, Random r) {
        String t = "Number series";
        List<Long> terms = new ArrayList<>();
        long next;
        String rule;
        int kind = d == Difficulty.EASY ? r.nextInt(3) : d == Difficulty.MEDIUM ? 3 + r.nextInt(3) : 6 + r.nextInt(4);
        switch (kind) {
            case 0 -> {
                long a = between(r, 2, 40);
                long step = between(r, 2, 15);
                for (int k = 0; k < 5; k++) {
                    terms.add(a + step * k);
                }
                next = a + step * 5;
                rule = "Each term adds " + step + ".";
            }
            case 1 -> {
                long a = between(r, 60, 150);
                long step = between(r, 3, 12);
                for (int k = 0; k < 5; k++) {
                    terms.add(a - step * k);
                }
                next = a - step * 5;
                rule = "Each term subtracts " + step + ".";
            }
            case 2 -> {
                long a = between(r, 1, 6);
                long m = between(r, 2, 3);
                long v = a;
                for (int k = 0; k < 5; k++) {
                    terms.add(v);
                    v *= m;
                }
                next = v;
                rule = "Each term is multiplied by " + m + ".";
            }
            case 3 -> {
                long a = between(r, 1, 10);
                long inc = between(r, 1, 4);
                long start = between(r, 1, 5);
                long v = a;
                long diff = start;
                for (int k = 0; k < 5; k++) {
                    terms.add(v);
                    v += diff;
                    diff += inc;
                }
                next = v;
                rule = "The differences grow by " + inc + " each time.";
            }
            case 4 -> {
                int n0 = between(r, 1, 9);
                int add = between(r, 0, 3);
                for (int k = 0; k < 5; k++) {
                    terms.add((long) (n0 + k) * (n0 + k) + add);
                }
                next = (long) (n0 + 5) * (n0 + 5) + add;
                rule = "The terms are n² + " + add + " for n = " + n0 + ", " + (n0 + 1) + ", …";
            }
            case 5 -> {
                long a = between(r, 1, 5);
                long m = between(r, 2, 3);
                long add = between(r, 1, 3);
                long v = a;
                for (int k = 0; k < 5; k++) {
                    terms.add(v);
                    v = v * m + add;
                }
                next = v;
                rule = "Each term is multiplied by " + m + ", then " + add + " is added.";
            }
            case 6 -> {
                int n0 = between(r, 1, 6);
                int add = between(r, -1, 2);
                for (int k = 0; k < 5; k++) {
                    long n = n0 + k;
                    terms.add(n * n * n + add);
                }
                long n = n0 + 5;
                next = n * n * n + add;
                rule = "The terms are n³ " + (add >= 0 ? "+ " + add : "− " + (-add)) + " for n = " + n0 + ", " + (n0 + 1) + ", …";
            }
            case 7 -> {
                long a = between(r, 2, 20);
                long b = between(r, 30, 60);
                long sa = between(r, 2, 6);
                long sb = between(r, 2, 6);
                for (int k = 0; k < 6; k++) {
                    terms.add(k % 2 == 0 ? a + sa * (k / 2) : b - sb * (k / 2));
                }
                next = a + sa * 3;
                rule = "Two series alternate: " + a + ", " + (a + sa) + ", … (+" + sa + ") and " + b + ", " + (b - sb) + ", … (−" + sb + ").";
            }
            case 8 -> {
                long a = between(r, 1, 5);
                long b = between(r, 1, 6);
                terms.add(a);
                terms.add(b);
                for (int k = 2; k < 6; k++) {
                    terms.add(terms.get(k - 1) + terms.get(k - 2));
                }
                next = terms.get(4) + terms.get(5);
                rule = "Each term is the sum of the two before it.";
            }
            default -> {
                long a = between(r, 2, 8);
                long v = a;
                int m = 1;
                for (int k = 0; k < 5; k++) {
                    terms.add(v);
                    m++;
                    v = v * m;
                }
                next = v;
                rule = "Multiply by 2, then 3, then 4, and so on.";
            }
        }
        String shown = String.join(", ", terms.stream().map(String::valueOf).toList()) + ", ?";
        long lastDiff = terms.get(terms.size() - 1) - terms.get(terms.size() - 2);
        return choice(key, S, t, d, "What comes next in the series: " + shown, null, String.valueOf(next),
                List.of(String.valueOf(next + 2), String.valueOf(next - 3), String.valueOf(terms.get(terms.size() - 1) + lastDiff), String.valueOf(next + 10)),
                rule);
    }

    // ---------------- Probability ----------------

    static Seed probability(String key, Difficulty d, Random r) {
        String t = "Probability";
        if (d == Difficulty.EASY) {
            int kind = r.nextInt(3);
            if (kind == 0) {
                int red = between(r, 1, 12);
                int blue = between(r, 1, 12);
                String colour = pick(r, List.of("red", "blue"));
                int want = colour.equals("red") ? red : blue;
                return choice(key, S, t, d, "A bag has " + red + " red and " + blue + " blue balls. One ball is picked at random. What is the probability that it is "
                        + colour + "?", null, fraction(want, red + blue),
                        List.of(fraction(red + blue - want, red + blue), "1/" + Math.max(2, want + 1), "1/2".equals(fraction(want, red + blue)) ? "1/3" : "1/2",
                                fraction(want, Math.max(1, red + blue - want) + (want == red + blue - want ? 1 : 0))),
                        want + " of the " + (red + blue) + " balls are " + colour + ".");
            }
            if (kind == 1) {
                String[][] events = {{"a king", "4", "52"}, {"a heart", "13", "52"}, {"a red card", "26", "52"}, {"a face card (J, Q, K)", "12", "52"},
                        {"an ace of spades", "1", "52"}, {"a black king", "2", "52"}, {"a red face card", "6", "52"}, {"a 7", "4", "52"}};
                String[] e = events[r.nextInt(events.length)];
                long fav = Long.parseLong(e[1]);
                return choice(key, S, t, d, "One card is drawn at random from a full pack of 52 cards. What is the probability that it is " + e[0] + "?", null,
                        fraction(fav, 52), List.of(fraction(fav + 1, 52), "1/4", "1/13", "1/2", fraction(fav, 26)),
                        fav + " of the 52 cards are " + e[0] + ": " + fraction(fav, 52) + ".");
            }
            int face = between(r, 1, 6);
            String[] ask = {"a number greater than " + face, "an even number", "a multiple of 3", "a number less than " + face};
            int which = r.nextInt(ask.length);
            int fav = switch (which) {
                case 0 -> 6 - face;
                case 1 -> 3;
                case 2 -> 2;
                default -> face - 1;
            };
            if (fav == 0) {
                return probability(key, d, new Random(r.nextLong()));
            }
            return choice(key, S, t, d, "A fair die is thrown. What is the probability of getting " + ask[which] + "?", null, fraction(fav, 6),
                    List.of(fraction(Math.min(5, fav + 1), 6), "1/6", fraction(6 - fav == 0 ? 1 : 6 - fav, 6), "1/2"),
                    fav + " of the 6 faces qualify.");
        }
        if (d == Difficulty.MEDIUM) {
            if (r.nextBoolean()) {
                int sum = between(r, 3, 11);
                int ways = 6 - Math.abs(7 - sum);
                return choice(key, S, t, d, "Two fair dice are thrown. What is the probability that the total is " + sum + "?", null, fraction(ways, 36),
                        List.of(fraction(ways + 1, 36), "1/6", fraction(ways, 12), fraction(Math.max(1, ways - 1), 36), "1/" + sum),
                        ways + " of the 36 outcomes add up to " + sum + ".");
            }
            if (r.nextBoolean()) {
                int k = between(r, 4, 10);
                int ways = 0;
                for (int a = 1; a <= 6; a++) {
                    for (int b = 1; b <= 6; b++) {
                        ways += a + b > k ? 1 : 0;
                    }
                }
                return choice(key, S, t, d, "Two fair dice are thrown. What is the probability that the total is more than " + k + "?", null,
                        fraction(ways, 36), List.of(fraction(36 - ways, 36), fraction(ways + 6 - Math.abs(7 - k), 36), fraction(12 - k, 11), "1/2"),
                        ways + " of the 36 outcomes add up to more than " + k + ".");
            }
            int coins = between(r, 2, 4);
            long total = 1L << coins;
            return choice(key, S, t, d, coins + " fair coins are tossed together. What is the probability of getting at least one head?", null,
                    fraction(total - 1, total), List.of("1/" + total, "1/2", fraction(coins, total), fraction(total - 2, total)),
                    "Only 'all tails' (1 of " + total + " outcomes) has no head: 1 − 1/" + total + " = " + fraction(total - 1, total) + ".");
        }
        int red = between(r, 3, 9);
        int blue = between(r, 2, 8);
        int n = red + blue;
        if (r.nextBoolean()) {
            return choice(key, S, t, d, "A box has " + red + " red and " + blue + " blue pens. Two pens are taken out together at random. What is the probability that both are red?",
                    null, fraction((long) red * (red - 1), (long) n * (n - 1)),
                    List.of(fraction((long) red * red, (long) n * n), fraction(red, n), fraction((long) red * (red - 1), (long) n * n), "1/4"),
                    "C(" + red + ",2) / C(" + n + ",2) = " + red * (red - 1) / 2 + "/" + n * (n - 1) / 2 + ".");
        }
        return choice(key, S, t, d, "A box has " + red + " red and " + blue + " blue pens. Two pens are taken out together at random. What is the probability that one is red and one is blue?",
                null, fraction(2L * red * blue, (long) n * (n - 1)),
                List.of(fraction((long) red * blue, (long) n * n), fraction(red, n), fraction((long) red * blue, (long) n * (n - 1)), "1/2"),
                "(" + red + " × " + blue + ") / C(" + n + ",2) = " + red * blue + "/" + n * (n - 1) / 2 + ".");
    }

    // ---------------- Data interpretation (pictures) ----------------

    static final List<String> YEARS = List.of("2021", "2022", "2023", "2024", "2025");
    static final List<String> MONTHS = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun");
    static final List<String[]> BAR_THEMES = List.of(new String[] {"Laptops sold by a store", "Units"}, new String[] {"Cars made by a factory", "Cars"},
            new String[] {"Students admitted to a college", "Students"}, new String[] {"Trees planted by a city", "Trees"},
            new String[] {"Orders delivered by a shop", "Orders"});

    static Seed dataInterpretation(String key, Difficulty d, Random r) {
        return switch (r.nextInt(4)) {
            case 0 -> bar(key, d, r);
            case 1 -> pie(key, d, r);
            case 2 -> line(key, d, r);
            default -> table(key, d, r);
        };
    }

    private static Seed bar(String key, Difficulty d, Random r) {
        String t = "Data interpretation";
        String[] theme = pick(r, BAR_THEMES);
        while (true) {
            List<Integer> v = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                v.add(10 * between(r, 3, 15));
            }
            int max = Collections.max(v);
            int min = Collections.min(v);
            if (Collections.frequency(v, max) > 1 || Collections.frequency(v, min) > 1) {
                continue;
            }
            String fig = Svg.barChart(theme[0] + " (" + theme[1].toLowerCase() + ")", YEARS, v, theme[1]);
            if (d == Difficulty.EASY) {
                boolean highest = r.nextBoolean();
                int at = v.indexOf(highest ? max : min);
                List<String> others = new ArrayList<>(YEARS);
                others.remove(at);
                Collections.shuffle(others, r);
                if (r.nextBoolean()) {
                    return choice(key, S, t, d, "Look at the chart. In which year was the number the " + (highest ? "highest" : "lowest") + "?", fig, YEARS.get(at),
                            others, YEARS.get(at) + " has the " + (highest ? "tallest" : "shortest") + " bar.");
                }
                int a = r.nextInt(5);
                int b = (a + 1 + r.nextInt(4)) % 5;
                int diff = Math.abs(v.get(a) - v.get(b));
                if (diff == 0) {
                    continue;
                }
                return choice(key, S, t, d, "Look at the chart. What is the difference between " + YEARS.get(a) + " and " + YEARS.get(b) + "?", fig,
                        String.valueOf(diff), List.of(String.valueOf(diff + 10), String.valueOf(v.get(a) + v.get(b)), String.valueOf(Math.max(5, diff - 10))),
                        "|" + v.get(a) + " − " + v.get(b) + "| = " + diff + ".");
            }
            if (d == Difficulty.MEDIUM) {
                if (r.nextBoolean()) {
                    for (int a = 0; a < 4; a++) {
                        int from = v.get(a);
                        int to = v.get(a + 1);
                        if (to != from && (Math.abs(to - from) * 100) % from == 0) {
                            int pct = Math.abs(to - from) * 100 / from;
                            String dir = to > from ? "increase" : "decrease";
                            return choice(key, S, t, d, "Look at the chart. What was the percentage " + dir + " from " + YEARS.get(a) + " to " + YEARS.get(a + 1) + "?",
                                    fig, pct + "%", List.of(Math.abs(to - from) + "%", (pct + 10) + "%", num(Math.abs(to - from) * 100.0 / to) + "%", (pct / 2 + 1) + "%"),
                                    "|" + to + " − " + from + "| / " + from + " × 100 = " + pct + "%.");
                        }
                    }
                    continue;
                }
                int sum = v.stream().mapToInt(Integer::intValue).sum();
                return choice(key, S, t, d, "Look at the chart. What is the average per year over the five years?", fig, num(sum / 5.0),
                        List.of(num(sum / 5.0 + 4), num(sum / 4.0), num((max + min) / 2.0), num(sum / 5.0 - 6)),
                        "Total " + sum + " / 5 = " + num(sum / 5.0) + ".");
            }
            int first = v.get(0) + v.get(1);
            int last = v.get(3) + v.get(4);
            if (r.nextBoolean()) {
                if (last == first || (Math.abs(last - first) * 100) % first != 0) {
                    continue;
                }
                int pct = Math.abs(last - first) * 100 / first;
                return choice(key, S, t, d, "Look at the chart. By what percentage were the 2024 + 2025 totals " + (last > first ? "more" : "less")
                        + " than the 2021 + 2022 totals?", fig, pct + "%", List.of(Math.abs(last - first) + "%", (pct + 5) + "%", num(Math.abs(last - first) * 100.0 / last) + "%", (pct * 2) + "%"),
                        "2021+2022 = " + first + ", 2024+2025 = " + last + "; |" + last + " − " + first + "| / " + first + " × 100 = " + pct + "%.");
            }
            int total = v.stream().mapToInt(Integer::intValue).sum();
            int y = r.nextInt(5);
            if ((v.get(y) * 100) % total != 0 && (v.get(y) * 1000) % total != 0) {
                continue;
            }
            return choice(key, S, t, d, "Look at the chart. What percentage of the five-year total came from " + YEARS.get(y) + "?", fig,
                    num(v.get(y) * 100.0 / total) + "%", List.of(num(v.get(y) * 100.0 / total + 5) + "%", "20%".equals(num(v.get(y) * 100.0 / total) + "%") ? "25%" : "20%",
                            num(v.get(y) * 100.0 / total * 2) + "%", num(Math.max(1, v.get(y) * 100.0 / total - 3)) + "%"),
                    v.get(y) + " / " + total + " × 100 = " + num(v.get(y) * 100.0 / total) + "%.");
        }
    }

    private static Seed pie(String key, Difficulty d, Random r) {
        String t = "Data interpretation";
        List<String> labels = List.of("Food", "Rent", "Education", "Transport", "Savings", "Others");
        while (true) {
            List<Integer> p = new ArrayList<>();
            int left = 100;
            for (int k = 0; k < 5; k++) {
                int share = 5 * between(r, 1, 6);
                p.add(share);
                left -= share;
            }
            if (left < 5 || left > 35 || new LinkedHashSet<>(p).size() < 4) {
                continue;
            }
            p.add(left);
            int total = 1000 * pick(r, List.of(20, 30, 40, 50, 60, 80, 100, 120));
            String fig = Svg.pieChart("Monthly budget of a family: " + rupees(total), labels, p);
            int a = r.nextInt(6);
            int b = (a + 1 + r.nextInt(5)) % 6;
            if (d == Difficulty.EASY) {
                int amount = p.get(a) * total / 100;
                return choice(key, S, t, d, "Look at the chart. How much does the family spend on " + labels.get(a) + " each month?", fig, rupees(amount),
                        List.of(rupees(p.get(b) * total / 100), rupees(amount + 1000), rupees(p.get(a) * 100L), rupees(Math.max(500, amount - 1000))),
                        p.get(a) + "% of " + total + " = " + amount + ".");
            }
            if (d == Difficulty.MEDIUM) {
                int diff = Math.abs(p.get(a) - p.get(b)) * total / 100;
                if (diff == 0) {
                    continue;
                }
                String more = p.get(a) > p.get(b) ? labels.get(a) : labels.get(b);
                String less = p.get(a) > p.get(b) ? labels.get(b) : labels.get(a);
                return choice(key, S, t, d, "Look at the chart. How much more is spent on " + more + " than on " + less + "?", fig, rupees(diff),
                        List.of(rupees(Math.max(p.get(a), p.get(b)) * total / 100), rupees(diff + 2000), rupees(Math.abs(p.get(a) - p.get(b)) * 100L), rupees(diff * 2L)),
                        "(" + Math.max(p.get(a), p.get(b)) + "% − " + Math.min(p.get(a), p.get(b)) + "%) of " + total + " = " + diff + ".");
            }
            if (r.nextBoolean()) {
                int angle = p.get(a) * 360 / 100;
                return choice(key, S, t, d, "Look at the chart. What is the angle of the " + labels.get(a) + " sector at the centre of the pie?", fig, angle + "°",
                        List.of((p.get(a) * 3) + "°", (angle + 18) + "°", Math.max(9, angle - 18) + "°", p.get(a) + "°"),
                        p.get(a) + "% of 360° = " + angle + "°.");
            }
            int together = (p.get(a) + p.get(b)) * total / 100;
            return choice(key, S, t, d, "Look at the chart. If the family's budget rises to " + rupees(total * 2L) + " with the same shares, how much will go on "
                    + labels.get(a) + " and " + labels.get(b) + " together?", fig, rupees(together * 2L),
                    List.of(rupees(together), rupees(together * 2L + 2000), rupees((long) (p.get(a) + p.get(b)) * 200), rupees(together * 3L)),
                    "(" + p.get(a) + "% + " + p.get(b) + "%) of " + total * 2 + " = " + together * 2 + ".");
        }
    }

    private static Seed line(String key, Difficulty d, Random r) {
        String t = "Data interpretation";
        while (true) {
            List<Integer> v = new ArrayList<>();
            for (int k = 0; k < 6; k++) {
                v.add(5 * between(r, 3, 18));
            }
            int min = Collections.min(v);
            int max = Collections.max(v);
            if (Collections.frequency(v, min) > 1 || Collections.frequency(v, max) > 1) {
                continue;
            }
            String fig = Svg.lineChart("Bikes made by a factory (in thousands)", MONTHS, v, "Thousands");
            if (d == Difficulty.EASY) {
                boolean lowest = r.nextBoolean();
                int at = v.indexOf(lowest ? min : max);
                List<String> others = new ArrayList<>(MONTHS);
                others.remove(at);
                Collections.shuffle(others, r);
                return choice(key, S, t, d, "Look at the chart. In which month was production the " + (lowest ? "lowest" : "highest") + "?", fig,
                        MONTHS.get(at), others, MONTHS.get(at) + " has the " + (lowest ? "lowest" : "highest") + " point.");
            }
            if (d == Difficulty.MEDIUM) {
                boolean rise = r.nextBoolean();
                int best = -1;
                int bestChange = 0;
                boolean tie = false;
                for (int k = 0; k < 5; k++) {
                    int change = (v.get(k + 1) - v.get(k)) * (rise ? 1 : -1);
                    if (change > bestChange) {
                        bestChange = change;
                        best = k;
                        tie = false;
                    } else if (change == bestChange && change > 0) {
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
                return choice(key, S, t, d, "Look at the chart. Between which two months did production " + (rise ? "rise" : "fall") + " the most?", fig,
                        answer, pairs, answer + ": a " + (rise ? "rise" : "fall") + " of " + bestChange + " thousand.");
            }
            int total = v.stream().mapToInt(Integer::intValue).sum();
            if (r.nextBoolean()) {
                return choice(key, S, t, d, "Look at the chart. How many bikes (in thousands) were made from January to June in total?", fig,
                        String.valueOf(total), List.of(String.valueOf(total + 10), String.valueOf(total - 15), String.valueOf(total - v.get(5)), String.valueOf(total + 5)),
                        "Add all six points: " + total + " thousand.");
            }
            int h1 = v.get(0) + v.get(1) + v.get(2);
            int h2 = v.get(3) + v.get(4) + v.get(5);
            if (h1 == h2) {
                continue;
            }
            return choice(key, S, t, d, "Look at the chart. How many more bikes (in thousands) were made in " + (h2 > h1 ? "April–June than in January–March" : "January–March than in April–June") + "?",
                    fig, String.valueOf(Math.abs(h2 - h1)), List.of(String.valueOf(Math.abs(h2 - h1) + 5), String.valueOf(Math.max(h1, h2)),
                            String.valueOf(Math.abs(v.get(5) - v.get(0))), String.valueOf(Math.abs(h2 - h1) + 10)),
                    "Jan–Mar = " + h1 + ", Apr–Jun = " + h2 + "; difference " + Math.abs(h2 - h1) + ".");
        }
    }

    private static Seed table(String key, Difficulty d, Random r) {
        String t = "Data interpretation";
        List<String> names = new ArrayList<>(NAMES);
        Collections.shuffle(names, r);
        names = names.subList(0, 4);
        List<String> subjects = List.of("Maths", "Science", "English");
        while (true) {
            int[][] m = new int[4][3];
            int[] totals = new int[4];
            for (int a = 0; a < 4; a++) {
                for (int b = 0; b < 3; b++) {
                    m[a][b] = between(r, 45, 99);
                    totals[a] += m[a][b];
                }
            }
            int top = 0;
            for (int a = 1; a < 4; a++) {
                if (totals[a] > totals[top]) {
                    top = a;
                }
            }
            int ties = 0;
            for (int x : totals) {
                ties += x == totals[top] ? 1 : 0;
            }
            if (ties > 1) {
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            for (int a = 0; a < 4; a++) {
                rows.add(List.of(names.get(a), String.valueOf(m[a][0]), String.valueOf(m[a][1]), String.valueOf(m[a][2])));
            }
            String fig = Svg.table("Marks out of 100", List.of("Student", "Maths", "Science", "English"), rows);
            if (d == Difficulty.EASY) {
                if (r.nextBoolean()) {
                    List<String> others = new ArrayList<>(names);
                    others.remove(top);
                    return choice(key, S, t, d, "Look at the table. Who has the highest total marks?", fig, names.get(top), others,
                            names.get(top) + " has the highest total (" + totals[top] + ").");
                }
                int sub = r.nextInt(3);
                int best = 0;
                for (int a = 1; a < 4; a++) {
                    if (m[a][sub] > m[best][sub]) {
                        best = a;
                    }
                }
                int count = 0;
                for (int a = 0; a < 4; a++) {
                    count += m[a][sub] == m[best][sub] ? 1 : 0;
                }
                if (count > 1) {
                    continue;
                }
                List<String> others = new ArrayList<>(names);
                others.remove(best);
                return choice(key, S, t, d, "Look at the table. Who scored the most in " + subjects.get(sub) + "?", fig, names.get(best), others,
                        names.get(best) + " scored " + m[best][sub] + " in " + subjects.get(sub) + ".");
            }
            int who = r.nextInt(4);
            if (d == Difficulty.MEDIUM) {
                if (totals[who] % 3 != 0) {
                    continue;
                }
                int avg = totals[who] / 3;
                return choice(key, S, t, d, "Look at the table. What is " + names.get(who) + "'s average mark?", fig, String.valueOf(avg),
                        List.of(String.valueOf(avg + 2), String.valueOf(avg - 3), String.valueOf(m[who][0]), String.valueOf(avg + 5)),
                        "(" + m[who][0] + " + " + m[who][1] + " + " + m[who][2] + ") / 3 = " + avg + ".");
            }
            if (r.nextBoolean()) {
                int threshold = pick(r, List.of(60, 65, 70, 75));
                int above = 0;
                for (int a = 0; a < 4; a++) {
                    if (m[a][0] > threshold && m[a][1] > threshold && m[a][2] > threshold) {
                        above++;
                    }
                }
                List<String> wrong = new ArrayList<>();
                for (int k = 0; k <= 4; k++) {
                    if (k != above) {
                        wrong.add(String.valueOf(k));
                    }
                }
                return choice(key, S, t, d, "Look at the table. How many students scored more than " + threshold + " in every subject?", fig, String.valueOf(above),
                        wrong, "Check each row: " + above + " student(s) scored above " + threshold + " in all three subjects.");
            }
            int sub = r.nextInt(3);
            int sum = 0;
            for (int a = 0; a < 4; a++) {
                sum += m[a][sub];
            }
            return choice(key, S, t, d, "Look at the table. What is the class average in " + subjects.get(sub) + "?", fig, num(sum / 4.0),
                    List.of(num(sum / 4.0 + 2), num(sum / 3.0), num(sum / 4.0 - 3), num(sum / 4.0 + 5)),
                    "(" + m[0][sub] + " + " + m[1][sub] + " + " + m[2][sub] + " + " + m[3][sub] + ") / 4 = " + num(sum / 4.0) + ".");
        }
    }
}
