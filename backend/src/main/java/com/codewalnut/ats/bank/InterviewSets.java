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

    /** How many hand-written senior Java sets there are (A, B, C, D). */
    public static final int SENIOR_JAVA_SETS = 4;

    /** Set 1–4 (A–D) for the senior Java developer opening; each covers the same skills with different questions. */
    public static List<DraftQuestion> seniorJava(int set) {
        return switch (set) {
            case 2 -> seniorJavaB();
            case 3 -> seniorJavaC();
            case 4 -> seniorJavaD();
            default -> seniorJava();
        };
    }

    /** "A" for 1, "B" for 2, … */
    public static String setLetter(int set) {
        return String.valueOf((char) ('A' + Math.max(1, Math.min(SENIOR_JAVA_SETS, set)) - 1));
    }

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

    private static DraftQuestion intro() {
        return video("INTRO", "In one minute: your current role, your years with Java, and the service you own end to end today.", 60,
                "Clear scope (owner vs contributor); years with Java and Spring Boot; the service, its users and its traffic.");
    }

    private static void readinessFacts(List<DraftQuestion> q) {
        q.add(written("What is your notice period, and the earliest date you could join? (We prefer immediate to 60 days.)"));
        q.add(written("What are your current and expected salary (CTC)?"));
        q.add(written("This role is hybrid in Bengaluru. Where are you based, and are you happy to work from the Bengaluru office part of the week?"));
    }

    /** Set B: API design, seat inventory, event-driven flows, release problems. */
    static List<DraftQuestion> seniorJavaB() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Walk us through the architecture of the system you work on today: the services, data stores, "
                + "caches and messaging, and the part you own.", 150,
                "A coherent picture of how data flows; where they sit in it; real numbers; what they own vs what others own."));
        q.add(video("EXPERIENCE", "Tell us about a time a release caused a problem in production. How did you detect it, how did "
                + "you roll back or fix it, and what did you change in how the team releases?", 150,
                "Detected by monitoring or alerts rather than customers; a quick rollback or feature-flag switch-off; root "
                        + "cause; a process change (tests, canary or staged rollout, better alerts)."));
        q.add(video("SKILLS", "How would you design a REST API for searching flights and creating bookings? Cover the resources "
                + "and URLs, status codes, validation and errors, pagination, versioning and repeated requests.", 150,
                "Resource-oriented URLs (POST /bookings, a search endpoint); 201 Created with Location; 400/404/409/422 used "
                        + "correctly; one consistent error body (problem details); validation with @Valid; cursor or page "
                        + "pagination; a versioning strategy; an Idempotency-Key on booking creation; OpenAPI documentation."));
        q.add(blank("In MongoDB, to add an item to an array field only if it isn't already there, use the ____ update operator.",
                "$addToSet", "addToSet"));
        q.add(blank("In Spring, to remove an entry from a cache when the data changes, annotate the method with @____.", "CacheEvict"));
        q.add(blank("A Java 17 ____ is a compact, immutable data class that gets its constructor, accessors, equals, hashCode and "
                + "toString automatically.", "record"));
        q.add(blank("In Spring Security, the bean that defines the chain of security filters for HTTP requests is a Security____.",
                "FilterChain", "SecurityFilterChain"));
        q.add(blank("ConcurrentHashMap implements the ____ interface, the thread-safe extension of Map.", "ConcurrentMap"));
        q.add(blank("In JUnit 5, a test that runs once for each value from a source such as @ValueSource is annotated @____.",
                "ParameterizedTest"));
        q.add(video("PROJECTS", "Describe the most complex feature you delivered on your own, from a vague requirement to "
                + "production. How did you break it down, and what went wrong along the way?", 180,
                "Breaks a vague ask into concrete pieces; clarifies scope early; handles surprises; ships and follows up in production."));
        q.add(video("PROJECTS", "Which part of your current codebase would you rewrite if you had two weeks, and how would you do "
                + "it safely?", 120,
                "A specific pain with a measurable reason; tests before changing; incremental steps behind flags; no big-bang rewrite."));
        q.add(video("ARCHITECTURE", "Design seat availability for flights that thousands of agents search and book at the same "
                + "time, without overselling seats or slowing down searches.", 180,
                "The airline or inventory store is the source of truth with atomic conditional updates; short seat holds that "
                        + "expire; a separate cached read model for search that may be slightly stale; re-check at booking; "
                        + "idempotent booking; events to refresh caches."));
        q.add(video("ARCHITECTURE", "A booking flow calls five services one after another and has become slow and fragile. How "
                + "would you redesign it?", 180,
                "Find the critical path with tracing; call independent services in parallel; move non-critical steps (emails, "
                        + "loyalty) to events; timeouts, retries and circuit breakers; a saga for consistency; cache reference data."));
        q.add(choice("ARCHITECTURE", "After a booking, the email and loyalty services must be told. Loyalty is sometimes down for "
                + "a few minutes. What fits best?",
                List.of("Call both inside the booking request and fail the booking if either fails",
                        "Publish a 'booking created' event to Kafka and let each service consume it at its own pace",
                        "A job that scans all bookings every minute",
                        "Call loyalty first and email only if it succeeds"), 1));
        q.add(choice("ARCHITECTURE", "'Booking cancelled' messages from Kafka can be delivered more than once. What makes "
                + "processing them safe?",
                List.of("Turn on auto-commit of offsets",
                        "Use a single partition",
                        "Make the handler idempotent, e.g. record processed event IDs or use conditional updates",
                        "Increase the number of retries"), 2));
        q.add(choice("ARCHITECTURE", "An API's average latency is fine, but p99 latency is 4 seconds. What does this usually tell you?",
                List.of("The servers need more CPU",
                        "A small share of requests hit something slow (GC pauses, pool waits, slow downstream calls); investigate those",
                        "The network is slow for everyone",
                        "Nothing important; the average is what matters"), 1));
        q.add(choice("ARCHITECTURE", "For the number of seats shown in flight search results, what is usually acceptable?",
                List.of("Slightly stale data, as long as booking checks the real inventory",
                        "Calling the airline for every search result",
                        "Locking the flight record during every search",
                        "Caching it for a day"), 0));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                Map<String, Integer> fares = new HashMap<>();
                fares.put("BLR-DEL", 4500);
                fares.merge("BLR-DEL", 500, Integer::sum);
                fares.merge("BLR-BOM", 300, Integer::sum);
                System.out.println(fares.get("BLR-DEL") + " " + fares.get("BLR-BOM"));""",
                List.of("4500 300", "5000 300", "5000 null", "500 300"), 1));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                Optional<String> pnr = Optional.of("ABC123");
                String result = pnr.map(String::toLowerCase)
                        .filter(p -> p.startsWith("x"))
                        .orElse("none");
                System.out.println(result);""",
                List.of("abc123", "ABC123", "none", "It throws NoSuchElementException"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                String a = "PNR";
                String b = new String("PNR");
                System.out.println((a == b) + " " + a.equals(b) + " " + (a == b.intern()));""",
                List.of("true true true", "false true true", "false true false", "false false true"), 1));
        q.add(choiceWithCode("TECHNICAL", "With Spring's default settings, what happens to markCancelled when this method throws?", """
                @Transactional
                public void cancel(String id) throws Exception {
                    repo.markCancelled(id);
                    throw new Exception("refund failed");
                }""",
                List.of("It is rolled back, because the method is @Transactional",
                        "It is committed: by default Spring rolls back only on unchecked exceptions (RuntimeException and Error)",
                        "It never runs",
                        "The application fails at startup"), 1));
        q.add(choice("TECHNICAL", "You need the total booking value per airline for last month from a MongoDB bookings collection. "
                + "What do you use?",
                List.of("Load all bookings into Java and add them up",
                        "An aggregation pipeline: $match on the date range, then $group by airline with $sum",
                        "One query per airline in a loop",
                        "A text index on airline"), 1));
        q.add(choice("TECHNICAL", "What is true about the payload of a signed JWT?",
                List.of("It is encrypted, so only the server can read it",
                        "It is only Base64URL-encoded, so anyone holding the token can read it; never put secrets in it",
                        "It is hashed and cannot be read",
                        "It is compressed and unreadable without the key"), 1));
        q.add(videoWithCode("TECHNICAL", "You are reviewing this pull request. What is wrong, and what would you change?", """
                @RestController
                class BookingController {
                    @Autowired BookingRepository repo;

                    @PostMapping("/bookings")
                    public Booking create(@RequestBody Booking b) {
                        try {
                            return repo.save(b);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    }
                }""", 180,
                "Field injection instead of constructor injection; the database entity is used as request and response (use "
                        + "DTOs with @Valid); exceptions swallowed and printStackTrace instead of SLF4J logging and a proper error "
                        + "response (@ControllerAdvice); returning null gives a 200 with an empty body; 201 Created expected; no "
                        + "idempotency for retries; business logic belongs in a service; no authorisation check."));
        q.add(video("TECHNICAL", "A service's memory grows slowly for days, then it restarts with OutOfMemoryError. How do you find "
                + "the cause?", 150,
                "Heap dump on OOM (-XX:+HeapDumpOnOutOfMemoryError) and analysis in Eclipse MAT, or comparing two dumps; GC "
                        + "logs; usual causes (unbounded caches, ThreadLocals, listeners, collections that only grow); heap vs "
                        + "metaspace vs direct memory; container memory limit vs -Xmx (MaxRAMPercentage)."));
        q.add(video("TECHNICAL", "Your Jenkins or GitHub Actions pipeline takes 40 minutes and developers have started bypassing "
                + "it. How would you make it fast and trustworthy?", 150,
                "Measure where time goes; run stages in parallel; cache dependencies; split fast unit tests from slower "
                        + "integration tests; reuse Testcontainers; fix or quarantine flaky tests with an owner; fail fast; keep the "
                        + "SonarQube gate on new code."));
        q.add(video("SOFT_SKILLS", "You are asked to 'add multi-city search' with no other detail, and the product manager is away "
                + "for two days. What do you do?", 150,
                "Writes down questions and assumptions; starts on the parts that are certain or does a spike; shares a short "
                        + "written proposal; doesn't sit idle or build everything on guesses."));
        q.add(video("SOFT_SKILLS", "A teammate's pull request has a design you think will cause problems later, and the release "
                + "is tomorrow. What do you do?", 120,
                "Talks to them directly; separates must-fix from follow-up; explains with concrete examples; agrees a plan "
                        + "(small fix now, ticket for later); respectful."));
        q.add(video("SOFT_SKILLS", "Tell us about feedback you received that changed how you work.", 120,
                "A specific example; self-awareness; a lasting change rather than a one-off."));
        readinessFacts(q);
        q.add(video("READINESS", "What kind of work do you want to be doing in two years, and how does this role fit into that?", 90,
                "A clear direction that fits a hands-on backend role; genuine interest; realistic expectations."));
        return q;
    }

    /** Set C: Spring internals, price alerts, resilience, money and correctness. */
    static List<DraftQuestion> seniorJavaC() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "What is the hardest performance problem you have solved in a Java service? Tell us how you "
                + "measured it before and after.", 150,
                "A baseline in numbers; profiling (JFR, async-profiler, database profiler); a hypothesis tested; the fix; "
                        + "measured improvement."));
        q.add(video("EXPERIENCE", "Tell us about a time you took ownership of something nobody had asked you to own. What happened?",
                120, "Initiative; a real problem fixed; follow-through to production; impact on others."));
        q.add(video("SKILLS", "How does Spring Boot decide which beans to create and how to wire them? Explain auto-configuration, "
                + "component scanning, and what you do when two beans of the same type exist.", 150,
                "@SpringBootApplication combines configuration, auto-configuration and component scanning; auto-configuration "
                        + "uses conditions (on class, on missing bean) so your own beans win; constructor injection; @Primary or "
                        + "@Qualifier for two beans; the conditions report (--debug) or actuator to see why."));
        q.add(blank("In MongoDB, to update a document if it exists or insert it if it doesn't, set the ____ option to true.", "upsert"));
        q.add(blank("In Spring, to always run a method and store its result in the cache, annotate it with @____.", "CachePut"));
        q.add(blank("In a Java switch expression, a case written as a block returns its value with the ____ keyword.", "yield"));
        q.add(blank("When several Spring beans of the same type exist, the one annotated @____ is injected by default.", "Primary"));
        q.add(blank("The java.util.concurrent class that lets a thread wait until N other tasks have finished, counting down from "
                + "N, is ____.", "CountDownLatch"));
        q.add(blank("In Mockito, to make a mock return a value, write when(mock.call()).____(value).", "thenReturn"));
        q.add(video("PROJECTS", "Tell us about a system you designed or significantly changed. What alternatives did you "
                + "consider, and why did you choose the one you did?", 180,
                "Real alternatives with trade-offs (cost, complexity, performance, team skills); a reasoned choice; what "
                        + "happened afterwards."));
        q.add(video("PROJECTS", "Describe a time you changed a database schema or migrated data while the service kept running. "
                + "How did you avoid downtime?", 150,
                "Backward-compatible (expand and contract) changes; backfills; dual reads or writes during the move; feature "
                        + "flags; verification; a rollback plan."));
        q.add(video("ARCHITECTURE", "Design price-drop alerts: travellers save a route and get notified when the fare drops. There "
                + "are millions of saved searches and fares refresh every few minutes.", 180,
                "Subscriptions stored and indexed by route and date; react to fare-change events rather than polling every "
                        + "subscription; partition by route; remember the last price notified; de-duplicate and throttle "
                        + "notifications; idempotent sending; scale consumers."));
        q.add(video("ARCHITECTURE", "Your fare service uses Redis. What happens in most designs if Redis goes down, and how would "
                + "you design the service to stay up?", 150,
                "Without care, requests fail or hang on Redis calls; short timeouts on the client; fall back to the source "
                        + "with protection (circuit breaker, rate limit, a small local cache) so the database isn't flooded; "
                        + "Redis replication or a cluster; alerts."));
        q.add(choice("ARCHITECTURE", "Two services need the same customer data. What is usually best with microservices?",
                List.of("Both read and write the same database tables",
                        "One service owns the data; others call its API or keep a copy updated from its events",
                        "Copy the whole database to each service every night",
                        "Each service asks the user for the data again"), 1));
        q.add(choice("ARCHITECTURE", "In a microservices system using JWT, where should the token be validated?",
                List.of("Only in the frontend",
                        "Only once, at login",
                        "In each service that receives it: signature, expiry and audience",
                        "Nowhere; tokens can't be changed"), 2));
        q.add(choice("ARCHITECTURE", "A Kafka consumer group has 3 consumers reading a topic with 2 partitions. What happens?",
                List.of("All three consumers share both partitions evenly",
                        "Every consumer receives every message",
                        "Two consumers get one partition each; the third is idle",
                        "Kafka rejects the third consumer"), 2));
        q.add(choice("ARCHITECTURE", "What is the safest way to roll out a risky change to the booking flow?",
                List.of("Deploy it on Friday night when traffic is low",
                        "Release it to everyone at once and watch the logs",
                        "Ship it behind a feature flag and turn it on for a small share of traffic while watching metrics",
                        "Skip testing to release sooner and fix forward"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                List<String> names = new ArrayList<>(List.of("asha", "ravi"));
                List<String> view = Collections.unmodifiableList(names);
                names.add("meena");
                System.out.println(view.size());""",
                List.of("2", "3", "It throws UnsupportedOperationException", "0"), 1));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                Stream.of("a", "b", "c")
                        .peek(System.out::print)
                        .map(String::toUpperCase)
                        .findFirst();""",
                List.of("abc", "a", "A", "Nothing"), 1));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                class Seat {
                    final String no;
                    Seat(String no) { this.no = no; }
                    @Override public boolean equals(Object o) {
                        return o instanceof Seat s && s.no.equals(no);
                    }
                }
                Set<Seat> seats = new HashSet<>();
                seats.add(new Seat("12A"));
                seats.add(new Seat("12A"));
                System.out.println(seats.size());""",
                List.of("1", "2, because equals is overridden but hashCode is not", "0", "It does not compile"), 1));
        q.add(choice("TECHNICAL", "An @Async method that returns void throws an exception. What happens?",
                List.of("The caller receives the exception",
                        "The application crashes",
                        "The caller never sees it; it goes to the AsyncUncaughtExceptionHandler (logged by default)",
                        "Spring retries the method automatically"), 2));
        q.add(choice("TECHNICAL", "A booking document embeds every change ever made to it, and some now fail to save. What is "
                + "wrong, and the fix?",
                List.of("MongoDB documents are limited to 16 MB; move the growing history to its own collection that references the booking",
                        "Raise the document size limit in the MongoDB configuration",
                        "Use a bigger server",
                        "Turn on compression for that field"), 0));
        q.add(choice("TECHNICAL", "@Cacheable is on a method that takes a search-criteria object as its argument, but it never "
                + "gets a cache hit. Most likely cause?",
                List.of("Redis is down",
                        "The criteria class doesn't implement equals and hashCode, so equal arguments make different keys",
                        "@Cacheable only works on methods that return void",
                        "The cache name is too long"), 1));
        q.add(videoWithCode("TECHNICAL", "You are reviewing this method. What is wrong, and what would you change?", """
                public BigDecimal total(List<Booking> bookings) {
                    double total = 0;
                    for (Booking b : bookings) {
                        if (b.getStatus() == "CONFIRMED") {
                            total += b.getAmount().doubleValue();
                        }
                    }
                    return new BigDecimal(total);
                }""", 150,
                "Strings compared with == instead of equals (better: an enum); money summed as double loses precision (keep "
                        + "BigDecimal and add); new BigDecimal(double) is inexact (BigDecimal.valueOf); scale and rounding; "
                        + "mixed currencies; null amounts; could be a stream with reduce."));
        q.add(video("TECHNICAL", "One service is at 100% CPU and requests are timing out. Walk us through how you find what is "
                + "using the CPU, and what you do first.", 150,
                "Mitigate first (scale out, roll back a recent deploy); find hot threads (top -H, jstack, JFR or async-profiler "
                        + "flame graphs); rule out GC thrashing; look for hot loops, regex, serialisation or logging; compare with "
                        + "the last deploy."));
        q.add(video("TECHNICAL", "How would you test a class that calls an external airline API with retries and timeouts? What "
                + "would you test, and how?", 150,
                "Unit tests with mocks or WireMock simulating timeouts, 5xx errors and slow responses; check retry counts and "
                        + "backoff (inject a clock); circuit breaker states; contract tests; never the real API in CI; "
                        + "deterministic, fast tests."));
        q.add(video("SOFT_SKILLS", "On a Friday evening you discover that a bug you shipped is showing some customers the wrong "
                + "fare. What do you do, step by step?", 150,
                "Assess impact; stop the harm (feature flag or rollback); tell the right people early; fix with a test; "
                        + "communicate what happened; a blameless review; owns it."));
        q.add(video("SOFT_SKILLS", "How do you explain a technical trade-off to a non-technical product manager? Give an example.", 120,
                "Plain language; options with cost, risk and benefit; a recommendation; checks understanding."));
        q.add(video("SOFT_SKILLS", "Working with a team in another country, a decision you need keeps getting delayed. How do you "
                + "move it forward?", 120,
                "A short written proposal with options; a clear decision owner and date; asynchronous follow-up; escalates "
                        + "politely if needed; keeps working on what isn't blocked."));
        readinessFacts(q);
        q.add(video("READINESS", "Why are you looking for a change now, and why this role?", 90,
                "Honest, positive reasons; specific interest in this role and domain; no red flags about commitment."));
        return q;
    }

    /** Set D: integrations, cancellations and refunds, scaling for a sale, observability. */
    static List<DraftQuestion> seniorJavaD() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Tell us about the biggest scale you have worked at: requests per second, data size, number of "
                + "services. What broke first as it grew?", 150,
                "Concrete numbers; what failed first (database, a hot partition, connection pools, a downstream service); how "
                        + "they found and fixed it."));
        q.add(video("EXPERIENCE", "Describe a design or code review where your feedback changed the outcome. What did you spot, "
                + "and how did you raise it?", 120,
                "A real issue (correctness, security, performance, design); raised clearly with evidence; constructive; the result."));
        q.add(video("SKILLS", "How do you use Streams, Optional and records in Java 17 to make code clearer, and where would you "
                + "avoid Streams?", 120,
                "Streams and collectors for transformations; Optional as a return type, not for fields or parameters; records "
                        + "for immutable data and DTOs; avoid streams for side effects, complex control flow, checked exceptions "
                        + "and hot loops where boxing costs; readability first."));
        q.add(blank("In MongoDB, to remove matching items from an array field, use the ____ update operator.", "$pull", "pull"));
        q.add(blank("In Spring Data MongoDB, the class for building and running queries and updates in code is Mongo____.", "Template", "MongoTemplate"));
        q.add(blank("In Java, the Thread method that waits for that thread to finish is ____().", "join"));
        q.add(blank("The HTTP status code for 'too many requests' (rate limited) is ____.", "429"));
        q.add(blank("In Spring Boot, the annotation that binds a group of configuration properties to a class is @____.",
                "ConfigurationProperties"));
        q.add(blank("In JUnit 5, to check that code throws an exception, use assert____(SomeException.class, () -> ...).", "Throws", "assertThrows"));
        q.add(video("PROJECTS", "Tell us about an integration you built with an external system, such as a partner API, a GDS or "
                + "a payment provider. What made it hard, and how did you make it reliable?", 180,
                "Unreliable or slow APIs; timeouts and retries; idempotency; mapping their data to ours; sandbox gaps; "
                        + "monitoring; contract tests."));
        q.add(video("PROJECTS", "What technical debt did you leave behind on your last project, and why?", 120,
                "Honest; conscious, documented trade-offs; a plan or ticket to pay it back; no blame."));
        q.add(video("ARCHITECTURE", "Design a service that cancels bookings and refunds payments, where the airline, the payment "
                + "provider and our database must all agree, even if a step fails halfway.", 180,
                "A saga with a state machine; compensation steps; every step idempotent; outbox events; retries with backoff; a "
                        + "reconciliation job; a manual queue for stuck cases; an audit trail."));
        q.add(video("ARCHITECTURE", "Your shopping API must handle five times today's traffic during a holiday sale next month. "
                + "How do you prepare?", 180,
                "Load tests with realistic traffic; find bottlenecks; horizontal scaling or autoscaling; cache warming; rate "
                        + "limits and back-pressure; pool sizes; indexes and database capacity; protect downstream providers; "
                        + "dashboards, alerts and a runbook; degrade gracefully."));
        q.add(choice("ARCHITECTURE", "Clients retry POST /bookings after a timeout. How do you avoid duplicate bookings?",
                List.of("Change the endpoint to PUT",
                        "Tell clients not to retry",
                        "Accept an Idempotency-Key header, store it with a unique index, and return the first result on repeats",
                        "Add more server instances"), 2));
        q.add(choice("ARCHITECTURE", "A fare provider allows 100 requests per second for your account, and many instances of your "
                + "service call it. What keeps you under the limit?",
                List.of("Thread.sleep between calls in each instance",
                        "A shared rate limiter across instances (e.g. a token bucket in Redis), with queuing or graceful rejection",
                        "A bigger thread pool",
                        "Retry immediately whenever you get a 429"), 1));
        q.add(choice("ARCHITECTURE", "In Kubernetes, pods keep restarting under load because the liveness probe calls an endpoint "
                + "that checks the database. What is wrong?",
                List.of("Liveness should check only the app itself; a slow database makes healthy pods restart. Put dependency "
                                + "checks in the readiness probe",
                        "There are too few replicas; probes are fine",
                        "Remove all probes",
                        "Liveness should also call every downstream service"), 0));
        q.add(choice("ARCHITECTURE", "What does a SonarQube quality gate on 'new code' check?",
                List.of("That the whole codebase has 100% test coverage",
                        "Only formatting",
                        "That code changed recently (e.g. in this branch) meets the rules for coverage, bugs and vulnerabilities, so "
                                + "old debt doesn't block new work",
                        "Only that the code compiles"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                int[] counter = {0};
                List<Integer> nums = List.of(1, 2, 3, 4);
                nums.parallelStream().forEach(n -> counter[0] += n);
                System.out.println(counter[0]);""",
                List.of("Always 10", "Always 0",
                        "Usually 10, but it can be less: the parallel updates race and some can be lost", "It does not compile"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                record Fare(String route, int amount) {}
                var a = new Fare("BLR-DEL", 4500);
                var b = new Fare("BLR-DEL", 4500);
                System.out.println(a.equals(b) + " " + (a == b) + " " + a);""",
                List.of("true true Fare[route=BLR-DEL, amount=4500]", "false false Fare@1b6d3586",
                        "true false Fare[route=BLR-DEL, amount=4500]", "true false Fare{BLR-DEL, 4500}"), 2));
        q.add(choiceWithCode("TECHNICAL", "What does this print?", """
                String code = null;
                switch (Optional.ofNullable(code).orElse("NONE")) {
                    case "NONE" -> System.out.println("empty");
                    default -> System.out.println("value");
                }""",
                List.of("empty", "value", "It throws NullPointerException", "It does not compile"), 0));
        q.add(choice("TECHNICAL", "A @Scheduled job runs every minute on each of 4 instances and sends duplicate emails. What "
                + "fixes it?",
                List.of("Make the method synchronized",
                        "Use fixedDelay instead of cron",
                        "A distributed lock such as ShedLock, or run the job in only one place",
                        "Add @Async to the job"), 2));
        q.add(choice("TECHNICAL", "A MongoDB aggregation that groups 20 million booking documents by airline is slow. What usually "
                + "helps most?",
                List.of("Add a $sort at the end",
                        "Start with a $match that uses an index, so fewer documents enter the pipeline",
                        "Add a $project after the $group",
                        "Turn on allowDiskUse"), 1));
        q.add(choice("TECHNICAL", "An access token (JWT) is valid for 24 hours, and the user's account is disabled. What happens by "
                + "default, and what is the usual fix?",
                List.of("The token stops working at once",
                        "The token keeps working until it expires; use short-lived access tokens with refresh tokens, or a deny list",
                        "The server deletes the token from the browser",
                        "The token's signature changes automatically"), 1));
        q.add(videoWithCode("TECHNICAL", "You are reviewing this method. What is wrong, and what would you change?", """
                public List<Fare> cheapest(List<Fare> fares) {
                    Collections.sort(fares, (a, b) -> (int) (a.getPrice() - b.getPrice()));
                    return fares.subList(0, 10);
                }""", 150,
                "Sorts the caller's list in place (sort a copy or use a stream); the subtracting comparator truncates and can "
                        + "overflow (Comparator.comparing or Double.compare); subList(0, 10) throws when there are fewer than 10 "
                        + "(use limit); subList is a view of the original; null prices; money as BigDecimal."));
        q.add(video("TECHNICAL", "Logs show 'connection pool exhausted' for MongoDB during peak hours. How do you investigate and "
                + "fix it?", 150,
                "Pool metrics; slow queries (profiler, explain, missing indexes) holding connections; unclosed cursors or "
                        + "leaks; pool size vs number of instances vs server limits; timeouts; not just raising the pool size; "
                        + "load testing the fix."));
        q.add(video("TECHNICAL", "How do you decide what to log and at which level, and how do you follow one booking request "
                + "across several services?", 150,
                "SLF4J with sensible levels; structured logs; no personal data or secrets; correlation or trace IDs (MDC); "
                        + "OpenTelemetry tracing; metrics for counts and latency rather than logs; sampling."));
        q.add(video("SOFT_SKILLS", "You estimated a feature at five days. On day four you realise it needs ten. What do you do?", 120,
                "Raises it as soon as known; explains why; offers options (cut scope, extend, get help); re-plans; learns for "
                        + "future estimates."));
        q.add(video("SOFT_SKILLS", "QA in another time zone reports a bug you can't reproduce. How do you handle it?", 120,
                "Asks for steps, data and environment; checks logs and trace IDs; compares environments; adds logging if "
                        + "needed; works with QA asynchronously; never dismisses it."));
        q.add(video("SOFT_SKILLS", "What do you do to keep growing as an engineer? Give a recent example.", 90,
                "A specific, recent example of learning something and applying it at work."));
        readinessFacts(q);
        q.add(video("READINESS", "What would make you say yes to this offer, and what would make you hesitate?", 90,
                "Honest priorities; serious interest; any blockers (notice, location, salary) raised openly."));
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
