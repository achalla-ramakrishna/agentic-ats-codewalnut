package com.codewalnut.ats.bank;

import static com.codewalnut.ats.bank.AptitudeBank.choice;
import static com.codewalnut.ats.bank.AptitudeBank.pick;

import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.util.List;
import java.util.Random;

/**
 * Verbal-ability questions from curated lists, at least 17 per difficulty for each topic (ADR-0014).
 * Entries are "prompt|answer|distractor|distractor|distractor"; error-spotting entries are
 * "part A|part B|part C|answer letter or N|why".
 */
final class Verbal {

    private static final Section S = Section.VERBAL;

    private Verbal() {}

    private static List<String> level(Difficulty d, List<String> easy, List<String> medium, List<String> hard) {
        return d == Difficulty.EASY ? easy : d == Difficulty.MEDIUM ? medium : hard;
    }

    // ---------------- Synonyms ----------------

    private static final List<String> SYN_EASY = List.of(
            "HAPPY|Glad|Angry|Tired|Proud", "BEGIN|Start|Finish|Wait|Pause", "FAST|Quick|Slow|Heavy|Late", "BIG|Large|Tiny|Thin|Short",
            "SMART|Clever|Foolish|Rude|Weak", "SHUT|Close|Open|Break|Lift", "ANGRY|Cross|Calm|Kind|Sleepy", "RICH|Wealthy|Poor|Busy|Noisy",
            "HELP|Assist|Hinder|Ignore|Leave", "BRAVE|Courageous|Timid|Careless|Gentle", "SIMPLE|Easy|Hard|Long|Strange",
            "ANSWER|Reply|Question|Doubt|Request", "BUY|Purchase|Sell|Borrow|Lend", "QUIET|Silent|Loud|Busy|Bright",
            "MISTAKE|Error|Success|Rule|Lesson", "SHOW|Display|Hide|Cover|Forget", "END|Finish|Start|Middle|Open", "FUNNY|Amusing|Boring|Sad|Serious",
            "TRUST|Faith|Doubt|Fear|Anger");
    private static final List<String> SYN_MEDIUM = List.of(
            "CANDID|Frank|Secretive|Cautious|Polite", "ABUNDANT|Plentiful|Scarce|Costly|Hidden", "DILIGENT|Hard-working|Careless|Idle|Clever",
            "OBSOLETE|Outdated|Modern|Useful|Rare", "VITAL|Essential|Optional|Weak|Minor", "ACCURATE|Exact|Rough|Early|Vague",
            "GENUINE|Authentic|Fake|Partial|Similar", "HOSTILE|Unfriendly|Welcoming|Neutral|Shy", "PROMPT|Punctual|Delayed|Lazy|Rare",
            "FEEBLE|Weak|Strong|Brave|Fierce", "COMMENCE|Begin|Conclude|Suspend|Delay", "RELUCTANT|Unwilling|Eager|Ready|Quick",
            "CONCEAL|Hide|Reveal|Destroy|Discover", "FLEXIBLE|Adaptable|Rigid|Fragile|Narrow", "ADEQUATE|Sufficient|Lacking|Excessive|Perfect",
            "DURABLE|Long-lasting|Fragile|Cheap|Light", "TRIVIAL|Unimportant|Serious|Vital|Complex", "ANXIOUS|Worried|Relaxed|Excited|Bored",
            "ELIMINATE|Remove|Include|Create|Repeat");
    private static final List<String> SYN_HARD = List.of(
            "EPHEMERAL|Short-lived|Eternal|Visible|Heavenly", "UBIQUITOUS|Everywhere|Unique|Rare|Unknown", "PRAGMATIC|Practical|Idealistic|Rigid|Lazy",
            "METICULOUS|Very careful|Careless|Hasty|Clumsy", "AMELIORATE|Improve|Worsen|Measure|Ignore", "LOQUACIOUS|Talkative|Silent|Rude|Clever",
            "BENEVOLENT|Kind-hearted|Cruel|Greedy|Jealous", "PERNICIOUS|Harmful|Helpful|Hidden|Harmless", "ESOTERIC|Obscure|Popular|Simple|Ancient",
            "CAPRICIOUS|Unpredictable|Steady|Generous|Timid", "OSTENSIBLE|Apparent|Hidden|Genuine|Proven", "TACITURN|Reserved|Chatty|Angry|Joyful",
            "ACRIMONIOUS|Bitter|Friendly|Sweet|Calm", "SAGACIOUS|Wise|Foolish|Fierce|Sad", "PROPITIOUS|Favourable|Unlucky|Hostile|Doubtful",
            "OBDURATE|Stubborn|Yielding|Gentle|Weak", "MAGNANIMOUS|Generous|Petty|Selfish|Proud", "ENIGMATIC|Mysterious|Obvious|Plain|Noisy");

