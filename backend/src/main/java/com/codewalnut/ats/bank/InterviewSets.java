package com.codewalnut.ats.bank;

import com.codewalnut.ats.client.InterviewAgent.DraftQuestion;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written AI interview question sets (ADR-0030), used instead of a drafted set when they fit the
 * opening. Production scenarios rather than definitions; each spoken question carries what a strong
 * answer covers, for the reviewers and the AI review (never shown to candidates). Marked answers were
 * checked by hand.
 */
public final class InterviewSets {

    private InterviewSets() {}

    /** Senior / lead Java backend: coding depth, architecture, production experience and leading a team. */
    public static List<DraftQuestion> seniorJavaLead() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(video("INTRO", "In one minute: your current role, the size of the team you lead, and the system you own end to end.", 60,
                "Clear scope (owner vs contributor); team size; real numbers such as traffic or users."));

        q.add(video("EXPERIENCE", "Describe the most critical production system you've been accountable for: traffic, data volume, "
                + "uptime target, and exactly what you designed or decided yourself.", 120,
                "Concrete numbers; their own decisions rather than 'we'; trade-offs they made and why."));
        q.add(video("EXPERIENCE", "Tell us about a production incident you led. How did you find the cause, what was the fix, "
                + "and what did you change so it couldn't happen again?", 150,
                "A clear timeline; real tools (logs, metrics, thread or heap dumps); root cause vs symptom; follow-ups such as "
                        + "alerts, tests, a runbook or a design change."));

        q.add(blank("In double-checked locking, the instance field must be declared ____ so another thread never sees a "
                + "half-built object.", "volatile"));
        q.add(blank("@Transactional does nothing when the method is called from inside the same class, because the call "
                + "bypasses Spring's ____.", "proxy", "proxies", "AOP proxy"));
        q.add(blank("To stop two transactions silently overwriting the same JPA row, add a field annotated @____.", "Version"));
        q.add(blank("In a thread pool, a ThreadLocal leaks unless you call ____() in a finally block.", "remove"));
        q.add(blank("Since Java 8, a HashMap bucket with many collisions is converted from a linked list into a ____.",
                "tree", "red-black tree", "balanced tree"));

        q.add(video("PROJECTS", "Pick a project you're proud of. Describe it in words: the services, data stores and messaging. "
                + "What was the hardest technical problem, and what did you personally build?", 180,
                "A coherent architecture; a genuinely hard problem; their own contribution; numbers and outcomes."));
        q.add(video("PROJECTS", "What is a technical decision on that project you would reverse today, and why?", 120,
                "Honest reflection; what they learned; a concrete better alternative. Saying 'nothing' is a red flag for a lead."));

