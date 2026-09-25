# Email & communication

| | |
| --- | --- |
| **ID prefix** | MSG |
| **Status** | Ready |
| **Chunk** | 3 (WhatsApp/SMS later) |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [candidates.md](candidates.md) |
| **Last updated** | 2026-09-25 |

## Summary

Candidate emails are sent from the ATS using templates, and replies land back on
the candidate's timeline, so no conversation lives only in someone's inbox.

## Users

Recruiters mainly; Hiring and Account Managers occasionally; Admin manages templates.

## User stories

- **MSG-S1** As a Recruiter, I want to email a candidate from their profile using
  a template so that messages are fast and consistent.
- **MSG-S2** As a Recruiter, I want candidate replies to appear on the timeline
  so that colleagues see the whole conversation.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| MSG-01 | Email templates with merge fields (candidate name, job title, public employer label, recruiter, links); per-stage defaults. | MVP |
| MSG-02 | Send individual and bulk emails from profile or board; preview before send. | MVP |
| MSG-03 | Auto-acknowledgement email on application. | MVP |
| MSG-04 | Two-way sync with the sender's Google Workspace (or Microsoft 365) mailbox for candidate threads; replies attach to the candidate's timeline. | MVP |
| MSG-05 | Scheduled send and cancellable delayed rejection emails. | MVP |
| MSG-06 | Delivery status (sent, bounced) shown; bounces flag the candidate's email. | MVP |
| MSG-07 | Unsubscribe from non-essential emails (marketing/re-engagement), respected by bulk sends. | MVP |
| MSG-08 | WhatsApp / SMS notifications. | Later |

## Business rules

- All outbound mail runs through the background task queue with retries.
- Merge fields for client jobs use the public employer label unless the job is
  non-confidential.

## Edge cases & failure states

- Mail provider down → task retried with backoff; message shows "pending", then
  "failed" with a retry button — never shown as sent.
- Mailbox sync token revoked → user is prompted to reconnect; sending falls back
  to the system sender.

## Acceptance criteria

- **MSG-AC1** Given a confidential client job, when an acknowledgement is sent,
  then the email does not contain the client's name.
- **MSG-AC2** Given a candidate replies to a recruiter's email, then the reply
  appears on the candidate timeline within 5 minutes.

## Data

`Message`, `EmailTemplate`, `MailboxConnection`.

## API (planned)

`GET/POST /email-templates`, `POST /candidates/{id}/messages`, `POST /messages/bulk`.

## Open questions

- [ ] Google Workspace or Microsoft 365 for mail and calendar?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