    static Seed synonyms(String key, Difficulty d, Random r) {
        String[] e = pick(r, level(d, SYN_EASY, SYN_MEDIUM, SYN_HARD)).split("\\|");
        return choice(key, S, "Synonyms", d, "Choose the word closest in meaning to " + e[0] + ".", null, e[1], List.of(e[2], e[3], e[4]),
                e[0] + " means " + e[1].toLowerCase() + ". " + e[2] + " is close to its opposite.");
    }

    // ---------------- Antonyms ----------------

    private static final List<String> ANT_EASY = List.of(
            "HOT|Cold|Warm|Dry|Bright", "EARLY|Late|Soon|Quick|First", "STRONG|Weak|Tough|Large|Firm", "ACCEPT|Refuse|Receive|Agree|Allow",
            "VICTORY|Defeat|Success|Battle|Prize", "ARRIVE|Depart|Reach|Enter|Return", "ANCIENT|Modern|Old|Historic|Rare", "SHARP|Blunt|Pointed|Thin|Bright",
            "EXPAND|Shrink|Grow|Stretch|Spread", "GENEROUS|Mean|Kind|Rich|Open", "FREQUENT|Rare|Regular|Common|Quick", "PROFIT|Loss|Gain|Price|Trade",
            "INCREASE|Decrease|Raise|Double|Add", "HONEST|Deceitful|Truthful|Sincere|Fair", "FOOLISH|Wise|Silly|Funny|Young", "MAXIMUM|Minimum|Most|Average|Total",
            "SUNRISE|Sunset|Dawn|Noon|Morning", "INTERIOR|Exterior|Inside|Middle|Centre", "ASLEEP|Awake|Dozing|Lazy|Hungry");
    private static final List<String> ANT_MEDIUM = List.of(
            "FRUGAL|Extravagant|Thrifty|Careful|Simple", "TRANSPARENT|Opaque|Clear|Thin|Bright", "SCARCE|Abundant|Rare|Few|Limited",
            "HUMBLE|Arrogant|Modest|Shy|Polite", "TEMPORARY|Permanent|Brief|Short|Passing", "INFERIOR|Superior|Lower|Lesser|Junior",
            "VOLUNTARY|Compulsory|Willing|Optional|Free", "OPTIMIST|Pessimist|Hopeful person|Realist|Idealist", "ASCEND|Descend|Climb|Rise|Mount",
            "CONFIDENT|Doubtful|Sure|Bold|Certain", "RIGID|Flexible|Stiff|Firm|Hard", "VAGUE|Precise|Unclear|Hazy|Loose",
            "PERMIT|Forbid|Allow|Grant|Approve", "OBEDIENT|Rebellious|Dutiful|Loyal|Polite", "MAJORITY|Minority|Bulk|Mass|Most",
            "ATTRACT|Repel|Draw|Pull|Charm", "CONSTRUCT|Demolish|Build|Erect|Assemble", "NOVICE|Expert|Beginner|Learner|Trainee",
            "GENUINE|Counterfeit|Real|True|Original");
    private static final List<String> ANT_HARD = List.of(
            "LOQUACIOUS|Reticent|Garrulous|Fluent|Verbose", "EPHEMERAL|Everlasting|Fleeting|Brief|Passing", "BENIGN|Malignant|Harmless|Gentle|Mild",
            "ZENITH|Nadir|Peak|Summit|Apex", "AFFLUENT|Impoverished|Wealthy|Prosperous|Opulent", "DEARTH|Profusion|Scarcity|Lack|Shortage",
            "ADVERSITY|Prosperity|Hardship|Misfortune|Trouble", "CANDOUR|Deceit|Frankness|Honesty|Openness", "LETHARGIC|Energetic|Sluggish|Drowsy|Inactive",
            "AUGMENT|Diminish|Enlarge|Amplify|Boost", "PRUDENT|Reckless|Wise|Careful|Sensible", "EMANCIPATE|Enslave|Liberate|Release|Free",
            "INDIGENOUS|Foreign|Native|Local|Original", "OBSCURE|Prominent|Vague|Hidden|Unclear", "COMPLACENT|Concerned|Smug|Satisfied|Content",
            "VERBOSE|Concise|Wordy|Lengthy|Rambling", "DILIGENT|Negligent|Industrious|Busy|Earnest", "ALLEVIATE|Aggravate|Ease|Relieve|Soothe");

