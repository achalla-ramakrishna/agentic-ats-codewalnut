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

    /**
     * Senior Java developer, hands-on individual contributor (CodeWalnut's opening for a global travel
     * technology client): Java 17, Spring Boot, REST/gRPC, MongoDB, Redis/Caffeine caching, Spring Security
     * with JWT, JUnit 5/Mockito/Spock, CI/CD with SonarQube; airline shopping and booking flows.
     */
    public static List<DraftQuestion> seniorJava() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(video("INTRO", "In one minute: your current role, your years with Java, and the service you own end to end today.", 60,
                "Clear scope (owner vs contributor); years with Java and Spring Boot; the service, its users and its traffic."));

        q.add(video("EXPERIENCE", "Describe a high-throughput Java microservice you owned end to end: its traffic, its data store, "
                + "how it was built, tested, deployed and monitored, and what you designed yourself.", 150,
                "Real numbers (requests per second, data size, latency); the whole path from design to CI/CD to production "
                        + "monitoring; their own decisions rather than 'we'; trade-offs they made."));
        q.add(video("EXPERIENCE", "Tell us about a production issue you troubleshot and fixed. How did you find the cause, what "
                + "was the fix, and what did you change so it couldn't happen again?", 150,
                "A clear timeline; real tools (logs, metrics, traces, thread or heap dumps, database profiler); root cause vs "
                        + "symptom; follow-ups such as alerts, tests or a design change; owning it rather than blaming."));

        q.add(video("SKILLS", "How would you secure a set of Spring Boot microservices with JWT? Cover where tokens are issued "
                + "and checked, what goes in them, expiry and refresh, and how one service calls another.", 150,
                "Stateless validation of the signature (ideally asymmetric keys / JWKS) in a resource-server filter chain; short "
                        + "expiry with refresh tokens; claims and roles mapped to authorities; no secrets in the payload; "
                        + "service-to-service via client credentials or a propagated token; revocation and clock skew."));
        q.add(blank("In MongoDB, to increase a counter field atomically in one update, without reading the document first, use "
                + "the ____ update operator.", "$inc", "inc"));
        q.add(blank("In Spring, the annotation that caches a method's result is @____.", "Cacheable"));
        q.add(blank("Calling an @Cacheable or @Transactional method from another method of the same class skips the caching or "
                + "transaction, because the call bypasses Spring's ____.", "proxy", "proxies", "AOP proxy"));
        q.add(blank("A Java 17 ____ class uses a permits clause to list exactly which classes may extend it.", "sealed"));
        q.add(blank("In Mockito, to check that a method was called exactly twice, write verify(mock, ____(2)).", "times"));
        q.add(blank("In a thread pool, a ThreadLocal leaks unless you call ____() in a finally block.", "remove"));

        q.add(video("PROJECTS", "Pick a project you're proud of. Describe it in words: the services, data stores, caches and "
                + "messaging. What was the hardest technical problem, and what did you personally build?", 180,
                "A coherent architecture; a genuinely hard problem; their own contribution; numbers and outcomes."));
        q.add(video("PROJECTS", "What is a technical decision on that project you would reverse today, and why?", 120,
                "Honest reflection; what they learned; a concrete better alternative. Saying 'nothing' is a warning sign at this level."));

        q.add(video("ARCHITECTURE", "Design fare search for an airline shopping service: 3,000 searches per second, prices come "
                + "from slow external airline systems (1 to 3 seconds), and fares change often. Cover caching, timeouts, "
                + "freshness, and what the customer sees if a cached fare has changed by the time they book.", 180,
                "Cache-aside in Redis keyed by route, date and passengers, with a short TTL and jitter; a small in-memory "
                        + "(Caffeine) layer for the hottest keys; parallel calls to providers with timeouts, bulkheads and "
                        + "partial results; circuit breakers; one reload per key to avoid stampedes; re-price at booking and "
                        + "show a price-change message instead of selling a stale fare."));
        q.add(video("ARCHITECTURE", "Design booking creation so a customer is never charged twice and never left paid without a "
                + "booking, when the airline system or the payment provider can time out.", 180,
                "Idempotency key with a unique index; a booking state machine (held, paid, ticketed, failed); query status "
                        + "before retrying; bounded retries with backoff; a saga with compensation (refund or release the hold); "
                        + "events via an outbox; reconciling late responses."));
        q.add(choice("ARCHITECTURE", "A booking service must save the booking in MongoDB and publish a 'booking created' event to "
                + "Kafka. Neither may be lost, even if the service crashes between the two. What do you use?",
                List.of("Publish to Kafka first, then save the booking",
                        "A transactional outbox: save the event with the booking (or read it from a change stream), then publish it",
                        "Wrap both calls in one @Transactional method",
                        "Save the booking, then publish, and log an error if publishing fails"), 1));
        q.add(choice("ARCHITECTURE", "Fare search calls an airline availability API. The API goes down; search keeps retrying and "
                + "its threads pile up until search is down too. What fixes this?",
                List.of("Retry more times with a shorter delay",
                        "A bigger thread pool for outgoing calls",
                        "Timeouts plus a circuit breaker with limited retries and a fallback such as cached or partial results",
                        "A longer timeout on calls to the airline API"), 2));
        q.add(choice("ARCHITECTURE", "The fare cache in Redis has a 95% hit rate, yet MongoDB and the airline APIs spike every hour "
                + "on the hour. What is the most likely cause and fix?",
                List.of("Redis is too small; add memory",
                        "Many keys were cached with the same TTL and expire together, causing a stampede; add random jitter to TTLs "
                                + "and let one request reload a key while others wait",
                        "MongoDB is missing an index",
                        "Switch from Redis to Memcached"), 1));
        q.add(choice("ARCHITECTURE", "Two internal services you own make a very large number of small calls to each other with "
                + "strict latency targets. What fits best?",
                List.of("gRPC with Protobuf: binary messages over HTTP/2 with generated, typed clients",
                        "REST with JSON, opening a new connection for every call",
                        "SOAP web services",
                        "Both services reading and writing the same database tables"), 0));

        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                List<String> codes = List.of("BLR", "DEL", "BOM", "DEL");
                Map<String, Long> counts = codes.stream()
                        .collect(Collectors.groupingBy(c -> c, TreeMap::new, Collectors.counting()));
                System.out.println(counts);""",
                List.of("{BLR=1, DEL=2, BOM=1}", "{BLR=1, BOM=1, DEL=2}", "[BLR, BOM, DEL]", "It throws IllegalStateException (duplicate key)"), 1));
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
        q.add(choiceWithCode("TECHNICAL", "A controller calls quote(\"BLR-DEL\") many times. How often does provider.fetch run?", """
                @Service
                class FareService {
                    public Fare quote(String route) { return lookup(route); }

                    @Cacheable("fares")
                    public Fare lookup(String route) {
                        return provider.fetch(route); // slow call to the airline
                    }
                }""",
                List.of("Once; later calls come from the cache",
                        "Every time: the call to lookup() comes from inside the class, so it bypasses the caching proxy",
                        "Never; Spring calls the cache only",
                        "The application fails at startup"), 1));
        q.add(choice("TECHNICAL", "A MongoDB query filters on airline and departureDate (exact values) and sorts by price. It is "
                + "slow on 50 million documents. Which index fits best?",
                List.of("Three separate indexes: airline, departureDate and price",
                        "A compound index { price: 1, airline: 1, departureDate: 1 }",
                        "A compound index { airline: 1, departureDate: 1, price: 1 }",
                        "A text index on airline"), 2));
        q.add(choice("TECHNICAL", "Several instances book seats by reading a flight document, subtracting from seatsLeft in Java and "
                + "saving it. Flights get oversold. What fixes it?",
                List.of("Mark the booking method synchronized",
                        "One conditional atomic update, e.g. updateOne({_id: id, seatsLeft: {$gte: n}}, {$inc: {seatsLeft: -n}}), "
                                + "and check that it modified a document",
                        "Add @Transactional to the booking method",
                        "Declare seatsLeft volatile"), 1));
        q.add(videoWithCode("TECHNICAL", "You are reviewing this pull request. What is wrong, and what would you change?", """
                @Service
                public class FareCache {
                    private final Map<String, Fare> cache = new HashMap<>();

                    @Async
                    public CompletableFuture<Fare> get(String route) {
                        return CompletableFuture.completedFuture(cache.computeIfAbsent(route, provider::fetch));
                    }
                }""", 180,
                "HashMap shared across async threads is not thread-safe (ConcurrentHashMap or Caffeine); fares change, so the "
                        + "cache needs a TTL and a size limit; each instance has its own copy, so consider Redis or a short local "
                        + "TTL; a slow fetch inside computeIfAbsent blocks others on that key; @Async is ignored on self-calls; "
                        + "configure the async pool; handle provider errors and timeouts."));
        q.add(video("TECHNICAL", "Response time is 20 ms normally and 8 seconds under load. There are no errors in the logs. "
                + "Where do you start, and with which tools?", 180,
                "Something is waiting rather than failing: thread pools, the MongoDB or HTTP connection pools, locks, slow "
                        + "queries (profiler, explain()), downstream calls; thread dumps or JFR, pool metrics, GC logs, traces; "
                        + "look at p99 latency, not the average."));
        q.add(video("TECHNICAL", "How do you test a Spring Boot service that uses MongoDB, Redis and an external airline API? "
                + "Which tests run where in the CI pipeline, and how do you keep the SonarQube quality gate green without "
                + "gaming coverage?", 150,
                "Unit tests with JUnit 5 and Mockito or Spock; slice tests (@WebMvcTest, @DataMongoTest); Testcontainers for "
                        + "MongoDB and Redis; WireMock or contract tests for the airline API; a few Playwright end-to-end tests; "
                        + "fast tests on every pull request, slower ones later; the quality gate on new code; meaningful "
                        + "assertions rather than coverage padding; fixing Sonar issues or tuning rules with a reason."));

        q.add(video("SOFT_SKILLS", "You get a one-line ticket: 'Let travel agents change the seat on a booking.' Before writing "
                + "code, what questions do you ask, and how do you break it into pieces you can ship?", 150,
                "Asks about scope (which bookings and airlines, fees, who may do it), edge cases (partial failures, a seat "
                        + "taken meanwhile), non-functional needs (latency, audit, idempotency); splits into small deliverable "
                        + "slices; confirms with the product manager; estimates."));
        q.add(video("SOFT_SKILLS", "Product wants a feature by Friday. You believe it creates a production risk. Walk us "
                + "through the conversation and how it ends.", 120,
                "Puts the risk in concrete terms; offers options (smaller scope, a feature flag, a later date); makes the "
                        + "trade-off visible; commits to the decision once made."));
        q.add(video("SOFT_SKILLS", "You disagree with a design an architect on the client's global team has proposed. How do "
                + "you handle it?", 120,
                "First understands their reasons; brings data or a quick prototype; writes up options for an asynchronous "
                        + "discussion across time zones; respects the final decision; stays constructive."));

        q.add(written("What is your notice period, and the earliest date you could join? (We prefer immediate to 60 days.)"));
        q.add(written("What are your current and expected salary (CTC)?"));
        q.add(written("This role is hybrid in Bengaluru. Where are you based, and are you happy to work from the Bengaluru office part of the week?"));
        q.add(video("READINESS", "Why this role, building airline shopping and booking systems for a global travel technology "
                + "company, and what would you want to have achieved in your first 90 days? If you have airline or travel "
                + "experience (GDS, PNR, NDC, fares, ticketing), mention it.", 90,
                "Specific interest in the role; realistic 90-day goals (learn the domain and system, ship, improve something); "
                        + "any airline or travel domain knowledge is a plus."));
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
