package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.AssessmentQuestion.Kind;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.ArrayList;
import java.util.List;

/** Hand-written verbal and reasoning questions for the built-in aptitude bank (ADR-0014). */
final class AptitudeWritten {

    private AptitudeWritten() {}

    private static final Difficulty E = Difficulty.EASY;
    private static final Difficulty M = Difficulty.MEDIUM;
    private static final Difficulty H = Difficulty.HARD;

    private static final String PASSAGE_WORK = "Read the passage and answer the question.\n\n"
            + "\"Many companies now let employees work from home two or three days a week. Supporters say this saves travel "
            + "time and helps people focus on difficult tasks. Critics worry that new employees learn more slowly when they "
            + "rarely sit with experienced colleagues. To balance this, some firms ask new joiners to come to the office more "
            + "often during their first three months.\"\n\n";

    private static final String PASSAGE_SOLAR = "Read the passage and answer the question.\n\n"
            + "\"Solar power has become much cheaper over the last decade, mainly because panels are now made in very large "
            + "numbers. However, the sun does not shine at night, so electricity must be stored or supplied from other sources. "
            + "Better batteries are therefore as important as cheaper panels if solar power is to meet most of a country's needs.\"\n\n";

    private static final List<String> SYLLOGISM = List.of("Only conclusion I follows", "Only conclusion II follows",
            "Both conclusions follow", "Neither conclusion follows");

    private static final List<String> PARTS = List.of("Part A", "Part B", "Part C", "No error");