    static Seed antonyms(String key, Difficulty d, Random r) {
        String[] e = pick(r, level(d, ANT_EASY, ANT_MEDIUM, ANT_HARD)).split("\\|");
        return choice(key, S, "Antonyms", d, "Choose the word most nearly opposite in meaning to " + e[0] + ".", null, e[1], List.of(e[2], e[3], e[4]),
                e[1] + " is the opposite of " + e[0] + "; " + e[2] + " means nearly the same as " + e[0] + ".");
    }

    // ---------------- Sentence completion ----------------

    private static final List<String> COMP_EASY = List.of(
            "She has been working here ___ 2022.|since|for|from|by", "The meeting is ___ Monday.|on|in|at|by",
            "He is good ___ mathematics.|at|in|on|with", "I have lived in Pune ___ five years.|for|since|from|during",
            "They ___ football every Sunday.|play|plays|playing|played to", "The sun ___ in the east.|rises|rise|rising|rose up",
            "This is ___ honest man.|an|a|the|some", "Neither of the answers ___ correct.|is|are|were|be",
            "She is taller ___ her brother.|than|then|from|to", "We reached the station ___ the train left.|before|until|unless|although",
            "The book is ___ the table.|on|at|into|onto it", "He was tired, ___ he kept working.|but|so|because|or",
            "Please switch ___ the lights when you leave.|off|out|down|away", "There are ___ apples in the basket.|a few|a little|much|any one",
            "My father ___ to work by bus.|goes|go|going|gone", "I am looking forward ___ you.|to meeting|to meet|meeting|for meeting",
            "Each of the boys ___ a bicycle.|has|have|having|are having", "It is raining, ___ take an umbrella.|so|but|because|although");
    private static final List<String> COMP_MEDIUM = List.of(
            "If I ___ you, I would accept the offer.|were|am|was being|will be", "By the time we arrived, the film ___.|had started|has started|starts|was start",
            "He insisted ___ paying the bill.|on|for|to|at", "The manager, along with his team, ___ attending the summit.|is|are|were|have been",
            "She prefers tea ___ coffee.|to|than|over than|from", "Hardly ___ the room when the lights went out.|had he entered|he had entered|did he entered|he entered",
            "The project was delayed ___ heavy rain.|owing to|despite|in spite|although", "We need to ___ the problem before the release.|address|addressing|addressed to|be address",
            "He apologised ___ being late.|for|of|about to|on", "The data ___ collected over three months.|was|were being|have|has been to",
            "Unless you practise, you ___ improve.|will not|would not have|did not|had not", "She is one of the best players who ___ ever played here.|have|has|is|was",
            "The committee ___ divided in its opinion.|was|were|are being|have been", "No sooner had the bell rung ___ the students left.|than|then|when|as",
            "I wish I ___ harder for the exam.|had studied|studied|have studied|would study", "The report must be submitted ___ Friday.|by|until|till|since",
            "He was accused ___ stealing the files.|of|for|with|about", "Despite ___ tired, she finished the race.|being|she was|been|to be");
    private static final List<String> COMP_HARD = List.of(
            "The evidence was so ___ that the jury reached a verdict within minutes.|compelling|ambiguous|tentative|scant",
            "Her ___ remarks offended everyone at the meeting.|tactless|tactful|diplomatic|cordial",
            "The new policy is meant to ___ the effects of inflation on low incomes.|mitigate|exacerbate|celebrate|ignore",
            "Far from being ___, the plan was carefully thought through.|haphazard|methodical|deliberate|systematic",
            "The scientist's theory, once ___, is now widely accepted.|ridiculed|praised|proven|adopted",
            "He is so ___ that he never spends money on anything but essentials.|parsimonious|lavish|generous|prodigal",
            "The speaker's argument was ___: every point followed logically from the last.|cogent|incoherent|rambling|flawed",
            "The CEO's ___ decision to cut prices surprised even her own team.|abrupt|gradual|expected|planned",
            "Because the instructions were ___, nobody knew what to do.|ambiguous|explicit|lucid|precise",
            "Rather than ___ the critics, the author used their feedback to improve the book.|resent|thank|consult|cite",
            "Years of drought have ___ the region's water reserves.|depleted|replenished|enriched|doubled",
            "The startup's growth was ___, doubling every quarter.|exponential|stagnant|sluggish|declining",
            "She remained ___ even under intense pressure.|composed|flustered|agitated|frantic",
            "The ___ of the evidence made it impossible to convict him.|paucity|abundance|clarity|strength",
            "His promotion was the ___ of years of hard work.|culmination|beginning|cessation|negation",
            "The two accounts of the accident were so ___ that the police suspected one was false.|contradictory|similar|consistent|identical",
            "Critics called the film ___, saying it copied older classics.|derivative|original|innovative|groundbreaking",
            "The professor's lectures were ___; students struggled to stay awake.|soporific|stimulating|riveting|lively");

