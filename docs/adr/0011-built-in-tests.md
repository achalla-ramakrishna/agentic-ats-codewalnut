# ADR-0011: Built-in online tests, written by CodeWalnut, scored automatically

- **Status**: accepted
- **Date**: 2026-10-03
- **Relates to**: docs/features/assessments.md (ASMT-02 provider integration stays an option),
  ADR-0006 (messaging), ADR-0010 (suggestions)

## Context

Recruiters want to send applicants an aptitude, Java or Python test straight
after they apply, to keep momentum, and to use the score when deciding whom
to take forward. No external test provider is in use today.

## Decision

- **Our own test library** (`assessment`, `assessment_question`): multiple
  choice (one or several right answers) and short answers (e.g. "what does
  this code print?"), with an optional code block, points per question, a
  time limit and a pass mark. A test can change only while it's a **draft**;
  once **ready** its questions are locked (duplicate to change), so every
  candidate gets the same test and scores stay comparable.
- **AI drafts, people check.** Claude (structured output, `AssessmentDraft`)
  can draft questions for a draft test; they are marked "AI draft — check it"
  until a person edits them, and "Mark ready" asks for confirmation while
  unchecked drafts remain. Dev and demo use a built-in starter bank
  (aptitude, Java, Python) instead.
- **Sending** (`assessment_invite`) posts a message in the candidate chat
  (and by email / WhatsApp if ticked, through the existing messaging) with a
  link `/tests/{id}`. The candidate signs in with Google using the email the
  test was sent to; anyone else gets "not found".
- **Taking**: the timer starts on Start and is enforced by the server
  (30 s grace); answers are saved as they go; when time runs out, what was
  saved is scored. Unstarted tests expire at the due date. Correct answers
  never leave the server in candidate responses; candidates see "submitted",
  not their score.
- **Scoring** is all-or-nothing per question (short answers ignore case,
  extra spaces and surrounding quotes). Results show in the drawer (with a
  per-question review for staff), as a Test column and "Passed a test" filter
  on the opening, in the "closest to selection" order and reasons, and to the
  AI assistant for questions such as "who passed the Java test?".
- **Reminders are one click, not automatic.** Email goes from the
  recruiter's own Gmail, which needs their signed-in session, so a
  background job can't send it. The Test column and drawer highlight tests
  not started after two days with a **Remind** button; an expired test can
  be reminded with two more days.
- **Advisory.** No stage changes on a score; ASMT-05 auto-advance stays off.

## Consequences

- Tests work with no third-party account or cost; AI drafting costs a few
  rupees per batch.
- No code execution yet: a secure sandbox (or a provider such as HackerRank,
  ASMT-02) is needed for "write code" questions.
- Questions are visible to candidates during the test and could be shared;
  keep several tests per role and refresh them (duplicate and edit).
