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
| PIPE-16 | **Import from Naukri** (recruiters and admins): upload Naukri's applicant Excel (.xlsx, or .csv) on an opening. Every row is ranked against the job description: by AI when it is set up (batches of 20, up to 400 rows), otherwise by rules (opening's skills in headline/key skills/designation, years asked, title words, location, notice). Each row shows match %, a one-line reason, what's good and what to check, and whether the person is already in the ATS or this opening. The top 10 that can be added start ticked; Top 10/20/50/All change the choice. Only the ticked rows become candidates (Naukri details as the first note, source NAUKRI); the file and other rows are not stored. Then one editable *Ask for résumé* email goes to each added person from the recruiter's Gmail; replies show in Messages and Workflow → Waiting for our reply, and staff schedule interviews from there. Gender, date of birth and home town columns are never read (ADR-0026). | Done |
| PIPE-17 | **More columns in an opening's candidate list**: Experience, Current role, Key skills, AI summary and Notice, next to Match. Values come from the AI résumé reading when there is one, otherwise from the job-portal import (stored on the application as `portal_profile`; Match then shows the import ranking). Long text stays two lines (hover for all), so rows keep an even height. Email and phone sit under the name; Stage comes right after Match; Updated is off by default. *Columns* chooses which optional columns show, remembered per browser. The search box also finds portal skills and current role. Candidates imported from Naukri before this change get their details from their first note at start-up. | Done |
| PIPE-18 | **Batches for bulk email**: Import from Naukri and Send to candidates have a *Batch size* (default 30, 1–200, remembered per browser). *Select top N* / *Select next N* ticks one batch; people already added or already sent the test are skipped, so repeating picks the next batch. Emails still go one per person from the sender's Gmail (about 2,000 a day on Google Workspace). | Done |
| PIPE-19 | **Email from an opening's list, a page at a time** (recruiters and admins): the list shows one page of *Per page* candidates (default 30, remembered per browser), best match first until another sort is chosen; *Previous / Next* move between pages and any search, filter or sort change returns to page 1. *Select this page* (or the tick box in the header) ticks everyone on the page who has an email and isn't Rejected; *Hide people already emailed* drops anyone emailed from this opening, so page 1 is always the next people to contact. *Email selected* opens one editable email (any template, default *Ask for résumé*) sent one by one from the sender's Gmail, best match first. *Emailed 11 Oct* shows under each person's contact details (`lastEmailedAt` in the opening's list). | Done |
| PIPE-20 | **Résumé uploaded filter and WhatsApp selected**: a *Résumé uploaded* chip lists only people with an original résumé on file (e.g. those who replied with one). *WhatsApp selected* opens a panel with an editable message (default asks for interview slots; *[day, time]* must be filled in first) and one button per person. Without the WhatsApp Business API each click opens WhatsApp with the message ready and the recruiter presses Send; with the API it sends. Every message is saved in the candidate's conversation. Meant for people who already replied, never cold bulk outreach from a personal number. | Done |
| PIPE-22 | **Email from the Workflow page, tab by tab**: tick candidates (or *Select first N*, batch size default 30; only people with an email who aren't closed) and *Email selected*. The email panel opens with the template that fits the tab: *Waiting for our reply* → *Thanks for résumé – interview slots*; *Never contacted* → *Ask for résumé*; *No contact for 7+ days* → *Follow-up*; others → blank. Works across openings: `{jobTitle}` is filled per person. Email rather than WhatsApp for bulk, to avoid WhatsApp blocking new numbers. | Done |
| PIPE-23 | **Workflow by stage, with the emails each person got**: every email sent from a template records the template (`message.template_key`; tests record *test_invite* / *test_reminder*). The Workflow page adds stage chips (All, Applied / Sourced, Interviewed, Shortlisted, Offer sent, Joined, On hold, Rejected) above the follow-up tabs, with counts that follow each other; an *Emails sent* column with one chip per template and date; and a filter *No “X” yet* / *Got “X”*. Bulk email suggests the template from that filter, the tab, or the stage (Applied → résumé request, Shortlisted → shortlisted, Offer sent → selected, Rejected → rejected). Sending *Shortlisted* or *Selected* can also move the person to Shortlisted / Offer sent (ticked by default). | Done |

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
| 2026-10-12 | PIPE-23: Workflow by stage; emails remember their template; "No X yet" filter |
| 2026-10-12 | PIPE-22: bulk email from Workflow tabs with a matching template |
| 2026-10-11 | PIPE-20: Résumé uploaded filter; WhatsApp selected candidates one click each |
| 2026-10-11 | PIPE-19: pages of 30 ranked by match; select a page and email it |
| 2026-10-11 | PIPE-18: batch size (default 30) for bulk emails |
| 2026-10-11 | PIPE-17: experience, current role, key skills, AI summary and notice columns; even row heights; column chooser |
| 2026-10-11 | PIPE-16: import a Naukri applicant export, ranked against the job description, then ask for résumés by email (ADR-0026) |
| 2026-10-07 | PIPE-15: seven stages instead of thirteen (ADR-0024) |
| 2026-10-04 | PIPE-13, PIPE-14: see all of a candidate's openings and add them to another client's opening |
| 2026-09-25 | Created from SPEC.md |
