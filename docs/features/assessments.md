# Coding assessments

| | |
| --- | --- |
| **ID prefix** | ASMT |
| **Status** | Draft |
| **Chunk** | 5 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md) |
| **Last updated** | 2026-09-25 |

## Summary

A coding assessment is CodeWalnut's main screening signal, so it is a built-in
stage: send a test from a provider or a take-home assignment, pull back the
score, and advance or flag automatically.

## Users

Recruiters send and review; Hiring Managers and interviewers read results.

## User stories

- **ASMT-S1** As a Recruiter, I want to send the right test for the role in one
  click so that screening doesn't stall.
- **ASMT-S2** As a Recruiter, I want candidates above the threshold advanced
  automatically so that good people move fast.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| ASMT-01 | Per job: assessment type (provider test or take-home), test id or assignment brief, pass threshold, deadline (days). | MVP |
| ASMT-02 | Provider integration (one of HackerRank / Codility / HackerEarth): send invite, receive score and report link via signed webhook. | MVP |
| ASMT-03 | Take-home: send brief and a submission link; candidate submits a GitHub repo URL; reviewer scores against a rubric. | MVP |
| ASMT-04 | Reminders at 24 h and 48 h before deadline; expired invites flagged. | MVP |
| ASMT-05 | Score ≥ threshold → auto-advance (configurable) or flag for review; below → flag, never auto-reject. | MVP |
| ASMT-06 | Result (score, max, percentile if given, report link) shown on the application and in the debrief. | MVP |
| ASMT-07 | Plagiarism / proctoring flags from the provider shown if available. | v1 |

## Business rules

- Auto-reject on score is not allowed; a person decides.
- Webhooks are idempotent by provider event id.

## Edge cases & failure states

- Webhook never arrives → poll the provider daily for pending invites.
- Candidate asks for an extension → Recruiter extends deadline; audited.

## Acceptance criteria

- **ASMT-AC1** Given a score above threshold with auto-advance on, then the
  application moves to the next stage with a `StageEvent` by "system".
- **ASMT-AC2** Given a replayed webhook, then no duplicate result is stored.

## Data

`Assessment`, `AssessmentConfig` (per job).

## API (planned)

`POST /applications/{id}/assessments`, `GET /assessments/{id}`,
`POST /webhooks/{provider}`.

## Open questions

- [ ] Which assessment tool is used today, if any?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
