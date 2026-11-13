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
        q.add(intro());
        ownership(q);
        q.add(d5());
        q.add(d9());
        q.add(d13());
        q.add(d21());
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
        q.add(regrettedDecision());
        architectureCore(q);
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
        technicalCore(q);
        q.add(d1());
        q.add(d17());
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
        codingCore(q);
        q.add(video("SOFT_SKILLS", "You get a one-line ticket: 'Let travel agents change the seat on a booking.' Before writing "
                + "code, what questions do you ask, and how do you break it into pieces you can ship?", 150,
                "Asks about scope (which bookings and airlines, fees, who may do it), edge cases (partial failures, a seat "
                        + "taken meanwhile), non-functional needs (latency, audit, idempotency); splits into small deliverable "
                        + "slices; confirms with the product manager; estimates."));
        q.add(video("SOFT_SKILLS", "Product wants a feature by Friday. You believe it creates a production risk. Walk us "
                + "through the conversation and how it ends.", 120,
                "Puts the risk in concrete terms; offers options (smaller scope, a feature flag, a later date); makes the "
                        + "trade-off visible; commits to the decision once made."));
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

    // ---- the senior architect's questions: in every set ----

    private static void ownership(List<DraftQuestion> q) {
        q.add(video("EXPERIENCE", "Tell us about a system or service where you had ownership. What did it do, who used it, and "
                + "what were you personally responsible for?", 150,
                "Clear ownership (design, build, run, on-call); what the service does and for whom; concrete decisions they "
                        + "made themselves; how they kept it healthy in production."));
        q.add(video("EXPERIENCE", "For that system, what were the scale, latency, throughput and reliability constraints, and how "
                + "did they shape your design?", 120,
                "Real numbers (requests per second, p95/p99 latency targets, data volume, uptime or SLOs); how the constraints "
                        + "drove choices (caching, async processing, partitioning, replication, timeouts); how they measured them."));
    }

    private static DraftQuestion regrettedDecision() {
        return video("PROJECTS", "What design decision did you later regret, and what would you do differently?", 120,
                "An honest, specific decision; why it seemed right at the time; what it cost; what they changed or would "
                        + "change; what they learned. Saying 'none' is a warning sign.");
    }

    private static void architectureCore(List<DraftQuestion> q) {
        q.add(video("ARCHITECTURE", "Explain the system design of a notification system (email, SMS and push) for millions of "
                + "users.", 180,
                "Intake through an API or events (Kafka) decoupled from sending; templates and user preferences and opt-outs; "
                        + "workers per channel and provider; queues with retries, backoff and a dead-letter queue; idempotency "
                        + "or de-duplication keys; rate limits per provider and per user; priorities (OTP before marketing); "
                        + "scheduling and time zones; delivery status from provider callbacks; provider failover; partitioning "
                        + "to scale; monitoring."));
        q.add(video("ARCHITECTURE", "Imagine there is only one item left in stock and many users try to buy it at the same moment. "
                + "How do you make sure it is sold to only one user?", 150,
                "One atomic conditional update (UPDATE ... SET qty = qty - 1 WHERE id = ? AND qty > 0, or MongoDB $inc with a "
                        + "$gte condition) and checking that a row or document changed; or optimistic locking with a version and "
                        + "retry; or SELECT ... FOR UPDATE; a reservation with expiry during checkout; idempotency for retries; "
                        + "not an in-JVM synchronized (several instances); a cached count is not the source of truth; what the "
                        + "other users see."));
        q.add(video("ARCHITECTURE", "A UI screen loads one million records by fetching them from an API. Identify every stage "
                + "where it is slow, and how you would improve each one.", 180,
                "Database query (indexes, only the needed columns); server memory and serialisation of a million objects "
                        + "(stream, paginate); payload size over the network (pagination or cursors, compression, field "
                        + "selection); the browser parsing JSON and holding it in memory; rendering a million rows (virtualised "
                        + "lists); best of all, not loading a million rows: server-side paging, filtering, sorting, search and "
                        + "aggregation; caching; full exports as a background job."));
        q.add(video("ARCHITECTURE", "When can caching lead to failures? Give examples and how you prevent them.", 120,
                "Stale data (wrong prices) — TTLs, invalidation, versioning; stampedes when many keys expire — jitter and "
                        + "request coalescing; the cache going down and flooding the database — timeouts and protected "
                        + "fallbacks; cold starts; inconsistent per-instance caches; caching errors or nulls; unbounded caches "
                        + "using up memory; personal data cached under the wrong key; lookups for keys that never exist."));
        q.add(video("ARCHITECTURE", "Give examples of anti-patterns you have seen in Java services or microservices, and how you "
                + "fixed them.", 120,
                "Concrete examples such as god classes, a distributed monolith (chatty synchronous calls, a shared "
                        + "database), swallowed exceptions, N+1 queries, entities used as API objects, field injection, retries "
                        + "without backoff; why each is harmful and how it was fixed."));
        q.add(video("ARCHITECTURE", "What monitoring and logging strategy would you put in place for a critical API?", 150,
                "Rate, errors and latency percentiles (p95/p99) plus saturation; SLOs and alerts on symptoms, with runbooks; "
                        + "structured logs with correlation or trace IDs, sensible levels, no personal data or secrets; "
                        + "distributed tracing (OpenTelemetry); dashboards (Prometheus, Grafana); synthetic checks; health of "
                        + "dependencies; business metrics such as bookings per minute; avoiding alert fatigue."));
    }

    private static void technicalCore(List<DraftQuestion> q) {
        q.add(video("TECHNICAL", "How do you debug high memory usage or an OutOfMemoryError in a Java service?", 150,
                "Heap dump on OOM or with jcmd, analysed in Eclipse MAT (dominator tree), comparing dumps over time; GC logs and "
                        + "the heap trend; which memory it is (heap, metaspace, direct buffers, thread stacks, native); "
                        + "container limit vs -Xmx (MaxRAMPercentage); usual causes (unbounded caches, ThreadLocals, listeners, "
                        + "growing collections); mitigate while investigating; verify the fix under load."));
        q.add(video("TECHNICAL", "How do you identify a deadlock or thread contention in a running Java application?", 150,
                "Several thread dumps a few seconds apart (jstack or jcmd); the JVM reports deadlocks it finds; many BLOCKED "
                        + "threads waiting on the same monitor; JFR lock events or async-profiler; ThreadMXBean; symptoms (low "
                        + "CPU, throughput drops, requests piling up); fixes: consistent lock ordering, smaller critical sections, "
                        + "tryLock with timeouts, concurrent collections; thread-pool starvation as a cause."));
        q.add(video("TECHNICAL", "Explain the basics of GC tuning. When would you avoid tuning the GC first?", 150,
                "Pick the collector by goal (G1 by default, ZGC for low pauses, Parallel for throughput); size the heap (equal "
                        + "-Xms and -Xmx, MaxRAMPercentage in containers); pause-time goals; read GC logs (-Xlog:gc*) and measure "
                        + "before changing anything; change one setting at a time. Avoid it first when the cause is a leak, "
                        + "excessive allocation in code, an undersized container, or the slowness isn't GC at all — profile first."));
        q.add(video("TECHNICAL", "How do you design global exception handling in a Spring Boot API so clients get useful errors "
                + "but internal details are not leaked?", 150,
                "A central @RestControllerAdvice mapping exceptions to status codes; one consistent error body (problem details: "
                        + "type, title, status, detail, an error code, a trace ID); field-level validation errors; no stack "
                        + "traces, SQL, class names or internal messages to clients; full details logged server-side with the "
                        + "trace ID; domain errors (4xx) vs unexpected ones (500 with a generic message); never swallowing "
                        + "exceptions."));
        q.add(video("TECHNICAL", "A SQL query with several joins has become slow. How would you find out why and make it fast?", 150,
                "EXPLAIN or EXPLAIN ANALYZE to read the plan; indexes on join and filter columns (composite, covering); no "
                        + "functions on indexed columns; only the columns and rows needed (pagination); statistics and row "
                        + "estimates; N+1 from the ORM; denormalising or pre-aggregating (a materialised view) for read-heavy "
                        + "reports; caching; measuring before and after."));
    }

    private static void codingCore(List<DraftQuestion> q) {
        q.add(writtenCode("Write a Java program that prints how many times each character occurs in a string. Type your code "
                + "below; it isn't run, so write it as you would for a code review.",
                "Correct counting (a LinkedHashMap to keep order, an int array for ASCII, or streams with groupingBy and "
                        + "counting); handles null and empty input; a clear decision on case and spaces; O(n) time; readable "
                        + "output; a bonus for Unicode code points or a small test."));
        q.add(writtenCode("Write Java code that transfers a given amount from one account to another, and the JUnit tests for it. "
                + "Type your code below; it isn't run. Show how you handle invalid input, insufficient funds and transfers "
                + "happening at the same time.",
                "BigDecimal for money; validates amount > 0, two different accounts that exist; insufficient funds raises a "
                        + "specific exception and changes nothing; both balances change or neither (a transaction or locks); "
                        + "concurrent transfers lock both accounts in a consistent order (e.g. by id) to avoid deadlock, or use "
                        + "optimistic locking with retry; idempotency for retries. Tests: success, insufficient funds, zero or "
                        + "negative amount, same account, concurrent transfers keep the total unchanged, A to B and B to A at "
                        + "once without deadlock."));
    }

    // ---- the team's concept questions (Dhaval's list), spread across the sets ----

    private static DraftQuestion d1() {
        return video("TECHNICAL", "Explain the difference between HashMap and ConcurrentHashMap, and when you would use each.", 90,
                "HashMap isn't thread-safe (lost updates, corrupted state under concurrent writes) and allows a null key and "
                        + "values; ConcurrentHashMap is thread-safe with fine-grained locking and CAS, no nulls, weakly consistent "
                        + "iterators, atomic compute, merge and putIfAbsent; synchronizedMap locks the whole map.");
    }

    private static DraftQuestion d2() {
        return video("TECHNICAL", "How is JVM memory divided?", 90,
                "Heap (young generation: eden and survivor spaces; old generation); metaspace for class metadata (native, "
                        + "replaced PermGen); a stack per thread; the code cache for JIT-compiled code; direct and native memory; "
                        + "what an OOM in each looks like; a container limit covers all of it, not just -Xmx.");
    }

    private static DraftQuestion d3() {
        return video("TECHNICAL", "What is the difference between synchronized, ReentrantLock and ReadWriteLock? When would you "
                + "use each?", 120,
                "synchronized: built-in monitor, simple, released automatically, no timeout; ReentrantLock: unlock in finally, "
                        + "tryLock with timeout, interruptible, fairness, conditions; ReadWriteLock: many readers or one writer, "
                        + "for read-heavy data; StampedLock for optimistic reads; prefer concurrent collections or atomics "
                        + "where possible; pitfalls such as forgetting to unlock or writer starvation.");
    }

    private static DraftQuestion d4() {
        return video("TECHNICAL", "Explain CompletableFuture and where you would use it.", 120,
                "Asynchronous computation with callbacks; supplyAsync with a dedicated executor (not the common pool for "
                        + "blocking I/O); thenApply, thenCompose, thenCombine; allOf for fanning out to several providers and "
                        + "combining; exceptionally or handle for errors; orTimeout; join vs get; passing context such as MDC.");
    }

    private static DraftQuestion d5() {
        return video("SKILLS", "What happens internally when a Spring Boot application starts?", 120,
                "SpringApplication.run; the environment (properties, profiles); creating the application context; component "
                        + "scanning and bean definitions; auto-configuration with conditions; creating beans, injecting "
                        + "dependencies, post-processors and proxies (@Transactional, @Async); starting the embedded server; "
                        + "runners and the ready event; how to investigate slow startup.");
    }

    private static DraftQuestion d6() {
        return video("SKILLS", "Which Spring Boot Actuator endpoints have you used, and for what?", 90,
                "health with liveness and readiness groups for Kubernetes, info, metrics and prometheus, loggers (change levels "
                        + "at runtime), env and configprops with care, threaddump, heapdump, mappings, startup; securing them "
                        + "(separate port, authentication, exposing only what's needed).");
    }

    private static DraftQuestion d7() {
        return video("SKILLS", "How do you shut down a Spring Boot service gracefully?", 90,
                "server.shutdown=graceful with a timeout per phase; readiness goes down first so no new traffic arrives; "
                        + "in-flight requests finish; consumers stop and commit offsets; scheduled and async work completes; "
                        + "pools close; Kubernetes preStop and terminationGracePeriodSeconds; SIGTERM, not kill -9.");
    }

    private static DraftQuestion d8() {
        return video("SKILLS", "How do you externalize configuration across environments such as dev, QA and production?", 90,
                "application.yml with profiles; environment variables and command-line overrides; @ConfigurationProperties with "
                        + "validation; Kubernetes ConfigMaps or Spring Cloud Config; one build promoted through environments; "
                        + "secrets kept separately.");
    }

    private static DraftQuestion d9() {
        return video("SKILLS", "How do you handle secrets such as database passwords and API keys?", 90,
                "Never in code or Git (scan for leaks); environment variables or a secrets manager (Vault, a cloud secret "
                        + "manager, encrypted Kubernetes secrets); least privilege; rotation; never logged; different per "
                        + "environment; access audited.");
    }

    private static DraftQuestion d11() {
        return video("SKILLS", "Explain filters vs interceptors in Spring. When would you use each?", 90,
                "A servlet filter runs before the DispatcherServlet for every request (security, CORS, logging, correlation "
                        + "IDs, compression); a HandlerInterceptor runs in Spring MVC and knows the controller method (preHandle, "
                        + "postHandle, afterCompletion) for per-endpoint checks or timing; ordering; AOP for method-level concerns.");
    }

    private static DraftQuestion d12() {
        return video("SKILLS", "What is the difference between PUT and PATCH?", 60,
                "PUT replaces the whole resource and is idempotent (missing fields are reset); PATCH is a partial update (JSON "
                        + "Merge Patch or JSON Patch) and isn't necessarily idempotent; concurrency with ETag and If-Match.");
    }

    private static DraftQuestion d13() {
        return video("SKILLS", "When would you use query parameters, and when path variables?", 60,
                "Path variables identify a resource (/bookings/{id}); query parameters filter, sort, page or search "
                        + "(/flights?from=BLR&to=DEL); required vs optional; consistent URL design.");
    }

    private static DraftQuestion d14() {
        return video("ARCHITECTURE", "How would you implement idempotency in a payment API?", 120,
                "The client sends an Idempotency-Key; store the key, a hash of the request and the result with a unique "
                        + "constraint; return the stored result on a repeat and reject a different payload with the same key; "
                        + "handle a request still in progress; expire old keys; keep it atomic with the payment record; pass "
                        + "the key to the payment provider.");
    }

    private static DraftQuestion d15() {
        return video("ARCHITECTURE", "What is the difference between a correlation ID and an idempotency key?", 90,
                "A correlation ID traces one request across services and logs and doesn't change behaviour; an idempotency key "
                        + "identifies one logical operation so retries aren't executed twice, is chosen by the client, reused on "
                        + "retries and stored by the server; a retry has the same idempotency key but a new correlation ID.");
    }

    private static DraftQuestion d16() {
        return video("SKILLS", "How do you secure REST APIs?", 120,
                "HTTPS everywhere; authentication (OAuth2 or OIDC, JWT validation) and authorisation per endpoint and per "
                        + "resource (ownership checks); input validation; rate limiting; nothing sensitive in URLs or logs; CORS "
                        + "and security headers; the OWASP Top 10 (injection, broken object-level authorisation); secrets "
                        + "management; dependency scanning; audit logs.");
    }

    private static DraftQuestion d17() {
        return video("TECHNICAL", "In MongoDB, when do you embed documents and when do you reference them?", 90,
                "Embed when data is read together, owned by the parent, bounded in size and updated together (single-document "
                        + "atomicity); reference when many-to-many, growing without bound (the 16 MB limit), shared or updated "
                        + "independently; duplication vs $lookup; access patterns drive the schema.");
    }

    private static DraftQuestion d18() {
        return video("TECHNICAL", "Explain compound indexes in MongoDB.", 90,
                "One index on several fields in order; the prefix rule; the equality, sort, range (ESR) rule; sort direction "
                        + "for multi-field sorts; covered queries; the cost on writes and memory; checking with explain().");
    }

    private static DraftQuestion d19() {
        return video("TECHNICAL", "Why might a MongoDB query do a collection scan, and how do you fix it?", 90,
                "No index on the filter fields, or one that can't be used (wrong field order, unanchored regex, $ne or $nin, "
                        + "$where, case-insensitive matching without a collation, type mismatches); low selectivity; check "
                        + "explain('executionStats') for COLLSCAN vs IXSCAN and documents examined vs returned; add or reorder "
                        + "an index or rewrite the query.");
    }

    private static DraftQuestion d20() {
        return video("TECHNICAL", "How do you identify slow queries in MongoDB?", 90,
                "The database profiler with slowms and system.profile; slow query logs; explain('executionStats'); Atlas "
                        + "Performance Advisor or Query Profiler; currentOp for running queries; timings per repository call in "
                        + "the application (Micrometer, tracing); then fix indexes or queries and verify.");
    }

    private static DraftQuestion d21() {
        return video("SKILLS", "What is the difference between authentication and authorization?", 60,
                "Authentication proves who you are (login, a token); authorization decides what you may do (roles, scopes, "
                        + "resource ownership); 401 vs 403; both checked on every request.");
    }

    private static DraftQuestion d22() {
        return video("TECHNICAL", "Explain Spring transactions, covering propagation and isolation levels.", 150,
                "@Transactional works through proxies; propagation: REQUIRED, REQUIRES_NEW, NESTED (savepoints), SUPPORTS, "
                        + "MANDATORY, NEVER, NOT_SUPPORTED; isolation: READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, "
                        + "SERIALIZABLE and the anomalies each prevents (dirty, non-repeatable and phantom reads); rollback on "
                        + "unchecked exceptions by default; the self-invocation pitfall; readOnly; MongoDB transactions need a "
                        + "replica set.");
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
        ownership(q);
        q.add(d6());
        q.add(d12());
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
        q.add(regrettedDecision());
        architectureCore(q);
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
        technicalCore(q);
        q.add(d2());
        q.add(d18());
        q.add(d22());
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
        codingCore(q);
        q.add(video("SOFT_SKILLS", "You are asked to 'add multi-city search' with no other detail, and the product manager is away "
                + "for two days. What do you do?", 150,
                "Writes down questions and assumptions; starts on the parts that are certain or does a spike; shares a short "
                        + "written proposal; doesn't sit idle or build everything on guesses."));
        q.add(video("SOFT_SKILLS", "A teammate's pull request has a design you think will cause problems later, and the release "
                + "is tomorrow. What do you do?", 120,
                "Talks to them directly; separates must-fix from follow-up; explains with concrete examples; agrees a plan "
                        + "(small fix now, ticket for later); respectful."));
        readinessFacts(q);
        q.add(video("READINESS", "What kind of work do you want to be doing in two years, and how does this role fit into that?", 90,
                "A clear direction that fits a hands-on backend role; genuine interest; realistic expectations."));
        return q;
    }

    /** Set C: Spring internals, price alerts, resilience, money and correctness. */
    static List<DraftQuestion> seniorJavaC() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        ownership(q);
        q.add(d7());
        q.add(d11());
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
        q.add(regrettedDecision());
        architectureCore(q);
        q.add(d14());
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
        technicalCore(q);
        q.add(d3());
        q.add(d19());
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
        codingCore(q);
        q.add(video("SOFT_SKILLS", "On a Friday evening you discover that a bug you shipped is showing some customers the wrong "
                + "fare. What do you do, step by step?", 150,
                "Assess impact; stop the harm (feature flag or rollback); tell the right people early; fix with a test; "
                        + "communicate what happened; a blameless review; owns it."));
        q.add(video("SOFT_SKILLS", "How do you explain a technical trade-off to a non-technical product manager? Give an example.", 120,
                "Plain language; options with cost, risk and benefit; a recommendation; checks understanding."));
        readinessFacts(q);
        q.add(video("READINESS", "Why are you looking for a change now, and why this role?", 90,
                "Honest, positive reasons; specific interest in this role and domain; no red flags about commitment."));
        return q;
    }

    /** Set D: integrations, cancellations and refunds, scaling for a sale, observability. */
    static List<DraftQuestion> seniorJavaD() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        ownership(q);
        q.add(d8());
        q.add(d16());
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
        q.add(regrettedDecision());
        architectureCore(q);
        q.add(d15());
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
        technicalCore(q);
        q.add(d4());
        q.add(d20());
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
        codingCore(q);
        q.add(video("SOFT_SKILLS", "You estimated a feature at five days. On day four you realise it needs ten. What do you do?", 120,
                "Raises it as soon as known; explains why; offers options (cut scope, extend, get help); re-plans; learns for "
                        + "future estimates."));
        q.add(video("SOFT_SKILLS", "QA in another time zone reports a bug you can't reproduce. How do you handle it?", 120,
                "Asks for steps, data and environment; checks logs and trace IDs; compares environments; adds logging if "
                        + "needed; works with QA asynchronously; never dismisses it."));
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

    /** A typed code answer in the technical round; reviewed by the AI and people, not run. */
    private static DraftQuestion writtenCode(String prompt, String good) {
        return new DraftQuestion("TECHNICAL", "WRITTEN", prompt, "", List.of(), -1, List.of(), 0, good);
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
