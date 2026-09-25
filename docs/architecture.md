# Architecture

Nothing is built yet; this is the target shape. Stack choice and
alternatives are recorded in `docs/adr/0001-initial-architecture.md`.

## Repo overview

```
agentic-ats-codewalnut/
  backend/   Spring Boot 3 (Java 21) API + server-rendered careers pages
  frontend/  React + TypeScript (Vite) app for staff and candidate portal
    src/api/          HTTP client (session + CSRF) and typed endpoint wrappers
    src/auth/         AuthContext: loads GET /me, sign-in state
    src/components/   AppShell (server-driven navigation) + ui/ design system
    src/pages/        one component per screen
    src/styles/       design tokens (light/dark) and globals
  docs/      spec, feature requirements, architecture, ADRs
```

Sign-in: the SPA calls `GET /api/v1/me`; `401` shows the login page. Google
sign-in is a full-page redirect to `/oauth2/authorization/google`, handled by
Spring Security, which maps the Google account to a provisioned `AppUser`
and creates a server-side session (HTTP-only cookie). CSRF uses the
`XSRF-TOKEN` cookie echoed as `X-XSRF-TOKEN`.

## Shape

```
                 ┌──────────────────────────┐
 Recruiters,     │  React SPA (frontend/)    │──┐
 HMs, panel ───▶ │  staff UI + cand. portal  │  │  /api/v1
                 └──────────────────────────┘  │
                                               ▼
 Client reviewers ── review page (magic link) ─┐
                                               │
 Candidates ── careers page (Thymeleaf) ──▶ ┌────────────────────┐    ┌──────────┐
                                            │  Spring Boot app    │──▶│  MySQL    │
 Providers ── /webhooks/{provider} ───────▶ │  (backend/)         │    │  (+ task  │
 (assessment, e-sign, email)                │                     │◀──│  table)   │
                                            │  @Scheduled workers │    └──────────┘
                                            └─────────┬──────────┘
                                                      ▼
                                   Object storage (CVs, offers) · Google Calendar/Gmail
                                   · assessment provider · e-sign · LLM · Slack · HRMS
```

## Module boundaries (backend)

```
com.codewalnut.ats
  controller/   REST endpoints (/api/v1) + Thymeleaf careers controllers.
                No business logic — delegate to service/.
  service/      Business rules, one group per ATS module (below).
  client/       Outbound clients to external providers. Nothing else calls
                a provider API directly.
  task/         Outbox BackgroundTask table + @Scheduled workers (email, reminders,
                parsing, provider calls, retention).
  security/     Spring Security config, Google OAuth2 login, magic links
                (candidates, client reviewers), RBAC policy (AccessPolicy).
  repository/   Spring Data JPA repositories.
  domain/       JPA entities.
  dto/          Request/response shapes — never expose domain/ entities
                over the API.
  config/       Spring configuration (datasource, HTTP clients, CORS, storage).
```

`controller` → `service` → (`client` | `repository` | enqueue a `task`).
`client` and `repository` never call back up into `service`. `domain` has
no dependency on any other package.

| Service group | Owns | Notes |
| --- | --- | --- |
| `ClientService` | Client, ClientContact, per-client templates, commercial terms | |
| `RequisitionService` | Requisition, approvals | |
| `JobService` | Job, PipelineStage, HiringTeam, templates | |
| `CandidateService` | Candidate, Document, dedupe/merge, parsing | |
| `ApplicationService` | Application, StageEvent, stage-move rules | The only place stage transitions are validated |
| `InterviewService` | Interview, Scorecard, scheduling | |
| `AssessmentService` | Assessment, provider adapters | |
| `SubmissionService` | Submission, snapshot, AM approval, duplicate-submission guard, client review links | Only path by which candidate data reaches a client |
| `OfferService` | Offer, OfferApproval, e-sign, client-offer tracking | |
| `PlacementService` | Placement, guarantee period, invoicing export | v1 |
| `MessagingService` | Message, templates, email | |
| `ReportService` | Read-only aggregates over StageEvent etc. | |
| `AuditService` | AuditLog (append-only) | |
| `LlmService` | CV parsing, JD drafting, feedback summaries | Logs every call; never makes a decision |

