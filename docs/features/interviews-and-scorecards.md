# Interviews & scorecards

| | |
| --- | --- |
| **ID prefix** | INT |
| **Status** | In progress (scheduling and feedback shipped) |
| **Chunk** | 4 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [client-submissions.md](client-submissions.md), [ai-assistance.md](ai-assistance.md) |
| **Last updated** | 2026-10-04 |

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
| INT-21 | **Interview questions** page for staff who interview (VIEW_INTERVIEWS: admin, recruiter, hiring manager, account manager, interviewer): 17 categories and 150 questions (aptitude, data structures for freshers with Java/Python/JavaScript/C++ questions, live coding, Java, Python, JavaScript, React, Angular, Node.js, SQL, system design, CS fundamentals, DevOps and cloud, QA automation, data analytics, AI/GenAI, project deep-dive). Each has a level (fresher, 1–3 years, 3+ years), what a strong answer covers and the red flags, plus how to run and score the round (1–4 scale). Content lives in `resources/interview/guide.yml`. | Done |
| INT-22 | Filter by role (the role-test roles), level, language, category and search; pick questions for an interview (kept in the browser); hide the answer guides while sharing a screen; print. Linked from the Interviews page. | Done |
| INT-23 | Candidates, client contacts and approvers can't read the questions (the API refuses; there is no link in their navigation). | Done |
| INT-24 | **Feedback form** after a Google Meet interview (ADR-0017), at `/interviews/{id}/feedback`. How it went (happened, ended early, candidate didn't join, I couldn't join); rate six areas on the 1–4 scale from the interview questions guide (problem solving, technical depth, coding / hands-on, communication, ownership and attitude, role fit), each with optional evidence; strengths, concerns, the questions asked, an overall recommendation (strong no hire, no hire, hire, strong hire) and notes. A recommendation and at least one rating are required when the interview took place; a no-show needs only a note. Opens when the interview starts; not for cancelled interviews. One entry per person, editable; submits and edits are audited. | Done |
| INT-25 | Who gives it: the organiser and the listed interviewers, plus recruiters and admins (who may run an interview for someone). Hiring managers and account managers who weren't on the panel read it. The candidate's drawer shows, per interview, "Feedback: n of panel" with the recommendations and a link to the form. | Done |
| INT-26 | Independent opinions: a panel member sees the others' feedback only after submitting their own (INT-AC1). | Done |
| INT-27 | "Waiting for your feedback" on the Interviews page: interviews you were on that started, ended in the last 30 days and have no feedback from you. Candidates, client contacts and approvers can never read or write feedback; an interviewer can't open an interview they weren't on. | Done |
| INT-28 | **Interview kit** per opening (ADR-0019), at `/interview-kits/{jobId}`, linked from the opening, the Interviews page and the feedback form. Generated from the job description: the skills it asks for (must-have or nice to have), the level (from years of experience or words like intern, senior, lead) and the closest role test. Recruiters and admins can change the level or role and regenerate; regenerating picks fresh questions. Notes say what was assumed (no description, no years given, no clear role). Rule-based, no AI; it suggests and the panel decides. | Done |
| INT-29 | The kit's contents: (1) the **online test**, the role test at that level, with **Create this test** (a draft to check, then send); (2) a **screening call** with questions from the job's must-haves, work mode, location and availability; (3) a **technical interview** with questions written from the must-haves plus questions from the interview guide for each skill, with strong answers and red flags; (4) **live coding**: a warm-up and a main problem at the level, with the expected approach and what to look for, plus data-structure questions to discuss (a hands-on exercise instead for SQL, DevOps and data-analyst roles); (5) **system design** from mid-level up; (6) a **project deep-dive**; then the **scorecard** and hire bar. Answer guides can be hidden before sharing a screen; the kit prints. | Done |
| INT-30 | When the job description changes after the kit was made, the kit says so and offers to regenerate. | Done |
| INT-31 | The feedback form adds the job's must-have skills (from the kit) to the six standard areas to rate. | Done |
| INT-32 | Who sees kits: hiring staff (VIEW_CANDIDATES) for any opening; interviewers only for openings where they are on an interview panel. Candidates, client contacts and approvers never. Generating is audited. | Done |
| INT-33 | **Log an interview held elsewhere** (recruiters and admins), from the candidate's panel: a Meet or call set up outside the app, already held or happening now (last 90 days). No calendar event or email; it adds the interview (the logger is organiser, plus listed interviewers) so the panel can give feedback. History notes it as logged. | Done |
| INT-34 | **Recent interviews and feedback** on the Interviews page: every interview that started in the last 30 days (hiring staff see all; interviewers only theirs), with how many have given feedback, whether yours is due, and a **Give feedback** / **View feedback** button. The candidate's panel always shows the feedback link, or says it opens when the interview starts. | Done |