        q.add(video("ARCHITECTURE", "Design the payment step of an order service handling 2,000 requests per second. The payment "
                + "provider times out on 1% of calls. Cover idempotency, retries, the data model, and what happens when the "
                + "provider answers after you have given up.", 180,
                "Idempotency key with a unique constraint; a payment state machine (pending, confirmed, failed); query the "
                        + "provider's status before retrying; bounded retries with backoff; outbox or events; reconcile late callbacks."));
        q.add(video("ARCHITECTURE", "You are splitting a Spring Boot monolith into services. What do you split first, how do you "
                + "handle data both sides need during the migration, and what would make you stop?", 180,
                "Strangler pattern; split by business capability; one owner per piece of data; change-data-capture or events "
                        + "during migration; not splitting just for the sake of it."));
        q.add(choice("ARCHITECTURE", "An order service must save the order in MySQL and publish an event to Kafka. Neither may be "
                + "lost, even if the service crashes between the two. What do you use?",
                List.of("A transactional outbox: write the event to a table in the same transaction, then publish it",
                        "Publish to Kafka first, then commit the database transaction",
                        "One @Transactional method around both the save and the Kafka send",
                        "Commit the database, then publish, and log an error if publishing fails"), 0));
        q.add(choice("ARCHITECTURE", "Service A calls service B. B goes down; A keeps retrying and its threads pile up until A "
                + "is down too. What fixes this?",
                List.of("Retry more times with a shorter delay",
                        "Timeouts plus a circuit breaker with limited retries and a fallback",
                        "A bigger thread pool in A",
                        "A longer timeout on calls to B"), 1));
        q.add(choice("ARCHITECTURE", "Redis has a 95% hit rate, yet the database hits 100% CPU every hour on the hour. What is "
                + "the most likely cause and fix?",
                List.of("Redis is too small; add memory",
                        "Missing database indexes; add them",
                        "Many keys expire at the same moment (same TTL), causing a stampede; add random jitter to TTLs and let one "
                                + "request reload a key while others wait",
                        "Switch from Redis to Memcached"), 2));

        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
                for (Integer i : list) {
                    if (i == 2) list.remove(i);
                }
                System.out.println(list);""",
                List.of("[1, 3, 4]", "[1, 2, 3, 4]", "It throws ConcurrentModificationException", "[1, 3]"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                var f = CompletableFuture.supplyAsync(() -> { throw new IllegalStateException(); });
                System.out.println(f.thenApply(s -> s + "!")
                        .exceptionally(e -> "recovered: " + e.getClass().getSimpleName())
                        .join());""",
                List.of("recovered: IllegalStateException", "recovered: CompletionException", "It throws an exception", "null!"), 1));
        q.add(choiceWithCode("TECHNICAL", "A controller calls placeOrder(order) and audit.log throws a RuntimeException. What "
                + "happens to the order?", """
                @Service
                class OrderService {
                    public void placeOrder(Order o) { save(o); }

                    @Transactional
                    public void save(Order o) {
                        repo.save(o);
                        audit.log(o); // throws RuntimeException
                    }
                }""",
                List.of("It is rolled back, because save() is @Transactional",
                        "It stays saved: the call to save() bypasses the proxy, so there is no surrounding transaction and repo.save() committed on its own",
                        "It is never saved",
                        "The application fails at startup"), 1));
        q.add(choice("TECHNICAL", "Listing 200 orders and showing order.getCustomer().getName() runs 201 SQL queries. What is the "
                + "best fix?",
                List.of("Make the customer mapping FetchType.EAGER",
                        "Turn on the Hibernate second-level cache",
                        "Fetch the customers in the same query with JOIN FETCH or an @EntityGraph",
                        "Increase the connection pool size"), 2));
        q.add(choice("TECHNICAL", "An account balance is updated by three instances of the same service and some updates are "
                + "lost. What prevents it?",
                List.of("Mark the update method synchronized",
                        "Optimistic locking with @Version and a retry, or SELECT ... FOR UPDATE",
                        "@Transactional on the update method alone",
                        "Declare the balance field volatile"), 1));
        q.add(videoWithCode("TECHNICAL", "You are reviewing this pull request as the lead. What is wrong, and what would you change?", """
                @Service
                public class ReportService {
                    private final Map<String, Report> cache = new HashMap<>();

                    @Async
                    public CompletableFuture<Report> build(String id) {
                        return CompletableFuture.completedFuture(cache.computeIfAbsent(id, this::load));
                    }
                }""", 180,
                "HashMap shared across async threads is not thread-safe (ConcurrentHashMap); an unbounded cache grows forever "
                        + "(Caffeine with size and expiry); a slow load inside computeIfAbsent blocks others on that key; @Async is "
                        + "ignored on self-calls; configure the async thread pool explicitly; handle exceptions from load()."));
        q.add(video("TECHNICAL", "Response time is 20 ms normally and 8 seconds under load. There are no errors in the logs. "
                + "Where do you start, and with which tools?", 180,
                "Waiting rather than failing: threads waiting on the database connection pool, a thread pool queue, or locks; "
                        + "thread dumps (jstack, JFR), connection-pool metrics, GC logs, the slow query log; look at p99, not averages."));
        q.add(video("TECHNICAL", "A service ran fine for 30 days, then crashed with OutOfMemoryError. Nobody changed the code. "
                + "How do you find the cause?", 150,
                "-XX:+HeapDumpOnOutOfMemoryError and analysing the dump (Eclipse MAT); usual causes: an unbounded cache, "
                        + "ThreadLocals, listeners never removed, a slow leak seen in the heap trend; check whether it is heap, "
                        + "metaspace or direct memory."));

        q.add(video("SOFT_SKILLS", "A strong engineer on your team ships code that works but that nobody else can maintain. "
                + "How do you handle it?", 120,
                "A private conversation with specific examples; agreed standards; pairing; follow-up. Not going around the "
                        + "person or escalating first."));
        q.add(video("SOFT_SKILLS", "Product wants a feature by Friday. You believe it creates a production risk. Walk us "
                + "through the conversation and how it ends.", 120,
                "Puts the risk in concrete terms; offers options (smaller scope, feature flag, a later date); makes the "
                        + "trade-off visible; commits to the decision once it is made."));
        q.add(video("SOFT_SKILLS", "How do you run code reviews and design reviews for a team of six to eight engineers?", 120,
                "Written design docs; checklists; review turnaround; mentoring juniors through reviews; automating style checks."));

        q.add(written("What is your notice period, and the earliest date you could join?"));
        q.add(written("What are your current and expected salary (CTC)?"));
        q.add(written("Where are you based, and are you open to relocating or working from the office?"));
        q.add(video("READINESS", "Why this role, and what would you want to have achieved in your first 90 days?", 60,
                "Specific interest in the role and company; realistic, concrete 90-day goals (learn the system, ship, improve something)."));
        return q;
    }

    private static DraftQuestion video(String phase, String prompt, int seconds, String good) {
        return new DraftQuestion(phase, "VIDEO", prompt, "", List.of(), -1, List.of(), seconds, good);
    }

    private static DraftQuestion videoWithCode(String phase, String prompt, String code, int seconds, String good) {
        return new DraftQuestion(phase, "VIDEO", prompt, code, List.of(), -1, List.of(), seconds, good);
    }

    private static DraftQuestion written(String prompt) {
        return new DraftQuestion("READINESS", "WRITTEN", prompt, "", List.of(), -1, List.of(), 0, "");
    }

    private static DraftQuestion blank(String prompt, String... accepted) {
        return new DraftQuestion("SKILLS", "FILL_BLANK", prompt, "", List.of(), -1, List.of(accepted), 0, "");
    }

    private static DraftQuestion choice(String phase, String prompt, List<String> options, int correct) {
        return new DraftQuestion(phase, "MULTIPLE_CHOICE", prompt, "", options, correct, List.of(), 0, "");
    }

    private static DraftQuestion choiceWithCode(String phase, String prompt, String code, List<String> options, int correct) {
        return new DraftQuestion(phase, "MULTIPLE_CHOICE", prompt, code, options, correct, List.of(), 0, "");
    }
}
