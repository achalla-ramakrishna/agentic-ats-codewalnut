# ADR-0018: Admin updates on interview feedback and key stages

- **Status**: accepted
- **Date**: 2026-10-04
- **Builds on**: ADR-0006 (email from the user's own Gmail), ADR-0017 (interview feedback)

## Context

Admins want to know, without opening every candidate, when interview feedback
comes in and when a candidate reaches an important point in the pipeline, but
not every stage change.

## Decision

- **Two triggers only**: the first submission of a panel member's feedback
  (edits don't re-send), and a move into a key stage. Default key stages:
  Shortlisted, Selected, Offer accepted, Joined, set with
  `ATS_ADMIN_UPDATE_STAGES`.
- **One plain-text summary** per update: candidate, opening and client, stage,
  contact details, education, the latest three test scores, plus the feedback
  (recommendation, ratings, strengths, concerns, notes, panel progress) or the
  stage move (who, from where, the note, feedback so far).
- **Always kept in the app** (`admin_update`, the Admin updates page, admins
  only via the new VIEW_ADMIN_UPDATES capability), so nothing is lost if email
  can't go out.
- **Emailed best effort** to the other active admins, after the change commits,
  from the acting person's Gmail. The app has no mailbox of its own (ADR-0006),
  so if that person hasn't connected Gmail the email is skipped; the page shows
  why. Emailing never blocks or undoes the change.
- Staff only: candidates and client contacts never see updates.

## Consequences

- If the acting person hasn't connected Google, admins have to check the
  page. A shared sender (a service mailbox) would fix that, but needs its own
  credentials and a decision.
- Updates aren't marked read or unread yet.
