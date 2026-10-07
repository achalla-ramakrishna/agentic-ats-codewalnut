# Pipeline

| | |
| --- | --- |
| **ID prefix** | PIPE |
| **Status** | Ready |
| **Chunk** | 2 (referrals v1) |
| **Owner** | TBD |
| **Related** | [candidates.md](candidates.md), [interviews-and-scorecards.md](interviews-and-scorecards.md), [client-submissions.md](client-submissions.md) |
| **Last updated** | 2026-10-04 |

## Summary

Each application (one candidate × one job) moves through the job's stages until
hired/placed, rejected or withdrawn. The board is the Recruiter's main working
view, and every move is recorded for reporting.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Move candidates, reject, bulk-action, work the board |
| Hiring Manager / Account Manager | Review shortlists, move candidates in their stages |

## User stories

- **PIPE-S1** As a Recruiter, I want a board per job with candidates in stage
  columns so that I see where everyone is at a glance.
- **PIPE-S2** As a Recruiter, I want to reject 20 candidates at once with a
  reason and a polite email so that I close loops quickly.
- **PIPE-S3** As a Hiring Manager, I want to see only the candidates waiting on
  me so that I don't slow the process down.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| PIPE-01 | Default stage templates: **Internal** — Applied/Sourced → Recruiter screen → Coding assessment → Technical interviews → HM/culture round → Offer → Hired. **Client** — Applied/Sourced → Recruiter screen → Coding assessment → CodeWalnut tech interview → Submitted to client → Client interviews → Client decision → Offer → Placed. | MVP |
| PIPE-02 | Templates editable by Admin; per-client default template; per-job stage edits (add/remove/reorder) before candidates enter. | MVP |
| PIPE-03 | Kanban board and list view per job; card shows name, current title, experience, days in stage, owner, flags. | MVP |
| PIPE-04 | Drag-and-drop or menu to move stage; bulk move, bulk reject, bulk email. | MVP |
| PIPE-05 | Every move writes a `StageEvent` (from, to, actor, time, reason). | MVP |
| PIPE-06 | Reject requires a reason from a configurable list; for client jobs also "rejected by CodeWalnut" or "rejected by client". Optional templated email, sent now or after a delay (default 24 h, cancellable). | MVP |
| PIPE-07 | Other exits: Withdrawn (candidate's choice, reason) and On hold; both reversible. | MVP |
| PIPE-08 | Stage rules enforced by the API: cannot leave Technical interviews with missing scorecards; cannot enter Submitted to client except via a submission; cannot enter Offer without a decision. | MVP |
| PIPE-09 | Per-stage SLA (hours); cards over SLA are flagged; owners get a daily digest. | MVP |
| PIPE-10 | "My queue": candidates waiting on the signed-in user across jobs. | MVP |
| PIPE-11 | Move an application to a different job (keeps history). | MVP |
| PIPE-12 | Employee referrals: referrer submits a candidate, sees status, bonus eligibility tracked. | v1 |
| PIPE-13 | The candidate panel lists every opening the candidate is in, across clients, with client and stage, linking to each. | Done |
| PIPE-14 | **Add to another opening** (recruiters and admins): put the same candidate forward for another opening, e.g. another client's, at a chosen active stage (default Applied / Sourced) with an optional note. One candidate record, so profile, résumés and documents carry over; stage, notes, tests and interviews stay per opening. Both openings' history records the link ("Also put forward for…", "Added from…"); audited. Refused if already in that opening or it is closed. Unlike PIPE-11, the original application stays. | Done |
| PIPE-15 | **Seven stages**, in this order: Applied / Sourced, Interviewed, Shortlisted, Offer sent, Joined, plus On hold and Rejected. Screening is part of Applied / Sourced; Submitted to client and Client interview are part of Shortlisted; Selected and Offer accepted are part of Offer sent; Withdrawn is Rejected with the reason. The retired stages are never offered; anything that still sends one (old spreadsheets, links, AI wording) lands in the stage that replaced it. Existing candidates were moved accordingly (V23); their history keeps the old stage names. Admin updates go out for Shortlisted, Offer sent and Joined. | Done |

## Business rules

- One active application per candidate per job.
- `StageEvent` is append-only and is the source for all funnel metrics.

## Edge cases & failure states

- Two people move the same card at once → the second move is refused with the
  current stage shown (optimistic locking).
- Rejection email scheduled, then the rejection is undone → email is cancelled.

## Acceptance criteria

- **PIPE-AC1** Given a reject without a reason, then the API refuses the move.
- **PIPE-AC2** Given an interview stage with one scorecard missing, when moving
  forward, then the API refuses and names the missing interviewer.
- **PIPE-AC3** Given any move, then a `StageEvent` exists and the funnel report
  reflects it.
- **PIPE-AC4** Given a board with 500 candidates, then it loads in < 1.5 s (p95).

## Data

`Application`, `PipelineStage`, `PipelineTemplate`, `StageEvent`, `RejectionReason`.

## API (planned)

`GET /jobs/{id}/pipeline`, `POST /applications`, `POST /applications/{id}/move`,
`POST /applications/bulk`, `GET /me/queue`.

## Change log

| Date | Change |
| --- | --- |
| 2026-10-07 | PIPE-15: seven stages instead of thirteen (ADR-0024) |
| 2026-10-04 | PIPE-13, PIPE-14: see all of a candidate's openings and add them to another client's opening |
| 2026-09-25 | Created from SPEC.md |