    static List<Seed> all() {
        List<Seed> out = new ArrayList<>();
        String v = "Synonyms";
        out.add(q("syn:1", Section.VERBAL, v, E, "Choose the word closest in meaning to ABUNDANT.", List.of("Plentiful", "Scarce", "Ordinary", "Tiny"), 0,
                "Abundant means existing in large quantities: plentiful."));
        out.add(q("syn:2", Section.VERBAL, v, E, "Choose the word closest in meaning to BRIEF.", List.of("Short", "Long", "Loud", "Bright"), 0,
                "Brief means short in time or length."));
        out.add(q("syn:3", Section.VERBAL, v, E, "Choose the word closest in meaning to DILIGENT.", List.of("Hard-working", "Lazy", "Careless", "Clever"), 0,
                "A diligent person works carefully and steadily."));
        out.add(q("syn:4", Section.VERBAL, v, M, "Choose the word closest in meaning to CANDID.", List.of("Frank", "Secretive", "Rude", "Shy"), 0,
                "Candid means open and honest: frank."));
        out.add(q("syn:5", Section.VERBAL, v, M, "Choose the word closest in meaning to METICULOUS.", List.of("Very careful", "Hasty", "Generous", "Talkative"), 0,
                "Meticulous means showing great attention to detail."));
        out.add(q("syn:6", Section.VERBAL, v, H, "Choose the word closest in meaning to EPHEMERAL.", List.of("Short-lived", "Permanent", "Heavenly", "Ancient"), 0,
                "Ephemeral means lasting a very short time."));

        String a = "Antonyms";
        out.add(q("ant:1", Section.VERBAL, a, E, "Choose the word most opposite in meaning to ANCIENT.", List.of("Modern", "Old", "Historic", "Aged"), 0,
                "Ancient means very old; the opposite is modern."));
        out.add(q("ant:2", Section.VERBAL, a, E, "Choose the word most opposite in meaning to EXPAND.", List.of("Contract", "Extend", "Enlarge", "Spread"), 0,
                "To expand is to grow larger; to contract is to grow smaller."));
        out.add(q("ant:3", Section.VERBAL, a, M, "Choose the word most opposite in meaning to OPTIMISTIC.", List.of("Pessimistic", "Hopeful", "Cheerful", "Positive"), 0,
                "Optimistic means expecting good outcomes; pessimistic means expecting bad ones."));
        out.add(q("ant:4", Section.VERBAL, a, M, "Choose the word most opposite in meaning to TRANSPARENT.", List.of("Opaque", "Clear", "Obvious", "Glassy"), 0,
                "Transparent things let light through; opaque things don't."));
        out.add(q("ant:5", Section.VERBAL, a, H, "Choose the word most opposite in meaning to FRUGAL.", List.of("Extravagant", "Thrifty", "Careful", "Simple"), 0,
                "Frugal means spending little; extravagant means spending a lot."));
        out.add(q("ant:6", Section.VERBAL, a, H, "Choose the word most opposite in meaning to MITIGATE.", List.of("Aggravate", "Reduce", "Ease", "Soften"), 0,
                "To mitigate is to make less severe; to aggravate is to make worse."));

        String c = "Sentence completion";
        out.add(q("fill:1", Section.VERBAL, c, E, "Fill in the blank: She has been working here ___ 2022.", List.of("since", "for", "from", "by"), 0,
                "\"Since\" is used with a point in time with the present perfect."));
        out.add(q("fill:2", Section.VERBAL, c, E, "Fill in the blank: He is good ___ solving puzzles.", List.of("at", "in", "on", "with"), 0,
                "The phrase is \"good at\"."));
        out.add(q("fill:3", Section.VERBAL, c, E, "Fill in the blank: The report must be submitted ___ Friday.", List.of("by", "until", "since", "at"), 0,
                "\"By Friday\" means no later than Friday, which suits a deadline."));
        out.add(q("fill:4", Section.VERBAL, c, M, "Fill in the blank: Neither the manager nor the team members ___ aware of the change.",
                List.of("were", "was", "is", "has been"), 0, "With neither … nor, the verb agrees with the nearer subject (team members), so \"were\"."));
        out.add(q("fill:5", Section.VERBAL, c, M, "Fill in the blank: If I ___ you, I would accept the offer.", List.of("were", "am", "will be", "have been"), 0,
                "An imaginary condition takes \"were\": If I were you …"));
        out.add(q("fill:6", Section.VERBAL, c, H, "Fill in the blank: Hardly had we reached the station ___ the train left.", List.of("when", "than", "then", "and"), 0,
                "\"Hardly … when\" is the correct pair."));

        String err = "Error spotting";
        out.add(q("err:1", Section.VERBAL, err, E, "Find the part with an error: (A) He don't know / (B) the answer / (C) to this question.", PARTS, 0,
                "With \"he\", use \"doesn't\": He doesn't know."));
        out.add(q("err:2", Section.VERBAL, err, M, "Find the part with an error: (A) Each of the students / (B) have submitted / (C) the assignment.", PARTS, 1,
                "\"Each\" is singular, so \"has submitted\"."));
        out.add(q("err:3", Section.VERBAL, err, M, "Find the part with an error: (A) The news / (B) are very good / (C) today.", PARTS, 1,
                "\"News\" is uncountable and takes a singular verb: is."));
        out.add(q("err:4", Section.VERBAL, err, M, "Find the part with an error: (A) I have seen / (B) that movie / (C) yesterday.", PARTS, 0,
                "With a finished time (yesterday), use the simple past: I saw."));
        out.add(q("err:5", Section.VERBAL, err, M, "Find the part with an error: (A) She is one of the / (B) best player / (C) in the team.", PARTS, 1,
                "\"One of the\" is followed by a plural: best players."));
        out.add(q("err:6", Section.VERBAL, err, H, "Find the part with an error: (A) The team / (B) has finished / (C) its project on time.", PARTS, 3,
                "The sentence is correct: \"team\" takes \"has\" and \"its\"."));

        String rc = "Reading comprehension";
        out.add(q("rc:1", Section.VERBAL, rc, E, PASSAGE_WORK + "According to the passage, what is one concern about working from home?",
                List.of("New employees may learn more slowly", "It costs companies more money", "Employees travel more", "Difficult tasks take longer"), 0,
                "Critics worry that new employees learn more slowly."));
        out.add(q("rc:2", Section.VERBAL, rc, E, PASSAGE_WORK + "What do some firms ask new joiners to do?",
                List.of("Come to the office more often in their first three months", "Work from home every day", "Travel less", "Choose their own team"), 0,
                "The last sentence says so."));
        out.add(q("rc:3", Section.VERBAL, rc, M, PASSAGE_WORK + "What is the main idea of the passage?",
                List.of("Companies are balancing the benefits and drawbacks of working from home", "Working from home should be banned",
                        "Travel time is the biggest problem for employees", "New employees do not want to work from home"), 0,
                "The passage gives both sides and a way some firms balance them."));
        out.add(q("rc:4", Section.VERBAL, rc, E, PASSAGE_SOLAR + "Why has solar power become cheaper?",
                List.of("Panels are now made in very large numbers", "The sun shines for longer", "Batteries have become free",
                        "Other power sources were banned"), 0, "The first sentence gives the reason."));
        out.add(q("rc:5", Section.VERBAL, rc, M, PASSAGE_SOLAR + "Which statement does the passage support?",
                List.of("Electricity must be stored because panels produce no power at night", "Solar power is no longer useful",
                        "Batteries matter less than panels", "Solar power already meets all of a country's needs"), 0,
                "The sun does not shine at night, so electricity must be stored or supplied from elsewhere."));
        out.add(q("rc:6", Section.VERBAL, rc, H, PASSAGE_SOLAR + "In the last sentence, what does the word \"therefore\" show?",
                List.of("A conclusion drawn from the previous point", "A contrast with the previous point", "An example", "An order of events"), 0,
                "\"Therefore\" introduces a conclusion: because the sun doesn't shine at night, batteries matter."));

        String br = "Blood relations";
        out.add(q("blood:1", Section.LOGICAL, br, E, "A is B's sister. C is B's mother. D is C's father. How is A related to D?",
                List.of("Granddaughter", "Daughter", "Grandmother", "Niece"), 0, "A is C's daughter, and C is D's child, so A is D's granddaughter."));
        out.add(q("blood:2", Section.LOGICAL, br, E, "X is Y's brother. Y is Z's sister. Z is W's father. How is X related to W?",
                List.of("Uncle", "Father", "Brother", "Cousin"), 0, "X, Y and Z are siblings, and Z is W's father, so X is W's uncle."));
        out.add(q("blood:3", Section.LOGICAL, br, E, "P is Q's husband. R is Q's only son. S is R's sister. How is S related to P?",
                List.of("Daughter", "Sister", "Niece", "Wife"), 0, "R and S are Q and P's children, so S is P's daughter."));
        out.add(q("blood:4", Section.LOGICAL, br, M, "Pointing to a boy, Neha says, \"He is the son of my father's only son.\" How is the boy related to Neha?",
                List.of("Nephew", "Brother", "Son", "Cousin"), 0, "Her father's only son is her brother; his son is her nephew."));
        out.add(q("blood:5", Section.LOGICAL, br, M, "Ravi's mother is the only daughter of Sunil's mother. How is Sunil related to Ravi?",
                List.of("Maternal uncle", "Father", "Brother", "Grandfather"), 0,
                "Sunil's mother's only daughter is Sunil's sister, who is Ravi's mother, so Sunil is Ravi's maternal uncle."));
        out.add(q("blood:6", Section.LOGICAL, br, H, "If A × B means A is the father of B, and A − B means A is the brother of B, what does P × Q − R mean?",
                List.of("P is the father of R", "P is the brother of R", "R is the son of Q", "P is the uncle of R"), 0,
                "P is Q's father and Q is R's brother, so P is also R's father."));

        String sy = "Syllogisms";
        out.add(q("syl:1", Section.LOGICAL, sy, E, "Statements: All dogs are animals. All animals are living beings.\n"
                + "Conclusions: I. All dogs are living beings. II. Some living beings are dogs.", SYLLOGISM, 2,
                "All dogs are living beings, so some living beings are dogs: both follow."));
        out.add(q("syl:2", Section.LOGICAL, sy, M, "Statements: Some pens are books. All books are tables.\n"
                + "Conclusions: I. Some pens are tables. II. All tables are pens.", SYLLOGISM, 0,
                "The pens that are books are also tables, so I follows; nothing says all tables are pens."));
        out.add(q("syl:3", Section.LOGICAL, sy, M, "Statements: All cars are bikes. No bike is a truck.\n"
                + "Conclusions: I. No car is a truck. II. Some trucks are cars.", SYLLOGISM, 0,
                "Cars are inside bikes, and bikes and trucks don't overlap, so no car is a truck; II contradicts I."));
        out.add(q("syl:4", Section.LOGICAL, sy, M, "Statements: All engineers are graduates. Some graduates are teachers.\n"
                + "Conclusions: I. Some engineers are teachers. II. All teachers are graduates.", SYLLOGISM, 3,
                "The teachers among graduates need not be engineers, and only some teachers are known to be graduates."));
        out.add(q("syl:5", Section.LOGICAL, sy, H, "Statements: Some cats are dogs. Some dogs are rats.\n"
                + "Conclusions: I. Some cats are rats. II. Some rats are dogs.", SYLLOGISM, 1,
                "\"Some dogs are rats\" means some rats are dogs (II); cats and rats need not overlap."));
        out.add(q("syl:6", Section.LOGICAL, sy, H, "Statements: No apple is a mango. All mangoes are fruits.\n"
                + "Conclusions: I. No apple is a fruit. II. Some fruits are not apples.", SYLLOGISM, 1,
                "Mangoes are fruits that are not apples, so II follows; apples may still be fruits."));

        String ar = "Arrangements and ranking";
        out.add(q("seat:1", Section.LOGICAL, ar, E, "In a queue, Meera is 7th from the front and 12th from the back. How many people are in the queue?",
                List.of("18", "19", "17", "20"), 0, "7 + 12 − 1 = 18 (Meera is counted twice)."));
        out.add(q("seat:2", Section.LOGICAL, ar, E, "In a row of 30 students, Kavin is 9th from the left. What is his position from the right?",
                List.of("22nd", "21st", "23rd", "20th"), 0, "30 − 9 + 1 = 22."));
        out.add(q("seat:3", Section.LOGICAL, ar, M, "Sam is taller than Raj. Raj is taller than Tom. Ann is taller than Sam. Who is the second tallest?",
                List.of("Sam", "Ann", "Raj", "Tom"), 0, "Ann > Sam > Raj > Tom."));
        out.add(q("seat:4", Section.LOGICAL, ar, M, "Five friends A, B, C, D and E sit in a row facing north. C is in the middle. A is to the immediate left of C. "
                + "B is at the right end. E is not at either end. Who sits at the left end?", List.of("D", "E", "A", "B"), 0,
                "From the left: D, A, C, E, B."));
        out.add(q("seat:5", Section.LOGICAL, ar, H, "Five friends A, B, C, D and E sit in a row facing north. C is in the middle. A is to the immediate left of C. "
                + "B is at the right end. E is not at either end. Who sits between C and B?", List.of("E", "D", "A", "Nobody"), 0,
                "From the left: D, A, C, E, B, so E sits between C and B."));
        return out;
    }

    private static Seed q(String id, Section section, String topic, Difficulty difficulty, String prompt, List<String> options,
            int correct, String explanation) {
        return new Seed(AptitudeBank.VERSION + ":" + id, section, topic, difficulty, Kind.SINGLE_CHOICE, prompt, null,
                options, null, List.of(correct), List.of(), explanation);
    }
}
