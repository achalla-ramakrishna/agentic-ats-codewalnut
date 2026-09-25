# Interviews & scorecards

| | |
| --- | --- |
| **ID prefix** | INT |
| **Status** | Ready |
| **Chunk** | 4 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [client-submissions.md](client-submissions.md), [ai-assistance.md](ai-assistance.md) |
| **Last updated** | 2026-09-25 |

## Summary

Schedule interviews against real calendars, let candidates pick slots, and
collect structured, rubric-based feedback that must be in before a decision.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Schedule, reschedule, chase feedback |
| Interviewer | See assigned interviews, submit scorecards |
| Hiring Manager | Run the debrief and decide |
| Candidate | Book or reschedule slots |

## User stories

- **INT-S1** As a Recruiter, I want to offer a candidate slots where the whole
  panel is free so that scheduling takes one email, not five.
- **INT-S2** As an Interviewer, I want the candidate profile, the scorecard and
  the video link in one place so that I'm ready in two minutes.
- **INT-S3** As a Hiring Manager, I want all scorecards side by side at the
  debrief so that I decide on evidence.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| INT-01 | Schedule an interview for an application: stage, duration, panel, type (video / in person / phone); free-busy lookup across the panel's calendars. | MVP |
| INT-02 | Candidate self-booking: send a link with available slots; booking creates calendar invites with a Meet/Teams link. | MVP |
| INT-03 | Reschedule and cancel, from either side, with notifications. | MVP |
| INT-04 | Scorecard template per stage: competencies with 1–4 ratings and guidance, notes, overall recommendation (strong no / no / yes / strong yes). | MVP |
| INT-05 | Interviewers see other panel feedback only after submitting their own. | MVP |
| INT-06 | Feedback reminders at 2 h and 24 h after the interview; overdue feedback flagged to the Recruiter. | MVP |
| INT-07 | Debrief view: all scorecards for the application side by side, with assessment results. | MVP |
| INT-08 | Interviewer sees only candidates they are assigned to, without contact details or CTC. | MVP |
| INT-09 | Client interview rounds recorded as client feedback (not internal scorecards), via the client review link or by the Recruiter (see [client-submissions.md](client-submissions.md)). | MVP |
| INT-10 | Panel rotation and interviewer load-balancing. | v1 |
| INT-11 | AI summary of scorecards for the debrief, labelled as AI (see [ai-assistance.md](ai-assistance.md)). | v1 |

## Business rules

- A scorecard can be edited by its author until the stage is left; changes after
  submission are audited.
- Interviewer load limit per week is configurable (v1).

## Edge cases & failure states

- Calendar API down → scheduling shows slots as unavailable and allows manual
  time entry; invites are retried.
- Panel member declines → Recruiter alerted to replace them.
- Candidate no-show → recorded as an outcome; no scorecard required.

## Acceptance criteria

- **INT-AC1** Given an interviewer who hasn't submitted, when they open the
  debrief, then peer feedback is hidden.
- **INT-AC2** Given an Interviewer requests an unassigned candidate, then `403`
  and an audit entry.
- **INT-AC3** Given an interview ended 2 h ago with no scorecard, then the
  interviewer has been reminded.

## Data

`Interview`, `InterviewPanelMember`, `Scorecard`, `ScorecardTemplate`.

## API (planned)

`GET/POST /interviews`, `PATCH /interviews/{id}`, `GET /interviews/mine`,
`POST /interviews/{id}/scorecards`, `GET /applications/{id}/debrief`,
public: `GET/POST /book/{token}`.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
