# Interviews & scorecards

| | |
| --- | --- |
| **ID prefix** | INT |
| **Status** | In progress (scheduling shipped) |
| **Chunk** | 4 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [client-submissions.md](client-submissions.md), [ai-assistance.md](ai-assistance.md) |
| **Last updated** | 2026-09-29 |

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

### First release: schedule on Google Calendar (shipped 2026-09-29)

A simpler first cut of INT-01 and INT-03, decided in ADR-0005.

| ID | Requirement | Priority |
| --- | --- | --- |
| INT-12 | Admins and Recruiters schedule an interview from the candidate's panel: title (the candidate sees it), date, time, duration (15–480 min), time zone (the browser's), optional interviewer emails (up to 10), and a message to the candidate (prefilled). | Done |
| INT-13 | The interview is created on the scheduler's own Google Calendar with a Google Meet link; Google emails the invitation to the candidate and interviewers. The ATS stores the Meet link, calendar link and event id. | Done |
| INT-14 | Staff connect their Google Calendar with scope `calendar.events` only; sign-in never asks for it and candidates can't start it. The token lives in the session only. Not connected or expired → "Connect Google Calendar", then back to the same candidate. | Done |
| INT-15 | The candidate must have an email; staff can edit a candidate's name, email and phone (emails unique; the audit log records which fields changed, not the values). | Done |
| INT-16 | Google fails or refuses → nothing is saved and a clear error is shown ("Nothing was sent"). | Done |
| INT-17 | Cancel (organiser only, optional internal reason) deletes the event and Google emails everyone. Scheduling and cancelling are added to the application history and the audit log. | Done |
| INT-18 | Interviews page and dashboard list upcoming interviews with Meet links; Interviewers see only interviews they are on and no candidate contact details. | Done |
| INT-19 | Candidates see their own upcoming interviews (title, time, Meet link) in their area; never the panel, organiser or notes. | Done |
| INT-20 | Reschedule in place (move the event instead of cancel + new). | v1 |

## Business rules

- A scorecard can be edited by its author until the stage is left; changes after
  submission are audited.
- Interviewer load limit per week is configurable (v1).

## Edge cases & failure states

- Calendar API down → scheduling shows slots as unavailable and allows manual
  time entry; invites are retried. (First release: the error is shown and
  nothing is saved; the recruiter tries again — INT-16.)
- Panel member declines → Recruiter alerted to replace them.
- Candidate no-show → recorded as an outcome; no scorecard required.

## Acceptance criteria

- **INT-AC4** Given a scheduled interview, then the candidate and each
  interviewer are attendees of a Google Calendar event with a Meet link and
  Google was asked to email them (`sendUpdates=all`).
- **INT-AC5** Given Google fails, then no interview is saved.
- **INT-AC6** Given an Interviewer not on a panel, then that interview is not
  in their list.

Tests: `InterviewFlowTest`, `GoogleCalendarClientTest`,
`GoogleSignInConfiguredTest` (backend); `InterviewsPanel.test.tsx` (frontend).

- **INT-AC1** Given an interviewer who hasn't submitted, when they open the
  debrief, then peer feedback is hidden.
- **INT-AC2** Given an Interviewer requests an unassigned candidate, then `403`
  and an audit entry.
- **INT-AC3** Given an interview ended 2 h ago with no scorecard, then the
  interviewer has been reminded.

## Data

`Interview`, `InterviewPanelMember`, `Scorecard`, `ScorecardTemplate`.

## API

Built: `GET /google/status`, `GET /google/connect?returnTo=`,
`GET /interviews` (upcoming), `GET/POST /applications/{id}/interviews`,
`POST /interviews/{id}/cancel`, `PATCH /candidates/{id}`,
`GET /candidate/interviews`.

Planned:

`GET/POST /interviews`, `PATCH /interviews/{id}`, `GET /interviews/mine`,
`POST /interviews/{id}/scorecards`, `GET /applications/{id}/debrief`,
public: `GET/POST /book/{token}`.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
| 2026-09-29 | "Connect Google Calendar" became "Connect Google (Calendar & Gmail)" (`/google/status`, `/google/connect`), shared with email (ADR-0006) |
| 2026-09-29 | First release: schedule on the organiser's Google Calendar with a Meet link, Google emails the invite (INT-12…INT-19, ADR-0005) |