    static Seed completion(String key, Difficulty d, Random r) {
        String[] e = pick(r, level(d, COMP_EASY, COMP_MEDIUM, COMP_HARD)).split("\\|");
        return choice(key, S, "Sentence completion", d, "Fill in the blank: " + e[0], null, e[1], List.of(e[2], e[3], e[4]),
                "\"" + e[0].replace("___", e[1]) + "\" is correct.");
    }

    // ---------------- Error spotting ----------------

    private static final List<String> ERR_EASY = List.of(
            "Each of the students|have submitted|the assignment.|B|'Each' is singular, so it takes 'has submitted'.",
            "She do not|like to travel|by bus.|A|With 'she', use 'does not'.",
            "I have seen|him yesterday|at the market.|B|With a past time like 'yesterday', use the simple past: 'I saw him'.",
            "He is|a honest|employee.|B|'Honest' begins with a vowel sound: 'an honest'.",
            "The children|is playing|in the park.|B|'Children' is plural: 'are playing'.",
            "My brother and I|goes to the same|school.|B|A plural subject takes 'go'.",
            "She is|more taller|than her sister.|B|'Taller' is already comparative; drop 'more'.",
            "We discussed|about the plan|for an hour.|B|'Discuss' takes no 'about'.",
            "The team|has won|the final match.|N|The sentence is correct.",
            "There is|many books|on the shelf.|A|'Many books' is plural: 'There are'.",
            "He have|finished|his homework.|A|With 'he', use 'has'.",
            "I am|knowing the answer|to this question.|B|'Know' is not used in the continuous: 'I know the answer'.",
            "One of my friends|live|in Chennai.|B|The subject is 'one': 'lives'.",
            "She returned back|from the office|at six.|A|'Returned' already means came back; drop 'back'.",
            "The news|are good|for everyone.|B|'News' is singular: 'is good'.",
            "We will meet|when he will come|tomorrow.|B|After 'when' for the future, use the present: 'when he comes'.",
            "Everyone|must bring|their own lunch.|N|The sentence is acceptable in modern English.",
            "He did not|went|to school today.|B|After 'did not', use the base form: 'go'.");
    private static final List<String> ERR_MEDIUM = List.of(
            "Neither the manager|nor the clerks|was present.|C|With 'neither … nor', the verb agrees with the nearer subject 'clerks': 'were'.",
            "Hardly had I reached|the station|than the train left.|C|'Hardly' pairs with 'when', not 'than'.",
            "The furniture|in these rooms|are very old.|C|'Furniture' is uncountable: 'is very old'.",
            "He is one of those men|who always|keeps their promises.|C|'Who' refers to 'men' (plural): 'keep'.",
            "Unless you do not work hard,|you will not|pass the exam.|A|'Unless' already means 'if not'; drop 'do not'.",
            "She has been living|in Mumbai|since five years.|C|For a length of time use 'for five years'.",
            "The number of applicants|have increased|this year.|B|'The number of' is singular: 'has increased'.",
            "If I would have known,|I would have|helped you.|A|Use the past perfect in the if-clause: 'If I had known'.",
            "Between you and I,|the plan|will not work.|A|After a preposition use 'me': 'between you and me'.",
            "The police|has arrested|the thief.|B|'Police' takes a plural verb: 'have arrested'.",
            "I prefer|coffee|than tea.|C|'Prefer' takes 'to': 'prefer coffee to tea'.",
            "Scarcely had we started|when it|began to rain.|N|The sentence is correct.",
            "Each boy and each girl|were given|a prize.|B|'Each … and each …' takes a singular verb: 'was given'.",
            "He as well as his friends|are going|to the fair.|B|With 'as well as', the verb agrees with 'he': 'is going'.",
            "No sooner did he arrive|when the|meeting began.|B|'No sooner' pairs with 'than'.",
            "The scenery|of Kashmir|are beautiful.|C|'Scenery' is uncountable: 'is beautiful'.",
            "She is|senior than me|by two years.|B|'Senior' takes 'to': 'senior to me'.",
            "Despite of the rain,|the match|continued.|A|Use 'despite' or 'in spite of', not 'despite of'.");
    private static final List<String> ERR_HARD = List.of(
            "Not only he speaks|English but also|French fluently.|A|After 'not only' at the start, invert: 'Not only does he speak'.",
            "The reason he failed|is because|he did not prepare.|B|Use 'the reason … is that', not 'is because'.",
            "Having finished the work,|the lights were|switched off.|B|The opening phrase needs a person as subject: 'he switched off the lights'.",
            "Ten kilometres|are a long distance|to walk.|B|A distance taken as one amount is singular: 'is a long distance'.",
            "The committee have|submitted its|report.|A|Use one agreement throughout: 'The committee has submitted its report'.",
            "Being a rainy day,|we stayed|at home.|A|The opening phrase has no subject of its own: 'It being a rainy day' or 'As it was a rainy day'.",
            "He is the most|intelligent of|all the other students.|C|With the superlative, drop 'other': 'of all the students'.",
            "Many a student|have failed|this test.|B|'Many a' takes a singular verb: 'has failed'.",
            "Please let me know|whether you will come|or not to the party.|C|'Or not' belongs after 'whether': 'whether or not you will come'.",
            "The cattle|is grazing|in the field.|B|'Cattle' is always plural: 'are grazing'.",
            "Little knowledge|is a dangerous|thing.|A|The proverb is 'A little knowledge'; 'little' alone means almost none.",
            "Who did you|give the book|to?|N|The sentence is acceptable in everyday English.",
            "Lest you should forget,|I will|remind you again.|C|'Remind again' repeats the idea of 're-'; say 'remind you'.",
            "The data shows|that sales|have doubled.|N|'Data' with a singular verb is standard in business English.",
            "She is|the cleverest|of the two sisters.|B|For two, use the comparative: 'the cleverer'.",
            "On reaching the office,|my phone|was missing.|A|The phrase has no person to reach the office: 'On reaching the office, I found my phone missing'.",
            "The professor|along with his students|were invited.|C|The subject is 'the professor': 'was invited'.",
            "It is high time|we leave|for the airport.|B|'It is high time' takes the past tense: 'we left'.");

