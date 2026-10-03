package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An offline stand-in for AI drafting, used in dev and demo and in tests: hands out questions
 * from a small built-in starter bank (aptitude, Java, Python), skipping ones already in the test.
 */
public class SampleAssessmentDrafter implements AssessmentDrafter {

    private static AssessmentDraft.Question choice(String prompt, String code, List<String> options, int correct,
            String explanation) {
        return new AssessmentDraft.Question("SINGLE_CHOICE", prompt, code, options, List.of(correct), List.of(),
                explanation, 1);
    }

    private static AssessmentDraft.Question multi(String prompt, List<String> options, List<Integer> correct,
            String explanation) {
        return new AssessmentDraft.Question("MULTI_CHOICE", prompt, "", options, correct, List.of(), explanation, 2);
    }

    private static AssessmentDraft.Question shortAnswer(String prompt, String code, List<String> accepted,
            String explanation) {
        return new AssessmentDraft.Question("SHORT_ANSWER", prompt, code, List.of(), List.of(), accepted, explanation, 2);
    }

    static final List<AssessmentDraft.Question> APTITUDE = List.of(
            choice("A train 150 m long passes a pole in 15 seconds. What is its speed?", "",
                    List.of("30 km/h", "36 km/h", "40 km/h", "54 km/h"), 1, "150 m / 15 s = 10 m/s = 36 km/h."),
            choice("6 workers finish a job in 12 days. How many days will 9 workers take at the same rate?", "",
                    List.of("6", "8", "9", "10"), 1, "6 × 12 = 72 worker-days; 72 / 9 = 8."),
            choice("What comes next: 2, 6, 12, 20, 30, ?", "", List.of("40", "42", "44", "48"), 1,
                    "Differences grow by 2: 4, 6, 8, 10, 12 → 30 + 12 = 42."),
            choice("A price goes up by 20% and then down by 20%. What is the overall change?", "",
                    List.of("No change", "4% decrease", "4% increase", "2% decrease"), 1, "1.2 × 0.8 = 0.96, a 4% decrease."),
            shortAnswer("A and B share ₹64 in the ratio 3 : 5. How many rupees does B get? (number only)", "",
                    List.of("40"), "64 × 5/8 = 40."),
            choice("Two fair dice are rolled. What is the probability that the total is 7?", "",
                    List.of("1/6", "1/12", "7/36", "5/36"), 0, "6 of the 36 outcomes add up to 7."));

    static final List<AssessmentDraft.Question> JAVA = List.of(
            choice("What does this print?", """
                    String a = "hi";
                    String b = new String("hi");
                    System.out.println((a == b) + " " + a.equals(b));""",
                    List.of("true true", "false true", "true false", "false false"), 1,
                    "== compares references (different objects); equals compares contents."),
            choice("Which collection keeps insertion order and allows no duplicates?", "",
                    List.of("HashSet", "TreeSet", "LinkedHashSet", "ArrayList"), 2,
                    "LinkedHashSet is a Set that remembers insertion order."),
            shortAnswer("What does this print?", """
                    int x = 5;
                    x += x++ + ++x;
                    System.out.println(x);""", List.of("17"),
                    "x += … keeps the old x (5); x++ gives 5 (x becomes 6), ++x gives 7; 5 + 5 + 7 = 17."),
            choice("In Spring Boot, which annotation makes a class handle HTTP requests and return JSON by default?", "",
                    List.of("@Controller", "@RestController", "@Service", "@Component"), 1,
                    "@RestController = @Controller + @ResponseBody."),
            multi("Which of these are checked exceptions?",
                    List.of("IOException", "SQLException", "NullPointerException", "IllegalArgumentException"),
                    List.of(0, 1), "IOException and SQLException are checked; the other two are RuntimeExceptions."),
            shortAnswer("What does this print?", """
                    List<Integer> nums = List.of(1, 2, 3, 4, 5);
                    int sum = nums.stream().filter(n -> n % 2 == 1).mapToInt(n -> n * n).sum();
                    System.out.println(sum);""", List.of("35"), "Odd numbers 1, 3, 5 squared: 1 + 9 + 25 = 35."));

    static final List<AssessmentDraft.Question> PYTHON = List.of(
            choice("What does this print?", "print([i * 2 for i in range(4)])",
                    List.of("[2, 4, 6, 8]", "[0, 2, 4, 6]", "[0, 2, 4, 6, 8]", "[0, 1, 2, 3]"), 1,
                    "range(4) is 0, 1, 2, 3; each is doubled."),
            choice("What does this print?", """
                    def add(item, bucket=[]):
                        bucket.append(item)
                        return bucket

                    add(1)
                    print(add(2))""", List.of("[2]", "[1, 2]", "An error", "[1]"), 1,
                    "The default list is created once and shared between calls."),
            choice("Which of these types is immutable?", "", List.of("list", "dict", "set", "tuple"), 3,
                    "Tuples can't be changed after creation."),
            shortAnswer("What does this print?", "print(len({1, 2, 2, 3, 3, 3}))", List.of("3"),
                    "A set keeps unique values: {1, 2, 3}."),
            shortAnswer("What does this print?", """
                    s = "codewalnut"
                    print(s[::-1][:4])""", List.of("tunl"), "Reversed: \"tunlawedoc\"; the first four letters are \"tunl\"."),
            multi("Which of these create a dictionary?", List.of("{}", "dict()", "set()", "dict(a=1)"),
                    List.of(0, 1, 3), "{} is an empty dict (not a set); set() makes a set."));

    private static final Map<String, List<AssessmentDraft.Question>> BANK = Map.of(
            "APTITUDE", APTITUDE, "JAVA", JAVA, "PYTHON", PYTHON);

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AssessmentDraft draft(Request request) {
        List<AssessmentDraft.Question> bank = BANK.getOrDefault(request.category(), APTITUDE);
        List<AssessmentDraft.Question> out = new ArrayList<>();
        for (AssessmentDraft.Question q : bank) {
            if (out.size() >= request.count()) {
                break;
            }
            if (!request.avoid().contains(q.prompt() + (q.code().isEmpty() ? "" : "\n" + q.code()))) {
                out.add(q);
            }
        }
        return new AssessmentDraft(out);
    }
}
