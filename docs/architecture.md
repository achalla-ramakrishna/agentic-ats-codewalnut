# Architecture

Proposed; nothing is built yet. Stack choice and alternatives are recorded
in `docs/adr/0001-initial-architecture.md`.

## Shape

A TypeScript modular monolith: one web app, one API, Postgres, and a
background job queue. Sized for a 3–6 engineer team and internal-tool
load; modules have clean boundaries so one can be split out later if it
earns it.

```
                 ┌───────────────────────┐
 Recruiters,     │  Next.js web app       │
 HMs, panel ───▶ │  (staff UI)            │──┐
                 └───────────────────────┘  │
                 ┌───────────────────────┐  │   ┌──────────────┐    ┌────────────┐
 Candidates ───▶ │  Careers page +        │──┼──▶│  API          │──▶│ PostgreSQL │
                 │  candidate portal      │  │   │  (modules     │    └────────────┘
                 └───────────────────────┘  │   │   below)      │──▶ Object storage
                                            │   └──────┬───────┘    (CVs, offers)
 Providers  ── webhooks ───────────────────┘          │ enqueue
 (assessment, e-sign, email)                  ┌───────▼───────┐
                                              │ Job queue      │──▶ Calendar, email,
                                              │ BullMQ + Redis │    assessment, e-sign,
                                              └───────────────┘    LLM, Slack, HRMS
```

## Planned repo layout

```
agentic-ats-codewalnut/
  apps/web/        Next.js (staff UI + careers page + candidate portal)
  apps/api/        Node API (NestJS) — or tRPC inside apps/web if the team stays small
  apps/worker/     BullMQ workers (emails, reminders, parsing, webhooks, retention)
  packages/db/     Schema + migrations (Prisma or Drizzle), seed data
  packages/shared/ Types, zod schemas, RBAC policy shared by web/api/worker
  docs/            Spec, architecture, ADRs
```

## API modules

| Module | Owns | Notes |
| --- | --- | --- |
| `auth` | SSO (staff), magic links (candidates), sessions | |
| `rbac` | Role + job-scope policy checks | Every other module calls this; never re-implement checks in a controller |
| `requisitions` | Requisition, approvals | |
| `jobs` | Job, PipelineStage, HiringTeam, templates | |
| `candidates` | Candidate, Document, dedupe/merge, parsing | |
| `applications` | Application, StageEvent, stage-move rules | Single place stage transitions are validated |
| `interviews` | Interview, Scorecard, scheduling | |
| `assessments` | Assessment, provider adapters | |
| `offers` | Offer, OfferApproval, e-sign | |
| `messaging` | Message, templates, email sync | |
| `reports` | Read-only aggregates over StageEvent etc. | |
| `audit` | AuditLog (append-only) | |
| `integrations` | One adapter per external provider | Nothing else calls provider APIs directly |
| `ai` | LLM calls behind one interface | Logs every call; never makes a decision |

Dependency direction: `controllers → services → (repositories | integrations)`.
Integrations and repositories never call back up into services.

## Core data model

| Entity | Key fields | Notes |
| --- | --- | --- |
| Requisition | role, level, headcount, budget_min/max, client_project, target_date, status, requested_by | |
| Job | requisition_id, title, jd, skills[], location, work_mode, status, pipeline_template_id | draft / open / on_hold / closed |
| PipelineStage | job_id, name, order, type, sla_hours | `type` drives automation |
| Candidate | name, email (unique per org), phone, links, current_ctc, expected_ctc, notice_days, source, tags[], consent_at | |
| Application | candidate_id, job_id, stage_id, status, owner_id, applied_at, rejected_reason | active / rejected / withdrawn / hired |
| StageEvent | application_id, from_stage, to_stage, actor_id, reason, at | Append-only |
| Interview | application_id, stage_id, start_at, end_at, panel[], meeting_url, status | |
| Scorecard | interview_id, interviewer_id, ratings (jsonb), recommendation, notes, submitted_at | |
| Assessment | application_id, provider, external_id, score, max_score, report_url, status | |
| Offer | application_id, ctc_breakdown (jsonb), joining_date, expires_at, status, signed_doc_id | |
| OfferApproval | offer_id, approver_id, decision, at | |
| Document | owner_type, owner_id, kind, storage_key, parsed (jsonb) | Private bucket |
| Message | candidate_id, channel, direction, subject, body, thread_id, sent_at | |
| User | email, name, role, sso_subject | |
| AuditLog | actor_id, entity, entity_id, action, diff (jsonb), at | Append-only |

## REST surface (`/api/v1`)

- `/requisitions`, `/jobs`, `/jobs/{id}/pipeline`
- `/candidates`, `/candidates/{id}/documents`, `/applications`, `/applications/{id}/move`
- `/interviews`, `/scorecards`, `/assessments`, `/offers`, `/offers/{id}/approve`
- `/reports/funnel`, `/reports/time-to-hire`
- `/public/jobs`, `/public/apply` — unauthenticated, rate-limited
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