## Business rules

- Feedback can be edited by its author; every submit and edit is audited
  (first release: no lock when the stage moves on).
- Feedback is a record for people to decide on. It never moves a candidate by
  itself, and no AI writes or scores it.
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

- **INT-AC7** Given a panel member gives feedback, then the others on the
  panel see it only after giving theirs; staff who weren't on the panel see it
  all; candidates and client contacts get no access.

Tests: `InterviewFlowTest`, `InterviewFeedbackFlowTest`, `InterviewKitFlowTest`, `InterviewKitServiceTest`, `GoogleCalendarClientTest`,
`GoogleSignInConfiguredTest` (backend); `InterviewsPanel.test.tsx`,
`InterviewFeedbackPage.test.tsx`, `InterviewKitPage.test.tsx` (frontend).

- **INT-AC1** Given an interviewer who hasn't submitted, when they open the
  debrief, then peer feedback is hidden. (Done: INT-26.)
- **INT-AC2** Given an Interviewer requests an unassigned candidate, then `403`
  and an audit entry.
- **INT-AC3** Given an interview ended 2 h ago with no scorecard, then the
  interviewer has been reminded.

## Data

Built: `Interview`, `InterviewFeedback` (table `interview_feedback`, V17: one
row per interview and author email; ratings as JSON; attendance;
recommendation). Planned: `InterviewPanelMember`, `ScorecardTemplate` (per-role
areas instead of the six defaults).

## API

Built: `GET /google/status`, `GET /google/connect?returnTo=`,
`GET /interviews` (upcoming), `GET/POST /applications/{id}/interviews`,
`POST /interviews/{id}/cancel`, `PATCH /candidates/{id}`,
`GET /candidate/interviews`, `GET/PUT /interviews/{id}/feedback`,
`GET /interviews/feedback-due`, `GET /applications/{id}/interview-feedback`,
`GET/POST /jobs/{id}/interview-kit`, `GET /interviews/recent`,
`POST /applications/{id}/interviews/log`.

Planned:

`GET/POST /interviews`, `PATCH /interviews/{id}`, `GET /interviews/mine`,
`GET /applications/{id}/debrief`,
public: `GET/POST /book/{token}`.

## Change log

| Date | Change |
| --- | --- |
| 2026-10-04 | Recent interviews with Give feedback buttons; log interviews held outside the app (INT-33, INT-34) |
| 2026-10-04 | Interview kits generated from the job description (INT-28 to INT-32, ADR-0019) |
| 2026-10-04 | Feedback form after interviews, independent until you submit, with a "waiting for your feedback" list (INT-24 to INT-27, ADR-0017) |
| 2026-10-04 | Interview questions page for staff, with scoring guide and freshers' data-structures questions (INT-21 to INT-23) |
| 2026-09-25 | Created from SPEC.md |
| 2026-09-29 | "Connect Google Calendar" became "Connect Google (Calendar & Gmail)" (`/google/status`, `/google/connect`), shared with email (ADR-0006) |
| 2026-09-29 | First release: schedule on the organiser's Google Calendar with a Meet link, Google emails the invite (INT-12…INT-19, ADR-0005) |
