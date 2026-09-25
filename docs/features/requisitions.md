# Requisitions

| | |
| --- | --- |
| **ID prefix** | REQ |
| **Status** | Ready |
| **Chunk** | 1 |
| **Owner** | TBD |
| **Related** | [jobs-and-careers-page.md](jobs-and-careers-page.md), [clients.md](clients.md) |
| **Last updated** | 2026-09-25 |

## Summary

A requisition is the approved request to hire. It fixes the hiring type,
headcount, budget and (for client roles) the client, before a job is opened.

## Users

| Role | Uses this feature to |
| --- | --- |
| Hiring Manager | Raise requisitions for internal roles |
| Account Manager | Raise requisitions from client requests |
| Approver | Approve or reject requisitions above thresholds |
| Recruiter | See approved requisitions and open jobs from them |

## User stories

- **REQ-S1** As a Hiring Manager, I want to request 2 senior React developers
  with a budget band so that recruiting can start once approved.
- **REQ-S2** As an Account Manager, I want to log a client's request for a tech
  lead as a direct placement so that the right process applies.
- **REQ-S3** As an Approver, I want to approve or reject requests with a comment
  so that hiring stays within plan.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| REQ-01 | Fields: hiring type (internal / client-deployed / direct placement), client (required for client types), project, role title, level, headcount, location / work mode, budget band (CTC) or bill rate, target start date, justification. | MVP |
| REQ-02 | Statuses: draft → pending approval → approved / rejected → filled / cancelled. | MVP |
| REQ-03 | Approval chain by configurable thresholds (e.g. level, headcount, budget); below threshold is auto-approved. | MVP |
| REQ-04 | Approvers are notified and approve/reject with a comment; the requester is notified of the outcome. | MVP |
| REQ-05 | An approved requisition can open one job (or several for multi-location roles). | MVP |
| REQ-06 | Headcount decrements as hires/placements are recorded; the requisition is `filled` at zero. | MVP |
| REQ-07 | Editing budget or headcount after approval sends it back for approval. | MVP |

## Business rules

- Hiring Managers raise internal requisitions; Account Managers raise client
  requisitions for their own clients.
- Budget and bill rate are compensation data: visible only to roles allowed to
  see compensation (see [auth-and-users.md](auth-and-users.md)).

## Edge cases & failure states

- Approver deactivated with pending items → items move to the next approver in
  the chain or to Admin.
- Client deactivated → pending requisitions for it are blocked from approval.

## Acceptance criteria

- **REQ-AC1** Given a requisition above threshold, when submitted, then it cannot
  open a job until every approver in the chain has approved.
- **REQ-AC2** Given an approved requisition, when its headcount is raised, then
  it returns to pending approval.
- **REQ-AC3** Given a client hiring type with no client selected, then the save
  is refused.

## Data

`Requisition`, `RequisitionApproval`.

## API (planned)

`GET/POST /requisitions`, `GET/PATCH /requisitions/{id}`,
`POST /requisitions/{id}/submit`, `POST /requisitions/{id}/approve`,
`POST /requisitions/{id}/reject`.

## Open questions

- [ ] Approval thresholds: who approves, above what CTC / level / headcount?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
