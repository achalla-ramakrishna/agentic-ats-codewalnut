# Clients

| | |
| --- | --- |
| **ID prefix** | CLI |
| **Status** | Ready |
| **Chunk** | 1 |
| **Owner** | TBD |
| **Related** | [client-submissions.md](client-submissions.md), [requisitions.md](requisitions.md), ADR-0002 |
| **Last updated** | 2026-09-25 |

## Summary

CodeWalnut hires for its clients as well as itself. A client is a record inside
CodeWalnut's ATS — not a separate tenant — owned by an Account Manager, with
its contacts, defaults and (from v1) commercial terms.

## Users

| Role | Uses this feature to |
| --- | --- |
| Account Manager | Create and maintain their clients and client contacts |
| Admin | Manage any client; reassign Account Managers |
| Recruiter | View clients they recruit for |

## User stories

- **CLI-S1** As an Account Manager, I want to add a new client with its contacts
  so that I can raise hiring requests for it.
- **CLI-S2** As an Account Manager, I want to set a client's default pipeline and
  whether its jobs are confidential so that every job for them starts right.
- **CLI-S3** As a Recruiter, I want to see a client's open roles and past
  submissions so that I don't re-submit the same people.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| CLI-01 | Client record: name (unique), industry, website, account manager, status (active / inactive), internal notes. | MVP |
| CLI-02 | Client contacts: name, email, title, and a `can_review` flag that allows them to receive submission review links. | MVP |
| CLI-03 | Per-client defaults: pipeline template, scorecard template, confidential-by-default flag, public employer label (e.g. "a US fintech client"). | MVP |
| CLI-04 | Per-client duplicate-submission guard period (default 6 months). | MVP |
| CLI-05 | Client page shows open jobs, submissions and placements for that client. | MVP |
| CLI-06 | Only Admin and the client's Account Manager can edit a client; Recruiters on that client's jobs can view it. | MVP |
| CLI-07 | Deactivating a client blocks new requisitions; existing jobs continue. | MVP |
| CLI-08 | Commercial terms: engagement model (deployed / direct placement / both), rate card or fee %, replacement guarantee period. | v1 |
| CLI-09 | Per-client data rules: NDA-protected JDs, own retention period, special handling where a contract requires it. | v1 |

## Business rules

- A client contact's email must be unique within that client.
- Client names never appear on public pages for confidential jobs.

## Edge cases & failure states

- Account Manager deactivated → their clients show "no owner" and Admins are
  prompted to reassign.
- A contact with pending review links is removed → their links are revoked.

## Acceptance criteria

- **CLI-AC1** Given a Recruiter not on any of Client A's jobs, when they open
  Client A, then `403`.
- **CLI-AC2** Given Client A has confidential-by-default on, when a job is
  created for it, then the job is confidential with the client's public label.
- **CLI-AC3** Given a client is deactivated, when an Account Manager raises a
  requisition for it, then it is refused.

## Data

`Client`, `ClientContact` (see `docs/architecture.md`).

## API (planned)

`GET/POST /clients`, `GET/PATCH /clients/{id}`, `GET/POST /clients/{id}/contacts`,
`PATCH/DELETE /clients/{id}/contacts/{contactId}`.

## Out of scope

Clients running their own hiring in the ATS (multi-tenant), invoicing.

## Open questions

- [ ] Any client needing special data handling? *(v1)*

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
