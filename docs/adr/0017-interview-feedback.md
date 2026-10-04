# ADR-0017: Interview feedback, independent until you submit

- **Status**: accepted
- **Date**: 2026-10-04
- **Builds on**: interview scheduling on Google Calendar (INT-12…19), the interview questions guide (INT-21)

## Context

Interviews run on Google Meet, but what the panel thought lived in chat
messages and memory. Hiring managers want structured feedback from everyone
on the panel, side by side, without one strong voice anchoring the rest.

## Decision

- **One feedback entry per interview and person** (`interview_feedback`,
  unique on interview and author email), editable, with every submit and edit
  audited. Fields: attendance, ratings, strengths, concerns, questions asked,
  recommendation, notes.
- **A fixed set of six areas on the guide's 1–4 scale** (problem solving,
  technical depth, coding / hands-on, communication, ownership and attitude,
  role fit), stored as JSON so per-role templates can come later without a
  migration. Any area may be left blank; at least one must be rated, and a
  recommendation chosen, when the interview took place. A no-show needs only a
  note.
- **Four-point recommendation with no "maybe"** (strong no hire, no hire,
  hire, strong hire), so every interviewer takes a side.
- **Independent until you submit**: a panel member (organiser or listed
  interviewer) sees the others' feedback only after giving their own. Staff
  with VIEW_CANDIDATES who weren't on the panel see all of it.
- **Who may write**: the panel, plus anyone with MANAGE_JOBS (recruiters,
  admins), who sometimes run an interview for someone else.
- **Staff only**: candidates and client contacts have no staff capabilities,
  so the API refuses them; interviewers can't open interviews they weren't on
  ("not found", like the interview list).
- Feedback opens when the interview starts and is closed for cancelled
  interviews. It moves nothing by itself and AI does not write or score it.

## Consequences

- No reminders by email yet: the Interviews page lists "Waiting for your
  feedback" (interviews from the last 30 days). Email or chat nudges can be
  added later (INT-AC3).
- Feedback isn't locked when the candidate changes stage; audit entries show
  any late edits.
- Sharing feedback with clients is out of scope; it would need its own
  decision and redaction.

## Addendum (2026-10-04): drafts during the interview

Interviewers want to rate some things, such as communication, as they happen. The
form is available any time (interviews happen early or late) and saves a private draft as they type.
Drafts are invisible to everyone else and don't count as feedback; only submitting
shares it with the panel (and triggers the admin update), so independence is kept.

