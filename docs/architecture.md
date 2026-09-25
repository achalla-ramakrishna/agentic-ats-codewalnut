# Architecture

Nothing is built yet; this is the target shape. Stack choice and
alternatives are recorded in `docs/adr/0001-initial-architecture.md`.

## Repo overview

```
agentic-ats-codewalnut/
  backend/   Spring Boot 3 (Java 21) API + server-rendered careers pages
  frontend/  React + TypeScript (Vite) app for staff and candidate portal
  docs/      spec, architecture, ADRs
```

## Shape

```
                 ┌──────────────────────────┐
 Recruiters,     │  React SPA (frontend/)    │──┐
 HMs, panel ───▶ │  staff UI + cand. portal  │  │  /api/v1
                 └──────────────────────────┘  │
                                               ▼
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
  security/     Spring Security config, Google OAuth2 login, candidate
                magic links, RBAC policy (AccessPolicy).
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
| `RequisitionService` | Requisition, approvals | |
| `JobService` | Job, PipelineStage, HiringTeam, templates | |
| `CandidateService` | Candidate, Document, dedupe/merge, parsing | |
| `ApplicationService` | Application, StageEvent, stage-move rules | The only place stage transitions are validated |
| `InterviewService` | Interview, Scorecard, scheduling | |
| `AssessmentService` | Assessment, provider adapters | |
| `OfferService` | Offer, OfferApproval, e-sign | |
| `MessagingService` | Message, templates, email | |
| `ReportService` | Read-only aggregates over StageEvent etc. | |
| `AuditService` | AuditLog (append-only) | |
| `LlmService` | CV parsing, JD drafting, feedback summaries | Logs every call; never makes a decision |

RBAC and job scoping are checked through one `AccessPolicy` in
`security/`, called from services — not re-implemented per controller.

## Core data model

UUID primary keys (`BINARY(16)` on MySQL). Semi-structured fields are
MySQL `JSON` columns.

| Entity | Key fields | Notes |
| --- | --- | --- |
| Requisition | role, level, headcount, budget_min/max, client_project, target_date, status, requested_by | |
| Job | requisition_id, title, jd, skills, location, work_mode, status, pipeline_template_id | draft / open / on_hold / closed |
| PipelineStage | job_id, name, position, type, sla_hours | `type` drives automation |
| Candidate | name, email (unique), phone, links, current_ctc, expected_ctc, notice_days, source, tags, consent_at | FULLTEXT on name/skills |
| Application | candidate_id, job_id, stage_id, status, owner_id, applied_at, rejected_reason | active / rejected / withdrawn / hired |
| StageEvent | application_id, from_stage, to_stage, actor_id, reason, at | Append-only |
| Interview | application_id, stage_id, start_at, end_at, meeting_url, status | Panel via join table |
| Scorecard | interview_id, interviewer_id, ratings (JSON), recommendation, notes, submitted_at | |
| Assessment | application_id, provider, external_id, score, max_score, report_url, status | |
| Offer | application_id, ctc_breakdown (JSON), joining_date, expires_at, status, signed_doc_id | |
| OfferApproval | offer_id, approver_id, decision, at | |
| Document | owner_type, owner_id, kind, storage_key, parsed (JSON) | Private bucket; FULLTEXT on parsed text |
| Message | candidate_id, channel, direction, subject, body, thread_id, sent_at | |
| AppUser | email, name, role, google_subject | |
| AuditLog | actor_id, entity, entity_id, action, diff (JSON), at | Append-only |
| BackgroundTask | type, payload (JSON), status, attempts, run_after, idempotency_key | Polled by workers |

## REST surface (`/api/v1`)

- `/requisitions`, `/jobs`, `/jobs/{id}/pipeline`
- `/candidates`, `/candidates/{id}/documents`, `/applications`, `/applications/{id}/move`
- `/interviews`, `/scorecards`, `/assessments`, `/offers`, `/offers/{id}/approve`
- `/reports/funnel`, `/reports/time-to-hire`
- `/public/apply` — unauthenticated, rate-limited (careers pages post here)
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
