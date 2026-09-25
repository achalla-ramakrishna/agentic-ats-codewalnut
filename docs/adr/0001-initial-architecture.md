# ADR-0001: Initial architecture — stack and shape

- **Status**: proposed
- **Date**: 2026-09-25

## Context

CodeWalnut needs an internal ATS (see `docs/SPEC.md`). Before chunk 0 we
need to fix the app's stack and overall shape. Constraints: a small team
(3–6 engineers), internal-tool load (hundreds of users, ~100k candidates
over time), a public careers page that should be SEO-friendly, several
third-party integrations with webhooks and retries, and strict privacy /
audit requirements on candidate data.

## Decisions

1. **Modular monolith**, not microservices. One API with module
   boundaries (see `docs/architecture.md`), one database, one worker
   process. Splitting later is possible; distributed-system overhead now
   is not justified.
2. **TypeScript end to end**: Next.js (React) for staff UI, careers page
   and candidate portal; Node API (NestJS); shared types and zod schemas
   in a workspace package. Plays to CodeWalnut's React strength and lets
   RBAC policy and validation be shared between UI and API.
3. **PostgreSQL** as the single store, including full-text search
   (`tsvector` + `pg_trgm`) and `jsonb` for scorecard ratings, offer
   breakdowns and parsed CVs. Add a search engine only if measured
   search latency misses the spec's target.
4. **BullMQ on Redis** for all async work: email, reminders, CV parsing,
   provider calls, webhook processing, retention jobs.
5. **Google Workspace SSO for staff, magic links for candidates** — no
   passwords stored.
6. **LLM behind a single `ai` module**, provider-swappable, every call
   logged; AI output is advisory only.

## Alternatives considered

- **Spring Boot 3 (Java 21) + React**, matching `agentic-pr-reviewer`.
  Strong option if the team maintaining the ATS is the same Java team;
  loses shared types between UI and API and SSR for the careers page
  would need a separate Next.js app anyway. Revisit if the owning team
  prefers Java — the module boundaries above port directly.
- **Buy (Greenhouse, Lever, Zoho Recruit, Keka Hire)** — open question in
  the spec; this ADR assumes build was chosen.
- **Microservices per module** — rejected for team size and load.

## Consequences

- One deployable API + one worker + one web app; simple CI/CD.
- RBAC and stage-transition rules live in exactly one module each; the
  API is the enforcement point, not the UI.
- Multi-tenancy is **not** designed in. If the ATS is to be offered to
  clients, add `org_id` scoping to every table before chunk 1 and record
  that in a new ADR.
