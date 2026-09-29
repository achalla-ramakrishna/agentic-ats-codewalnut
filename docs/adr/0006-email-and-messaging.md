# ADR-0006: Email from the recruiter's Gmail, plus in-app conversations

- **Status**: accepted
- **Date**: 2026-09-29
- **Builds on**: ADR-0005 (staff connect their own Google account)

## Context

Recruiters and hiring managers need to write to candidates at the natural
moments (shortlisted, not progressing, documents needed…), candidates need a
way to ask questions back, and the hiring team needs to discuss a candidate
without email threads that only some people see.

There is no email provider in the stack. Railway blocks outbound SMTP on
lower plans, and a transactional provider (SendGrid, Resend…) needs a
verified sending domain (DNS changes on codewalnut.com) and an account.

## Decision

- **Email goes from the sender's own Gmail** through the Gmail API with the
  `gmail.send` scope only (send, never read). It is requested in the same
  "Connect Google (Calendar & Gmail)" step as the calendar, on the same
  `google-calendar` registration, so the registered redirect URI doesn't
  change. The token stays in the session (as in ADR-0005). Replies land in
  the sender's inbox, which is where recruiters already work.
- **Every email is also a message in the ATS.** Conversations live per
  application in a `message` table (append-only), with two channels:
  - `CANDIDATE` — staff ↔ candidate. The candidate reads and replies in
    their candidate page (Google sign-in, ADR-0004). Staff choose per
    message whether to also email it.
  - `TEAM` — internal discussion (recruiters, hiring and account managers,
    admins). Never shown to the candidate.
- **Recipients are decided by the server**: an email only ever goes to the
  candidate's address on file, so the feature can't be used to mail anyone
  else. Subjects are stripped of line breaks and encoded, so no header
  injection. Plain text only.
- **Templates are starting points** the sender always sees and edits. The ATS
  suggests one right after a stage change (Screening, Shortlisted, Selected,
  Not progressing). Nothing is ever sent automatically.
- **Synchronous send**: if Gmail fails, nothing is saved and the error is
  shown — nothing is shown as sent when it wasn't.
- **No new email or chat vendor.** Chat refreshes by polling (20–30 s),
  which is plenty for hiring conversations.

## Consequences

- No automatic emails yet (e.g. an acknowledgement when someone applies via
  the job link): there is no staff session to send from. Those need a system
  sender (MSG-03); the on-screen confirmation covers it for now.
- Email replies are not pulled back into the ATS (MSG-04 sync is v1); the
  in-app conversation is the shared record, and each email notes that the
  candidate can reply in their candidate page.
- `gmail.send` is a sensitive scope; the same "unverified app" click-through
  as the calendar until Google verification.
- Hiring and Account Managers gain the new `MESSAGE_CANDIDATES` capability;
  Interviewers and Approvers don't.