RBAC, job scoping and client scoping are checked through one
`AccessPolicy` in `security/`, called from services — not re-implemented
per controller. Client Reviewer principals carry a single `client_id` and
may read only `Submission` snapshots for that client; they never load
`Candidate`, internal `Scorecard`s or notes directly (ADR-0002).

## Core data model

UUID primary keys (`BINARY(16)` on MySQL). Semi-structured fields are
MySQL `JSON` columns.

| Entity | Key fields | Notes |
| --- | --- | --- |
| Client | name, industry, account_manager_id, confidential_default, default_pipeline_template_id, submission_guard_months, status | |
| ClientContact | client_id, name, email, role, can_review | Becomes a Client Reviewer principal via magic link |
| Requisition | hiring_type, client_id (nullable), project, role, level, headcount, budget_min/max, bill_rate, target_date, status, requested_by | hiring_type: internal / client_deployed / direct_placement |
| Job | requisition_id, client_id (nullable), hiring_type, confidential, public_employer_label, title, jd, skills, location, work_mode, status, pipeline_template_id | draft / open / on_hold / closed |
| PipelineStage | job_id, name, position, type, sla_hours | `type` drives automation |
| Candidate | name, email (unique), phone, links, current_ctc, expected_ctc, notice_days, source, tags, consent_at | FULLTEXT on name/skills |
| Application | candidate_id, job_id, stage_id, status, owner_id, applied_at, rejected_reason | active / rejected / withdrawn / hired |
| StageEvent | application_id, from_stage, to_stage, actor_id, reason, at | Append-only |
| Interview | application_id, stage_id, start_at, end_at, meeting_url, status | Panel via join table |
| Scorecard | interview_id, interviewer_id, ratings (JSON), recommendation, notes, submitted_at | |
| Assessment | application_id, provider, external_id, score, max_score, report_url, status | |
| Offer | application_id, ctc_breakdown (JSON), joining_date, expires_at, status, signed_doc_id | |
| OfferApproval | offer_id, approver_id, decision, at | |
| Submission | application_id, client_id, submitted_by, approved_by, snapshot (JSON), cv_document_id, shown_rate (v1), status, client_decision, client_reason, sent_at, decided_at | Immutable snapshot of what the client saw |
| ClientReviewLink | client_contact_id, submission_ids, token_hash, expires_at, revoked_at | Single-client, expiring, revocable |
| ClientFeedback | submission_id, client_contact_id, round, decision, notes, at | Client-side feedback, separate from internal Scorecards |
| Placement | application_id, client_id, start_date, guarantee_end, replacement_of, status | v1 |
| Document | owner_type, owner_id, kind, storage_key, parsed (JSON) | Private bucket; FULLTEXT on parsed text |
| Message | candidate_id, channel, direction, subject, body, thread_id, sent_at | |
| AppUser | email, name, role, google_subject | |
| AuditLog | actor_id, entity, entity_id, action, diff (JSON), at | Append-only |
| BackgroundTask | type, payload (JSON), status, attempts, run_after, idempotency_key | Polled by workers |

## REST surface (`/api/v1`)

- `/clients`, `/clients/{id}/contacts`
- `/requisitions`, `/jobs`, `/jobs/{id}/pipeline`
- `/submissions`, `/submissions/{id}/approve`, `/submissions/{id}/send`
- `/candidates`, `/candidates/{id}/documents`, `/applications`, `/applications/{id}/move`
- `/interviews`, `/scorecards`, `/assessments`, `/offers`, `/offers/{id}/approve`
- `/reports/funnel`, `/reports/time-to-hire`
- `/public/apply` — unauthenticated, rate-limited (careers pages post here)
- `/review/{token}` — client review page + feedback, scoped to the link's client and submissions
- `/webhooks/{provider}` — signature-verified, idempotent

## Integrations

| Integration | Purpose | Phase |
| --- | --- | --- |
| Google Calendar / Microsoft 365 | Free-busy, invites, Meet/Teams links | MVP |
| Gmail / Outlook (or SES / SendGrid) | Outbound + two-way thread sync | MVP |
| Coding-test provider (HackerRank / Codility / HackerEarth) | Invites, scores, reports | MVP |
| E-sign (DocuSign / Zoho Sign / Leegality) | Offer signing | MVP |
| Slack | Notifications | v1 |
| LinkedIn, Naukri, Indeed | Posting + applicant import | v1 |
| HRMS (Keka / Darwinbox / Zoho People) | Hired hand-off | v1 |