    static Seed errors(String key, Difficulty d, Random r) {
        String[] e = pick(r, level(d, ERR_EASY, ERR_MEDIUM, ERR_HARD)).split("\\|");
        String answer = switch (e[3]) {
            case "A" -> "(A) " + e[0];
            case "B" -> "(B) " + e[1];
            case "C" -> "(C) " + e[2];
            default -> "(D) No error";
        };
        Seed s = choice(key, S, "Error spotting", d, "Which part of the sentence has an error? \"" + e[0] + " " + e[1] + " " + e[2] + "\"", null, answer,
                List.of("(A) " + e[0], "(B) " + e[1], "(C) " + e[2], "(D) No error"), e[4]);
        // Keep A–D in order: easier to read than shuffled labels.
        List<String> ordered = List.of("(A) " + e[0], "(B) " + e[1], "(C) " + e[2], "(D) No error");
        return new Seed(s.key(), s.section(), s.topic(), s.difficulty(), s.kind(), s.prompt(), null, ordered, null,
                List.of(ordered.indexOf(answer)), List.of(), s.explanation());
    }

    // ---------------- Reading comprehension ----------------

    /** A passage and its questions; each question is "question|answer|distractor|distractor|distractor". */
    private record Passage(String text, List<String> questions) {}

    private static final List<Passage> RC_EASY = List.of(
            new Passage("Riya joined a software company as a trainee. In her first month she attended classes every morning and worked on small tasks "
                    + "in the afternoon. Her mentor, Arun, reviewed her code every Friday. By the end of the month Riya had fixed twelve bugs and "
                    + "written her first feature, a search box for the help pages.", List.of(
                            "When did Riya attend classes?|Every morning|Every afternoon|Every Friday|Every evening",
                            "Who reviewed Riya's code?|Her mentor, Arun|Her manager|Another trainee|A customer",
                            "How many bugs had Riya fixed by the end of the month?|Twelve|Ten|Twenty|Two",
                            "What was Riya's first feature?|A search box for the help pages|A login page|A payment form|A new logo",
                            "What is the passage mainly about?|Riya's first month at work|How to fix bugs|Arun's career|Choosing a company")),
            new Passage("The city library opened a new reading room last year. It is open from 8 a.m. to 8 p.m. on weekdays and from 10 a.m. to "
                    + "4 p.m. on Saturdays. It stays closed on Sundays. Members can borrow up to four books at a time for two weeks. Students get "
                    + "free membership if they show a college ID.", List.of(
                            "When does the reading room close on weekdays?|8 p.m.|4 p.m.|6 p.m.|10 p.m.",
                            "On which day is the reading room closed?|Sunday|Saturday|Monday|Friday",
                            "How many books can a member borrow at a time?|Four|Two|Six|Eight",
                            "For how long can books be borrowed?|Two weeks|One week|One month|Four days",
                            "What do students need for free membership?|A college ID|A fee|A letter from a teacher|An address proof")),
            new Passage("Bamboo is one of the fastest-growing plants in the world. Some kinds grow almost a metre in a single day. Because it grows "
                    + "back quickly after being cut, bamboo is used to make furniture, floors, paper and even bicycles. It also holds soil in place "
                    + "and helps prevent landslides on hills.", List.of(
                            "How much can some bamboo grow in a day?|Almost a metre|A few centimetres|Ten metres|Half a kilometre",
                            "Why is bamboo good for making products?|It grows back quickly after being cut|It is very heavy|It never breaks|It is rare",
                            "Which of these is NOT mentioned as made from bamboo?|Glass|Furniture|Paper|Bicycles",
                            "How does bamboo help on hills?|It holds soil and helps prevent landslides|It attracts rain|It keeps animals away|It makes roads",
                            "Which title suits the passage best?|Bamboo: a fast-growing, useful plant|How to build a bicycle|Landslides in India|Paper making")),
            new Passage("A small bakery in Mysuru started taking orders on a messaging app during the lockdown. The owner, Mr Rao, posted photos of "
                    + "fresh bread every morning, and customers replied with their orders. Within three months, online orders made up half of his "
                    + "sales. He later hired two delivery riders and kept the service running even after the shop reopened.", List.of(
                            "How did customers place orders?|By replying on a messaging app|By phone calls only|At the counter|Through a website",
                            "What did Mr Rao post every morning?|Photos of fresh bread|Price lists|Discount codes|Delivery times",
                            "What share of sales came from online orders after three months?|Half|A quarter|All|A tenth",
                            "Whom did Mr Rao hire later?|Two delivery riders|Two bakers|A designer|A manager",
                            "What happened after the shop reopened?|The online service continued|The online service stopped|The shop closed|Prices doubled")));
    private static final List<Passage> RC_MEDIUM = List.of(
            new Passage("Solar power has become far cheaper over the last decade. The cost of solar panels fell by almost 90 percent, mainly because "
                    + "factories learnt to make them at a much larger scale and with better materials. However, the sun does not shine at night, so "
                    + "storing energy remains a challenge. Battery prices are falling too, but more slowly, and many grids still depend on coal or gas "
                    + "after sunset.", List.of(
                            "According to the passage, why has solar power become cheaper?|Panels are made at larger scale with better materials|Governments pay for all panels|The sun has become stronger|Coal has become costlier",
                            "What is the main challenge mentioned?|Storing energy for the night|Finding sunlight|Making panels|Training engineers",
                            "What does the passage say about batteries?|Their prices are falling, but more slowly|They are free|They have become costlier|They are not needed",
                            "The word 'challenge' in the passage is closest in meaning to|difficulty|competition|invitation|reward",
                            "Which can be inferred?|Solar alone cannot yet meet all needs after sunset|Coal plants will close next year|Solar panels do not work in winter|Batteries are cheaper than panels")),
            new Passage("Many companies now let employees work from home for part of the week. Supporters say this saves travel time and lets people "
                    + "focus without office noise. Critics worry that new employees learn more slowly without colleagues around them, and that teams "
                    + "lose the casual conversations that spark ideas. Most firms have settled on a hybrid model, asking staff to come in two or three "
                    + "days a week.", List.of(
                            "What do supporters of working from home say?|It saves travel time and helps focus|It is cheaper for companies|It improves teamwork|It helps new staff learn",
                            "What worry do critics have about new employees?|They learn more slowly without colleagues nearby|They work too many hours|They change jobs often|They dislike offices",
                            "What model have most firms chosen?|Hybrid, with two or three office days|Fully remote|Fully in office|Four-day weeks",
                            "The word 'spark' in the passage means|start|end|burn|hide",
                            "The author's tone is best described as|balanced|angry|humorous|alarmed")),
            new Passage("Plastic waste is a serious problem for oceans. Every year millions of tonnes of plastic reach the sea, where it breaks into tiny "
                    + "pieces called microplastics. Fish and birds swallow these pieces, and they can enter the human food chain. Some countries have "
                    + "banned single-use plastic bags, and a few companies are testing packaging made from seaweed, which breaks down naturally within "
                    + "weeks.", List.of(
                            "What are microplastics?|Tiny pieces that plastic breaks into|A new kind of plastic|Sea plants|Plastic factories",
                            "How can microplastics reach humans?|Through the food chain|Through the air only|Through mobile phones|They cannot",
                            "What step have some countries taken?|Banned single-use plastic bags|Banned fishing|Closed beaches|Stopped making glass",
                            "Why is seaweed packaging promising?|It breaks down naturally within weeks|It is stronger than steel|It is cheaper than paper|It can be eaten by birds",
                            "What is the passage mainly about?|Plastic pollution in oceans and some responses|How to catch fish|Seaweed farming|Recycling paper")),
            new Passage("When Priya's team missed two release deadlines, her manager did not blame anyone. Instead, he asked the team to list what had "
                    + "slowed them down. The list showed that half their time went into fixing problems found late in testing. The team began writing "
                    + "automated tests along with the code, and the next three releases went out on time.", List.of(
                            "How did the manager react to the missed deadlines?|He asked the team what had slowed them down|He blamed Priya|He hired more people|He cancelled the project",
                            "Where did half the team's time go?|Fixing problems found late in testing|Meetings|Training|Writing documents",
                            "What change did the team make?|Wrote automated tests along with the code|Worked weekends|Dropped testing|Changed managers",
                            "What was the result?|The next three releases were on time|Two more deadlines were missed|The team was split|Customers complained",
                            "What lesson does the passage suggest?|Finding the cause of a problem works better than blaming|Deadlines do not matter|Testing wastes time|Managers should do the coding")));
    private static final List<Passage> RC_HARD = List.of(
            new Passage("Economists have long noted a paradox: as technology makes a resource more efficient to use, total consumption of that resource "
                    + "often rises rather than falls. When steam engines became more efficient in the nineteenth century, Britain did not burn less "
                    + "coal; cheaper power made new uses profitable, and demand soared. Some analysts warn that the same may happen with energy-efficient "
                    + "data centres, while others argue that strict limits on emissions can break the pattern.", List.of(
                            "What paradox does the passage describe?|Greater efficiency can increase total consumption|Efficiency always cuts consumption|Technology makes resources scarce|Coal became expensive",
                            "Why did coal use rise in Britain?|Cheaper power made new uses profitable|Engines became less efficient|Coal was imported|Laws required it",
                            "How do some analysts think the pattern can be broken?|Strict limits on emissions|Building more data centres|Using more coal|Ignoring efficiency",
                            "The word 'soared' most nearly means|rose sharply|fell slowly|stayed level|disappeared",
                            "Which statement would the author most likely agree with?|Efficiency alone may not reduce resource use|Data centres use no energy|Steam engines were inefficient|Economists agree on everything")),
            new Passage("Critics of standardised tests argue that they reward memorisation and favour students who can afford coaching. Defenders reply "
                    + "that, without a common test, admissions would rely even more on interviews and recommendations, which are easier for well-connected "
                    + "families to influence. Both sides agree on one point: a single score should never be the only measure of a student's ability.",
                    List.of("What do critics say standardised tests reward?|Memorisation|Creativity|Teamwork|Sports",
                            "What is the defenders' main argument?|Without tests, admissions would depend more on easily influenced methods|Tests are always fair|Coaching should be banned|Interviews are better",
                            "On what point do both sides agree?|A single score should not be the only measure|Tests should be abolished|Coaching is useless|Interviews are unfair",
                            "The word 'influence' in the passage means|affect|ignore|measure|publish",
                            "The passage is best described as|a summary of two views on a debate|an advertisement for coaching|a personal story|a list of test dates")),
            new Passage("The octopus has a remarkable nervous system: about two-thirds of its neurons are in its arms rather than its brain. Each arm can "
                    + "taste, touch and even make simple decisions on its own, such as pulling food towards the mouth. Researchers studying this "
                    + "arrangement hope it will inspire robots whose limbs handle routine movements locally, leaving a central processor free for "
                    + "planning.", List.of(
                            "Where are most of an octopus's neurons?|In its arms|In its brain|In its eyes|In its skin",
                            "What can an octopus arm do on its own?|Taste, touch and make simple decisions|See colours|Breathe|Swim faster than the body",
                            "How might this inspire robots?|Limbs could handle routine movements locally|Robots could taste food|Robots could swim|Robots would need no processor",
                            "The word 'routine' in the passage means|regular and ordinary|dangerous|rare|complicated",
                            "Which title fits the passage best?|Thinking arms: what octopuses teach robot designers|Octopus recipes|The history of robots|Ocean pollution")),
            new Passage("In 1854 a cholera outbreak killed hundreds of people in London's Soho district. Most doctors believed the disease spread through "
                    + "bad air. John Snow, a physician, mapped the deaths and found they clustered around one public water pump on Broad Street. When "
                    + "the pump's handle was removed, new cases fell. Snow's map is now seen as an early example of using data to test a theory.", List.of(
                            "What did most doctors believe about cholera at the time?|It spread through bad air|It spread through water|It came from animals|It was not contagious",
                            "What did John Snow's map show?|Deaths clustered around one water pump|Deaths were spread evenly|Deaths were near hospitals|Only children died",
                            "What happened after the pump handle was removed?|New cases fell|New cases rose|The pump broke|Doctors protested",
                            "The word 'clustered' most nearly means|gathered closely|spread out|disappeared|doubled",
                            "Why is Snow's work important today?|It is an early example of using data to test a theory|It discovered vaccines|It invented maps|It ended all diseases")));

    static Seed reading(String key, Difficulty d, Random r) {
        List<Passage> passages = d == Difficulty.EASY ? RC_EASY : d == Difficulty.MEDIUM ? RC_MEDIUM : RC_HARD;
        Passage p = pick(r, passages);
        String[] q = pick(r, p.questions()).split("\\|");
        return choice(key, S, "Reading comprehension", d, "Read the passage and answer the question.\n\n" + p.text() + "\n\n" + q[0], null, q[1],
                List.of(q[2], q[3], q[4]), "The passage supports \"" + q[1] + "\".");
    }
}
