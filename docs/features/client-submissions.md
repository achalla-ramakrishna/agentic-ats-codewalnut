# Client submissions & review

| | |
| --- | --- |
| **ID prefix** | SUB |
| **Status** | Ready |
| **Chunk** | 6 (client portal v1) |
| **Owner** | TBD |
| **Related** | [clients.md](clients.md), [pipeline.md](pipeline.md), [offers-and-placements.md](offers-and-placements.md), ADR-0002 |
| **Last updated** | 2026-09-25 |

## Summary

For client jobs, CodeWalnut sends a shortlist to the client as **submissions**:
a branded, redacted snapshot of each candidate. The client reviews through a
secure link — no account needed — and their decisions and interview feedback
flow back into the pipeline.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Build submissions, chase client feedback |
| Account Manager | Approve submissions before they go out; own the client relationship |
| Client Reviewer (external) | Review candidates, shortlist/reject, give feedback, propose interview slots |

## User stories

- **SUB-S1** As a Recruiter, I want to build a submission in a few clicks from the
  candidate's profile so that shortlists go out the same day.
- **SUB-S2** As an Account Manager, I want to approve every submission so that
  nothing reaches the client that I haven't seen.
- **SUB-S3** As a client hiring manager, I want to review candidates and give
  feedback from one link so that I don't need another login.
- **SUB-S4** As a Recruiter, I want to be warned if a candidate was already sent
  to this client so that we avoid ownership disputes.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| SUB-01 | Build a submission: CodeWalnut-branded profile, recruiter summary, skills, experience, assessment summary, availability/notice, and the CV with contact details removed. | MVP |
| SUB-02 | Email, phone and profile links are stripped from the profile and the CV by default. | MVP |
| SUB-03 | A submission stores an immutable snapshot of exactly what the client saw (profile fields, CV version); later profile edits don't change it. | MVP |
| SUB-04 | Account Manager approval is required before a submission is sent. | MVP |
| SUB-05 | Candidate consent to be submitted to that named client is recorded before sending. | MVP |
| SUB-06 | Duplicate-submission guard: warn and require a reason if the candidate was submitted to the same client within the client's guard period (default 6 months), via any job. | MVP |
| SUB-07 | Send one or several submissions to a client contact with `can_review`: email with a secure review link. | MVP |
| SUB-08 | Review link: single client, specific submissions, expires (default 14 days), revocable, re-issuable; no account or password. | MVP |
| SUB-09 | On the review page the client can shortlist or reject (with reason), leave feedback, and propose interview slots. | MVP |
| SUB-10 | Client decisions move the application (Submitted → Client interviews, or Rejected "by client"); the Recruiter is notified. | MVP |
| SUB-11 | Client interview rounds and outcomes recorded as client feedback, by the Client Reviewer via the link or by the Recruiter on their behalf. | MVP |
| SUB-12 | Reminders if no client response after 2 and 5 working days, then the Account Manager is alerted; submissions never auto-reject. | MVP |
| SUB-13 | Every review-link view and action is audited with the contact and time. | MVP |
| SUB-14 | MVP submissions show no bill rate or expected CTC; commercials are shared outside the ATS. | MVP |
| SUB-15 | Per-client choice of showing bill rate, expected CTC, or neither. | v1 |
| SUB-16 | Full client portal: client users see all their jobs, submissions, interviews and history. | v1 |

## Business rules

- A Client Reviewer can only ever see submissions sent to their own client —
  never other clients, internal notes, internal scorecards or unsubmitted
  candidates.
- Withdrawing consent withdraws pending submissions to that client and removes
  them from the review page.

## Edge cases & failure states

- Expired or revoked link → "this link has expired, contact your CodeWalnut
  account manager"; no data shown.
- Forwarded link used by someone else → works only within the same client scope;
  every access is audited; the Account Manager can revoke it.
- Candidate withdraws after submission → the client sees "no longer available".

## Acceptance criteria

- **SUB-AC1** Given a sent submission, then the client-facing profile and CV
  contain no candidate email, phone or profile links.
- **SUB-AC2** Given a candidate submitted to Client A 2 months ago, when
  submitted to Client A again (any job), then it is blocked until a reason is
  entered.
- **SUB-AC3** Given a review link for Client A, when it is used to request any
  Client B resource, then the request is refused and audited.
- **SUB-AC4** Given the candidate's profile is edited after sending, then the
  client still sees the original snapshot.
- **SUB-AC5** Given a submission not yet approved by the Account Manager, then it
  cannot be sent.

## Data

`Submission`, `ClientReviewLink`, `ClientFeedback`.

## API (planned)

`POST /submissions`, `POST /submissions/{id}/approve`, `POST /submissions/send`,
`POST /review-links/{id}/revoke`; public: `GET /review/{token}`,
`POST /review/{token}/decisions`.

## Open questions

- [ ] Guard period per client other than 6 months?
- [ ] Should rejected-by-client reasons be a fixed list?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md; review link confirmed as enough for MVP, commercials deferred to v1 |
