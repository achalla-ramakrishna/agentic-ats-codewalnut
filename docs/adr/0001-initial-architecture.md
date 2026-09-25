# ADR-0001: Initial architecture — stack and shape

- **Status**: accepted — the H2 parts are superseded by ADR-0003 (MySQL everywhere)
- **Date**: 2026-09-25

## Context

CodeWalnut needs an internal ATS (see `docs/SPEC.md`). Before chunk 0 we
need to fix the app's stack and overall shape. Constraints: a small team,
internal-tool load (hundreds of users, ~100k candidates over time), a
public careers page that should be indexable by search engines, several
third-party integrations with webhooks and retries, and strict privacy /
audit requirements on candidate data.

CodeWalnut's team already builds and runs Spring Boot + React
applications, so using that stack means existing conventions, CI and
local setup apply with no new learning curve.

## Decisions

1. **Java + React stack**:
   - Backend: **Spring Boot 3 (Java 21)**, Maven, Spring Web, Spring Data
     JPA, Bean Validation, Lombok.
   - Frontend: **React + TypeScript (Vite)** SPA for staff and the
     candidate portal.
   - Database: **MySQL 8** for real environments, **H2** in-memory for
     `mvn test` and quick local runs; schema via **Flyway**, with
     migrations mirrored in `db/migration/{h2,mysql}`.
2. **Modular monolith**: one Spring Boot app, package-by-layer, with the
   ATS modules as service groups (see `docs/architecture.md`). No
   microservices.
3. **Auth**: **Spring Security OAuth2 login with Google Workspace** for
   staff; signed, single-use **magic links** for candidates. No
   passwords stored.
4. **Background work without new infrastructure**: a `background_task`
   table in MySQL (outbox pattern) polled by `@Scheduled` workers, with retry
   count, backoff and idempotency key. Handles email, reminders, CV
   parsing, provider calls and retention. Revisit (e.g. JobRunr or a
   broker) only if volume demands it.
5. **Careers page**: the 2–3 public pages (job list, job detail, apply)
   are server-rendered by Spring Boot with **Thymeleaf**, so they are
   indexable and fast without SSR in the React app. Everything behind
   login is the React SPA.
6. **Search**: MySQL `FULLTEXT` indexes on candidate name, skills and
   parsed CV text. Add a search engine only if measured latency misses
   the spec's target.
7. **Semi-structured data** (scorecard ratings, offer CTC breakdown,
   parsed CV fields) uses MySQL `JSON` columns.
8. **LLM** calls sit behind one `LlmService` (provider-swappable, every
   call logged); AI output is advisory only.

## Alternatives considered

- **Next.js + Prisma + Postgres (TypeScript end to end)** — fewer moving
  parts (one app) and shared types, but a stack the team does not
  already run in production. Rejected for team fit.
- **Redis + a queue library for jobs** — more capable, but extra
  infrastructure the outbox table doesn't need yet.
- **SSR for the careers page via a separate Next.js app** — rejected;
  Thymeleaf covers three pages inside the existing app.
- **Buy (Greenhouse, Lever, Zoho Recruit, Keka Hire)** — open question in
  the spec; this ADR assumes build was chosen.

## Consequences

- Local dev needs only a JDK 21 and Node; MySQL is optional (`dev`
  profile uses H2).
- Every schema change is a Flyway migration mirrored into `h2/` and
  `mysql/`; the dialects differ (UUIDs as `BINARY(16)`, `JSON` column
  support, `ALTER` syntax), so each migration is verified against a real
  MySQL before merge.
- `JSON` and `FULLTEXT` behave differently on H2; tests that depend on
  them run against MySQL (Testcontainers) rather than H2.
- Multi-tenancy is **not** designed in. Client hiring is handled without
  it — see ADR-0002. Offering the ATS as a product to clients would need
  a new ADR.
