# ADR-0002: Client hiring — clients as records, not tenants

- **Status**: accepted
- **Date**: 2026-09-25

## Context

CodeWalnut hires for itself **and for its clients**. Client hiring comes
in two forms: candidates hired onto CodeWalnut's payroll and deployed to a
client project, and direct placements onto the client's payroll. In both,
CodeWalnut sources, screens and tests candidates, then submits a shortlist;
the client reviews, interviews and decides.

ADR-0001 left multi-tenancy out and said to decide before chunk 1 if
clients came into scope. They have.

## Decision

1. **Single-tenant, CodeWalnut-run ATS.** `Client` is a domain entity, not
   a tenant. Jobs and requisitions carry a `hiring_type` (`internal`,
   `client_deployed`, `direct_placement`) and a nullable `client_id`.
   There is no per-client database or schema.
2. **`Submission` is the only channel from CodeWalnut to a client.** It
   stores an immutable snapshot of exactly what the client saw (redacted,
   branded profile, CV version, assessment summary, rate/CTC shown).
   Client users never read `Candidate`, internal `Scorecard`s or notes.
3. **Client Reviewers authenticate by magic link** tied to one
   `ClientContact` and one client, expiring and revocable. A full client
   login/portal is v1, built on the same principal and scoping.
4. **Client scoping lives in `AccessPolicy`** alongside role and job
   scoping; every query for a client principal is filtered by its
   `client_id` in the service layer, with deny tests for cross-client
   access.
5. **Contact details are stripped from submissions by default**, and a
   duplicate-submission guard (same candidate, same client, configurable
   window) prevents double submissions and ownership disputes.

## Alternatives considered

- **Multi-tenant SaaS (tenant per client, `org_id` on every table)** —
  needed only if clients run their own hiring on the platform. They
  don't; CodeWalnut runs the process. Rejected as large up-front cost for
  no current need.
- **Email-only submissions (no client access)** — simplest, but loses
  structured client feedback, turnaround metrics and the audit trail.
  Kept as a fallback: recruiters can record client feedback on the
  client's behalf.
- **Sharing the live candidate profile with clients** — rejected: later
  edits would change what the client saw, and it risks leaking contact
  details and internal notes.

## Consequences

- Chunk 1 adds `Client`, `hiring_type` and `client_id` from the start;
  a new chunk (client submissions & review) is added to the MVP, moving
  the estimate from ~10 to ~12 weeks.
- Reports can slice by client with no extra infrastructure.
- If CodeWalnut later wants to offer the ATS as a product to clients,
  that is a new ADR superseding decision 1.
