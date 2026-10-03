package com.codewalnut.ats.bank;

import static com.codewalnut.ats.bank.AptitudeBank.between;
import static com.codewalnut.ats.bank.AptitudeBank.choice;
import static com.codewalnut.ats.bank.AptitudeBank.pick;

import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Reasoning puzzles whose answers come from small solvers, not from templates: blood relations are
 * read off a generated family tree, syllogisms are checked against every Venn model, and seating
 * and height puzzles are only used when the clues allow exactly one arrangement (ADR-0014).
 */
final class Reasoning {

    private static final Section S = Section.LOGICAL;

    private Reasoning() {}

    // ---------------- Blood relations ----------------

    /** One person in a generated family. Parents are indexes into the family list, or -1. */
    private record Person(boolean male, int mother, int father, int spouse) {}

    private enum Step {
        PARENT_OF, CHILD_OF, SIBLING_OF, SPOUSE_OF
    }

    static Seed bloodRelations(String key, Difficulty d, Random r) {
        String t = "Blood relations";
        int steps = d == Difficulty.EASY ? 2 : d == Difficulty.MEDIUM ? 3 : 4;
        while (true) {
            List<Person> fam = family(r);
            int start = r.nextInt(fam.size());
            List<Integer> path = new ArrayList<>(List.of(start));
            List<Step> moves = new ArrayList<>();
            boolean stuck = false;
            while (moves.size() < steps && !stuck) {
                int cur = path.get(path.size() - 1);
                List<int[]> next = new ArrayList<>();
                for (int other = 0; other < fam.size(); other++) {
                    if (path.contains(other)) {
                        continue;
                    }
                    Step s = edge(fam, cur, other);
                    if (s != null && allowed(moves.isEmpty() ? null : moves.get(moves.size() - 1), s)) {
                        next.add(new int[] {other, s.ordinal()});
                    }
                }
                if (next.isEmpty()) {
                    stuck = true;
                } else {
                    int[] chosen = pick(r, next);
                    path.add(chosen[0]);
                    moves.add(Step.values()[chosen[1]]);
                }
            }
            if (stuck) {
                continue;
            }
            int end = path.get(path.size() - 1);
            String rel = relation(fam, start, end);
            if (rel == null) {
                continue;
            }
            List<Character> letters = new ArrayList<>();
            for (char c = 'A'; c <= 'Z'; c++) {
                if (c != 'I' && c != 'O') {
                    letters.add(c);
                }
            }
            Collections.shuffle(letters, r);
            List<String> statements = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                int a = path.get(i);
                statements.add(letters.get(i) + " is the " + word(moves.get(i), fam.get(a).male()) + " of " + letters.get(i + 1));
            }
            char first = letters.get(0);
            char last = letters.get(moves.size());
            boolean male = fam.get(start).male();
            List<String> distractors = new ArrayList<>(List.of(flip(rel)));
            List<String> pool = new ArrayList<>(male ? MALE : FEMALE);
            pool.remove(rel);
            Collections.shuffle(pool, r);
            distractors.addAll(pool);
            return choice(key, S, t, d, String.join("; ", statements) + ". How is " + first + " related to " + last + "?", null, rel, distractors,
                    "Follow the chain: " + chain(fam, path, letters) + ", so " + first + " is " + last + "'s " + rel.toLowerCase() + ".");
        }
    }

    private static final List<String> MALE = List.of("Father", "Brother", "Uncle", "Grandfather", "Son", "Nephew", "Cousin", "Brother-in-law",
            "Father-in-law", "Son-in-law", "Husband", "Grandson");
    private static final List<String> FEMALE = List.of("Mother", "Sister", "Aunt", "Grandmother", "Daughter", "Niece", "Cousin", "Sister-in-law",
            "Mother-in-law", "Daughter-in-law", "Wife", "Granddaughter");

    private static String flip(String rel) {
        int i = MALE.indexOf(rel);
        if (i >= 0) {
            return FEMALE.get(i).equals(rel) ? "Uncle" : FEMALE.get(i);
        }
        i = FEMALE.indexOf(rel);
        return MALE.get(i).equals(rel) ? "Aunt" : MALE.get(i);
    }

    /**
     * Grandparents with three children; each child is married to someone from outside and has two
     * children of their own: three generations, 14 people.
     */
    private static List<Person> family(Random r) {
        List<Person> fam = new ArrayList<>();
        fam.add(new Person(true, -1, -1, 1));
        fam.add(new Person(false, -1, -1, 0));
        for (int c = 0; c < 3; c++) {
            boolean male = r.nextBoolean();
            int child = fam.size();
            fam.add(new Person(male, 1, 0, child + 1));
            fam.add(new Person(!male, -1, -1, child));
            int mother = male ? child + 1 : child;
            int father = male ? child : child + 1;
            for (int k = 0; k < 2; k++) {
                fam.add(new Person(r.nextBoolean(), mother, father, -1));
            }
        }
        return fam;
    }

    /** How a relates to b in one step, or null if they are not directly related. */
    private static Step edge(List<Person> fam, int a, int b) {
        Person pa = fam.get(a);
        Person pb = fam.get(b);
        if (pb.mother() == a || pb.father() == a) {
            return Step.PARENT_OF;
        }
        if (pa.mother() == b || pa.father() == b) {
            return Step.CHILD_OF;
        }
        if (pa.spouse() == b) {
            return Step.SPOUSE_OF;
        }
        if (pa.mother() >= 0 && pa.mother() == pb.mother()) {
            return Step.SIBLING_OF;
        }
        return null;
    }

    /** Steps that could loop back (child of my child's parent may be me) or double back are left out. */
    private static boolean allowed(Step prev, Step next) {
        if (prev == null) {
            return true;
        }
        return !(prev == Step.PARENT_OF && next == Step.CHILD_OF) && !(prev == Step.CHILD_OF && next == Step.PARENT_OF)
                && !(prev == Step.SIBLING_OF && next == Step.SIBLING_OF) && !(prev == Step.SPOUSE_OF && next == Step.SPOUSE_OF);
    }

    private static String word(Step s, boolean male) {
        return switch (s) {
            case PARENT_OF -> male ? "father" : "mother";
            case CHILD_OF -> male ? "son" : "daughter";
            case SIBLING_OF -> male ? "brother" : "sister";
            case SPOUSE_OF -> male ? "husband" : "wife";
        };
    }

    private static String chain(List<Person> fam, List<Integer> path, List<Character> letters) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            String rel = relation(fam, path.get(0), path.get(i));
            if (rel != null) {
                parts.add(letters.get(0) + " is " + letters.get(i) + "'s " + rel.toLowerCase());
            }
        }
        return String.join(", ", parts);
    }

    private static boolean isParent(List<Person> fam, int a, int b) {
        return fam.get(b).mother() == a || fam.get(b).father() == a;
    }

    private static boolean siblings(List<Person> fam, int a, int b) {
        return a != b && fam.get(a).mother() >= 0 && fam.get(a).mother() == fam.get(b).mother();
    }

    /** a's relation to b, read off the tree; null when it has no everyday name here. */
    private static String relation(List<Person> fam, int a, int b) {
        boolean m = fam.get(a).male();
        int spouseA = fam.get(a).spouse();
        int spouseB = fam.get(b).spouse();
        if (spouseA == b) {
            return m ? "Husband" : "Wife";
        }
        if (isParent(fam, a, b)) {
            return m ? "Father" : "Mother";
        }
        if (isParent(fam, b, a)) {
            return m ? "Son" : "Daughter";
        }
        if (siblings(fam, a, b)) {
            return m ? "Brother" : "Sister";
        }
        for (int x = 0; x < fam.size(); x++) {
            if (isParent(fam, a, x) && isParent(fam, x, b)) {
                return m ? "Grandfather" : "Grandmother";
            }
            if (isParent(fam, b, x) && isParent(fam, x, a)) {
                return m ? "Grandson" : "Granddaughter";
            }
        }
        for (int x = 0; x < fam.size(); x++) {
            if (isParent(fam, x, b) && (siblings(fam, a, x) || spouseA >= 0 && siblings(fam, spouseA, x))) {
                return m ? "Uncle" : "Aunt";
            }
            if (isParent(fam, x, a) && (siblings(fam, b, x) || spouseB >= 0 && siblings(fam, spouseB, x))) {
                return m ? "Nephew" : "Niece";
            }
        }
        for (int x = 0; x < fam.size(); x++) {
            for (int y = 0; y < fam.size(); y++) {
                if (isParent(fam, x, a) && isParent(fam, y, b) && siblings(fam, x, y)) {
                    return "Cousin";
                }
            }
        }
        if (spouseA >= 0 && isParent(fam, b, spouseA)) {
            return m ? "Son-in-law" : "Daughter-in-law";
        }
        if (spouseB >= 0 && isParent(fam, a, spouseB)) {
            return m ? "Father-in-law" : "Mother-in-law";
        }
        if (spouseA >= 0 && siblings(fam, spouseA, b) || spouseB >= 0 && siblings(fam, a, spouseB)) {
            return m ? "Brother-in-law" : "Sister-in-law";
        }
        return null;
    }

    // ---------------- Syllogisms ----------------

    private static final List<String> TERMS = List.of("cars", "bikes", "pens", "books", "doctors", "teachers", "apples", "flowers", "rivers",
            "chairs", "birds", "kites", "phones", "laptops", "coins", "stars", "trees", "boxes", "rings", "cups", "singers", "dancers", "clouds", "rocks");
    private static final String[] ANSWERS = {"Only conclusion I follows", "Only conclusion II follows", "Both I and II follow", "Neither I nor II follows"};

    /** A categorical statement: kind 0 All X are Y, 1 No X are Y, 2 Some X are Y, 3 Some X are not Y. */
    private record Claim(int kind, int x, int y) {
        String text(List<String> names) {
            String a = names.get(x);
            String b = names.get(y);
            return switch (kind) {
                case 0 -> "All " + a + " are " + b + ".";
                case 1 -> "No " + a + " are " + b + ".";
                case 2 -> "Some " + a + " are " + b + ".";
                default -> "Some " + a + " are not " + b + ".";
            };
        }

        /** The regions (bitmasks of sets) this claim speaks about: X∖Y for All / Some-not, X∩Y for No / Some. */
        long mask(int sets) {
            long m = 0;
            for (int reg = 1; reg < 1 << sets; reg++) {
                boolean inX = (reg >> x & 1) == 1;
                boolean inY = (reg >> y & 1) == 1;
                if (inX && (kind == 0 || kind == 3 ? !inY : inY)) {
                    m |= 1L << reg;
                }
            }
            return m;
        }

        /** Whether the claim holds when exactly the regions set in model are non-empty. */
        boolean holds(long model, int sets) {
            boolean touches = (model & mask(sets)) != 0;
            return kind < 2 ? !touches : touches;
        }

        boolean contradicts(Claim o) {
            return x == o.x && y == o.y && (kind + o.kind == 3) || (kind == 1 && o.kind == 2 || kind == 2 && o.kind == 1) && x == o.y && y == o.x;
        }
    }

    static Seed syllogisms(String key, Difficulty d, Random r) {
        String t = "Syllogisms";
        int sets = d == Difficulty.HARD ? 4 : 3;
        int want = r.nextInt(4);
        List<String> names = new ArrayList<>(TERMS);
        Collections.shuffle(names, r);
        names = names.subList(0, sets);
        while (true) {
            List<Claim> premises = new ArrayList<>();
            for (int i = 0; i < sets - 1; i++) {
                int kind = d == Difficulty.EASY ? r.nextInt(2) : r.nextInt(4);
                premises.add(r.nextBoolean() ? new Claim(kind, i, i + 1) : new Claim(kind, i + 1, i));
            }
            Claim c1 = randomClaim(r, sets);
            Claim c2 = randomClaim(r, sets);
            if (c1.equals(c2) || c1.contradicts(c2) || c2.contradicts(c1) || repeats(premises, c1) || repeats(premises, c2)) {
                continue;
            }
            boolean f1 = follows(premises, c1, sets);
            boolean f2 = follows(premises, c2, sets);
            int got = f1 && f2 ? 2 : f1 ? 0 : f2 ? 1 : 3;
            if (got != want || !consistent(premises, sets)) {
                continue;
            }
            StringBuilder prompt = new StringBuilder("Statements: ");
            for (Claim p : premises) {
                prompt.append(p.text(names)).append(' ');
            }
            prompt.append("\nConclusions: I. ").append(c1.text(names)).append(" II. ").append(c2.text(names))
                    .append("\nTaking the statements as true, which conclusions follow?");
            String explanation = "Conclusion I " + (f1 ? "must be true in every case the statements allow" : "can be false while the statements hold")
                    + "; conclusion II " + (f2 ? "must be true in every case" : "can be false while the statements hold") + ".";
            return choice(key, S, t, d, prompt.toString(), null, ANSWERS[got], List.of(ANSWERS), explanation);
        }
    }

    /** A conclusion that restates or flatly denies a statement teaches nothing. */
    private static boolean repeats(List<Claim> premises, Claim c) {
        for (Claim p : premises) {
            boolean sameTerms = p.x() == c.x() && p.y() == c.y() || p.x() == c.y() && p.y() == c.x();
            if (sameTerms && (p.equals(c) || p.contradicts(c) || c.contradicts(p) || p.kind() + c.kind() == 1)) {
                return true;
            }
        }
        return false;
    }

    private static Claim randomClaim(Random r, int sets) {
        int x = r.nextInt(sets);
        int y;
        do {
            y = r.nextInt(sets);
        } while (y == x);
        return new Claim(r.nextInt(4), x, y);
    }

    private static final java.util.Map<Integer, long[]> MODELS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Every way the regions can be empty or not with each set non-empty (the usual exam convention). */
    private static long[] models(int sets) {
        return MODELS.computeIfAbsent(sets, n -> {
            int regions = 1 << n;
            List<Long> out = new ArrayList<>();
            for (long model = 0; model < 1L << regions; model += 2) {
                boolean ok = true;
                for (int s = 0; s < n && ok; s++) {
                    long inS = 0;
                    for (int reg = 1; reg < regions; reg++) {
                        if ((reg >> s & 1) == 1) {
                            inS |= 1L << reg;
                        }
                    }
                    ok = (model & inS) != 0;
                }
                if (ok) {
                    out.add(model);
                }
            }
            return out.stream().mapToLong(Long::longValue).toArray();
        });
    }

    private static boolean consistent(List<Claim> premises, int sets) {
        for (long model : models(sets)) {
            if (premises.stream().allMatch(p -> p.holds(model, sets))) {
                return true;
            }
        }
        return false;
    }

    /** True when the conclusion holds in every model the premises allow. */
    static boolean follows(List<Claim> premises, Claim conclusion, int sets) {
        for (long model : models(sets)) {
            if (premises.stream().allMatch(p -> p.holds(model, sets)) && !conclusion.holds(model, sets)) {
                return false;
            }
        }
        return true;
    }

    // ---------------- Arrangements and ranking ----------------

    private static final List<String> FOLK = List.of("Asha", "Ravi", "Meena", "Kiran", "Arjun", "Divya", "Sameer", "Priya", "Rahul", "Neha",
            "Vikram", "Anu", "Farhan", "Lakshmi", "Joseph", "Gita");

    static Seed arrangements(String key, Difficulty d, Random r) {
        String t = "Arrangements and ranking";
        if (d == Difficulty.EASY) {
            String who = pick(r, FOLK);
            int front = between(r, 3, 25);
            int back = between(r, 3, 25);
            int total = front + back - 1;
            return switch (r.nextInt(3)) {
                case 0 -> choice(key, S, t, d, "In a queue, " + who + " is " + ordinal(front) + " from the front and " + ordinal(back)
                        + " from the back. How many people are in the queue?", null, String.valueOf(total),
                        List.of(String.valueOf(total + 1), String.valueOf(total + 2), String.valueOf(total - 1)),
                        front + " + " + back + " − 1 = " + total + " (" + who + " is counted twice).");
                case 1 -> choice(key, S, t, d, "In a class of " + total + " students, " + who + " ranks " + ordinal(front)
                        + " from the top. What is " + who + "'s rank from the bottom?", null, ordinal(back),
                        List.of(ordinal(back + 1), ordinal(back - 1 < 1 ? back + 2 : back - 1), ordinal(total - front)),
                        total + " − " + front + " + 1 = " + back + ".");
                default -> {
                    int between = between(r, 2, 9);
                    String other = pick(r, FOLK.stream().filter(n -> !n.equals(who)).toList());
                    int pos = front + between + 1;
                    yield choice(key, S, t, d, who + " is " + ordinal(front) + " in a line. There are " + between + " people between " + who + " and "
                            + other + ", who is behind " + who + ". What is " + other + "'s position from the front?", null, ordinal(pos),
                            List.of(ordinal(pos - 1), ordinal(pos + 1), ordinal(front + between)),
                            front + " + " + between + " + 1 = " + pos + ".");
                }
            };
        }
        if (d == Difficulty.MEDIUM || r.nextBoolean()) {
            int n = d == Difficulty.MEDIUM ? 5 : 6;
            return seating(key, d, r, t, n);
        }
        return heights(key, d, r, t);
    }

    private static String ordinal(int n) {
        String suffix = n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** A clue about a row: holds(perm) where perm[seat] = person. */
    private record Clue(String text, java.util.function.Predicate<int[]> holds) {}

    private static Seed seating(String key, Difficulty d, Random r, String t, int n) {
        List<String> names = new ArrayList<>(FOLK);
        Collections.shuffle(names, r);
        names = new ArrayList<>(names.subList(0, n));
        List<int[]> perms = permutations(n);
        int[] truth = perms.get(r.nextInt(perms.size()));
        List<Clue> clues = new ArrayList<>();
        Set<String> used = new HashSet<>();
        List<int[]> left = perms;
        for (int guard = 0; left.size() > 1 && guard < 400; guard++) {
            Clue c = seatClue(r, truth, names, n);
            if (c == null || !used.add(c.text())) {
                continue;
            }
            List<int[]> narrowed = left.stream().filter(c.holds()).toList();
            if (narrowed.size() < left.size()) {
                clues.add(c);
                left = narrowed;
            }
        }
        if (left.size() != 1 || clues.size() < 3) {
            return seating(key, d, r, t, n);
        }
        StringBuilder prompt = new StringBuilder(String.join(", ", names.subList(0, n - 1)) + " and " + names.get(n - 1) + " sit in a row facing north. ");
        Collections.shuffle(clues, r);
        for (Clue c : clues) {
            prompt.append(c.text()).append(' ');
        }
        int[] seatOf = new int[n];
        for (int s = 0; s < n; s++) {
            seatOf[truth[s]] = s;
        }
        String answer;
        String question;
        switch (r.nextInt(4)) {
            case 0 -> {
                question = "Who sits at the extreme left end?";
                answer = names.get(truth[0]);
            }
            case 1 -> {
                question = "Who sits at the extreme right end?";
                answer = names.get(truth[n - 1]);
            }
            case 2 -> {
                int s = between(r, 0, n - 2);
                question = "Who sits immediately to the right of " + names.get(truth[s]) + "?";
                answer = names.get(truth[s + 1]);
            }
            default -> {
                int s = between(r, 1, n - 2);
                question = "Who sits between " + names.get(truth[s - 1]) + " and " + names.get(truth[s + 1]) + "?";
                answer = names.get(truth[s]);
            }
        }
        List<String> others = new ArrayList<>(names);
        others.remove(answer);
        Collections.shuffle(others, r);
        StringBuilder order = new StringBuilder();
        for (int s = 0; s < n; s++) {
            order.append(s == 0 ? "" : " – ").append(names.get(truth[s]));
        }
        return choice(key, S, t, d, prompt.append(question).toString().strip(), null, answer, others,
                "The only order (left to right) that fits every clue is " + order + ".");
    }

    private static Clue seatClue(Random r, int[] truth, List<String> names, int n) {
        int[] seatOf = new int[n];
        for (int s = 0; s < n; s++) {
            seatOf[truth[s]] = s;
        }
        int a = r.nextInt(n);
        int b;
        do {
            b = r.nextInt(n);
        } while (b == a);
        String na = names.get(a);
        String nb = names.get(b);
        final int fa = a;
        final int fb = b;
        switch (r.nextInt(6)) {
            case 0 -> {
                if (seatOf[a] == 0 || seatOf[a] == n - 1) {
                    return new Clue(na + " sits at one of the ends.", p -> seat(p, fa) == 0 || seat(p, fa) == n - 1);
                }
                return new Clue(na + " does not sit at either end.", p -> seat(p, fa) != 0 && seat(p, fa) != n - 1);
            }
            case 1 -> {
                if (seatOf[a] + 1 == seatOf[b]) {
                    return new Clue(na + " sits immediately to the left of " + nb + ".", p -> seat(p, fa) + 1 == seat(p, fb));
                }
                return null;
            }
            case 2 -> {
                if (seatOf[a] > seatOf[b]) {
                    return new Clue(na + " sits somewhere to the right of " + nb + ".", p -> seat(p, fa) > seat(p, fb));
                }
                return null;
            }
            case 3 -> {
                if (Math.abs(seatOf[a] - seatOf[b]) == 1) {
                    return new Clue(na + " and " + nb + " sit next to each other.", p -> Math.abs(seat(p, fa) - seat(p, fb)) == 1);
                }
                return new Clue(na + " and " + nb + " do not sit next to each other.", p -> Math.abs(seat(p, fa) - seat(p, fb)) != 1);
            }
            case 4 -> {
                if (n % 2 == 1 && seatOf[a] == n / 2) {
                    return new Clue(na + " sits in the middle.", p -> seat(p, fa) == n / 2);
                }
                int gap = Math.abs(seatOf[a] - seatOf[b]) - 1;
                if (gap >= 1) {
                    return new Clue("Exactly " + gap + (gap == 1 ? " person sits" : " people sit") + " between " + na + " and " + nb + ".",
                            p -> Math.abs(seat(p, fa) - seat(p, fb)) - 1 == gap);
                }
                return null;
            }
            default -> {
                int s = seatOf[a];
                if (s == 0) {
                    return new Clue(na + " sits at the extreme left end.", p -> seat(p, fa) == 0);
                }
                if (s == n - 1) {
                    return new Clue(na + " sits at the extreme right end.", p -> seat(p, fa) == n - 1);
                }
                return null;
            }
        }
    }

    private static int seat(int[] perm, int person) {
        for (int s = 0; s < perm.length; s++) {
            if (perm[s] == person) {
                return s;
            }
        }
        return -1;
    }

    private static List<int[]> permutations(int n) {
        List<int[]> out = new ArrayList<>();
        permute(new int[n], new boolean[n], 0, out);
        return out;
    }

    private static void permute(int[] cur, boolean[] used, int i, List<int[]> out) {
        if (i == cur.length) {
            out.add(cur.clone());
            return;
        }
        for (int v = 0; v < cur.length; v++) {
            if (!used[v]) {
                used[v] = true;
                cur[i] = v;
                permute(cur, used, i + 1, out);
                used[v] = false;
            }
        }
    }

    private static Seed heights(String key, Difficulty d, Random r, String t) {
        int n = 5;
        List<String> names = new ArrayList<>(FOLK);
        Collections.shuffle(names, r);
        names = new ArrayList<>(names.subList(0, n));
        List<int[]> perms = permutations(n);
        int[] truth = perms.get(r.nextInt(perms.size()));
        List<Clue> clues = new ArrayList<>();
        Set<String> used = new HashSet<>();
        List<int[]> left = perms;
        for (int guard = 0; left.size() > 1 && guard < 400; guard++) {
            int i = r.nextInt(n);
            int j = r.nextInt(n);
            if (Math.abs(i - j) < 1 || Math.abs(i - j) > 2) {
                continue;
            }
            int taller = truth[Math.min(i, j)];
            int shorter = truth[Math.max(i, j)];
            String text = r.nextBoolean() ? names.get(taller) + " is taller than " + names.get(shorter) + "."
                    : names.get(shorter) + " is shorter than " + names.get(taller) + ".";
            if (!used.add(names.get(taller) + ">" + names.get(shorter))) {
                continue;
            }
            Clue c = new Clue(text, p -> seat(p, taller) < seat(p, shorter));
            List<int[]> narrowed = left.stream().filter(c.holds()).toList();
            if (narrowed.size() < left.size()) {
                clues.add(c);
                left = narrowed;
            }
        }
        if (left.size() != 1) {
            return heights(key, d, r, t);
        }
        Collections.shuffle(clues, r);
        StringBuilder prompt = new StringBuilder("Among five friends, ");
        for (Clue c : clues) {
            prompt.append(c.text()).append(' ');
        }
        int rank = r.nextInt(n);
        String[] asks = {"Who is the tallest?", "Who is the second tallest?", "Who is in the middle by height?", "Who is the second shortest?",
                "Who is the shortest?"};
        String answer = names.get(truth[rank]);
        List<String> others = new ArrayList<>(names);
        others.remove(answer);
        Collections.shuffle(others, r);
        StringBuilder order = new StringBuilder();
        for (int s = 0; s < n; s++) {
            order.append(s == 0 ? "" : " > ").append(names.get(truth[s]));
        }
        return choice(key, S, t, d, prompt.append(asks[rank]).toString(), null, answer, others,
                "The clues give one order, tallest first: " + order + ".");
    }
}
