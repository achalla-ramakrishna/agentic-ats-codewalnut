# Email & communication

| | |
| --- | --- |
| **ID prefix** | MSG |
| **Status** | In progress (first release shipped) |
| **Chunk** | 3 (WhatsApp/SMS later) |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [candidates.md](candidates.md) |
| **Last updated** | 2026-09-29 |

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
| MSG-08 | WhatsApp / SMS notifications. | Later (WhatsApp: see MSG-21…) |

### First release: conversations and email from Gmail (shipped 2026-09-29)

Decided in ADR-0006. Simpler than MSG-01…07: no system sender, no mailbox sync.

| ID | Requirement | Priority |
| --- | --- | --- |
| MSG-09 | Each candidate panel has **Chat with candidate** and **Team chat** tabs for Admins, Recruiters, Hiring Managers and Account Managers (`MESSAGE_CANDIDATES`). Interviewers and Approvers have neither. | Done |
| MSG-10 | Staff can also email a candidate message from their own Gmail (`gmail.send` only), with an editable subject and body. Templates: blank, application received, screening, shortlisted, request documents, selected, not progressing. | Done |
| MSG-11 | After moving a candidate to Screening, Shortlisted, Selected or Rejected, the pipeline offers "Let them know?" and opens the candidate chat with the matching template. Nothing is sent automatically. | Done |
| MSG-12 | Emails go only to the candidate's address on file (chosen by the server), as plain text, with no header injection; each ends with how to reply in the candidate page. | Done |
| MSG-13 | Gmail not connected or expired → "Connect Google (Calendar & Gmail)", then back to the same chat; Gmail failure → nothing saved, error shown. | Done |
| MSG-14 | Emails are recorded in the application history and the audit log (that an email was sent, never its content or address). | Done |
| MSG-15 | Candidates see their conversation per application in their candidate page, with a "new" count, and can reply (≤ 5 000 characters, 20 per hour per application). They never see the team chat or staff email addresses. | Done |
| MSG-16 | **Messages** page: candidate conversations, those awaiting our reply first; the dashboard lists candidates waiting for a reply. | Done |
| MSG-17 | Conversations are append-only and refresh every 20–30 seconds while open. | Done |
| MSG-18 | Candidate replies to emails flow back into the ATS (mailbox sync, MSG-04). | v1 |
| MSG-19 | Automatic emails without a staff session (acknowledgement on apply, MSG-03) via a system sender. | v1 |
| MSG-20 | Email or chat notification to staff when a candidate replies; @mentions in team chat. | v1 |

### WhatsApp (shipped 2026-10-02)

Decided in ADR-0008. Supersedes MSG-08 for WhatsApp (SMS stays Later).

| ID | Requirement | Priority |
| --- | --- | --- |
| MSG-21 | In "Chat with candidate" the recruiter writes once and ticks Email and/or WhatsApp (WhatsApp ticked by default when there's a mobile number). The message is always saved in the conversation for that job and shown on the candidate's page. | Done |
| MSG-22 | Without the WhatsApp Business API, WhatsApp opens with the candidate's number and the message ready (click-to-chat); the ATS records "WhatsApp opened" in the conversation, history and audit log. | Done |
| MSG-23 | With the Business API configured, the message is sent from CodeWalnut's number: approved template outside the 24-hour window, free text inside it; status shown (sent, delivered, read, failed). If only WhatsApp was chosen and it fails, nothing is saved; if email worked and WhatsApp failed, a warning is shown. | Done |
| MSG-24 | Candidate WhatsApp replies (via the signed webhook) appear in their conversation and count as waiting for a reply; duplicates are ignored; unknown numbers are ignored. | Done |
| MSG-25 | Webhook calls without a valid `X-Hub-Signature-256` are refused; the webhook is off (404) unless configured. | Done |
| MSG-26 | Files candidates send on WhatsApp are downloaded into their documents. | v1 |

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

- **MSG-AC3** Given a subject containing a line break and `Bcc:`, then the
  sent email has no extra header.
- **MSG-AC4** Given a candidate signed in with another email, then another
  candidate's conversation returns 404.
- **MSG-AC5** Given team chat messages, then the candidate's view contains
  none of them.

Tests: `MessageFlowTest`, `GmailClientTest`, `MimeMessagesTest`, `WhatsAppFlowTest`,
`WhatsAppWebhookTest`, `WhatsAppCloudClientTest`, `PhoneNumbersTest` (backend);
`Conversation.test.tsx`, `CandidateThread.test.tsx`, `JobDetailPage.test.tsx`
(frontend).

- **MSG-AC1** Given a confidential client job, when an acknowledgement is sent,
  then the email does not contain the client's name.
- **MSG-AC2** Given a candidate replies to a recruiter's email, then the reply
  appears on the candidate timeline within 5 minutes.

## Data

`Message`, `EmailTemplate`, `MailboxConnection`.

## API

Built: `GET/POST /applications/{id}/messages?channel=CANDIDATE|TEAM|CLIENT`,
`GET /whatsapp/status`, `GET/POST /webhooks/whatsapp` (Meta, signature-checked),
`GET /messages/inbox`, `GET/POST /candidate/applications/{id}/messages`,
`GET /google/status`, `GET /google/connect?returnTo=`.

Planned:

`GET/POST /email-templates`, `POST /candidates/{id}/messages`, `POST /messages/bulk`.

## Open questions

- [x] Google Workspace or Microsoft 365 for mail and calendar? → **Google** (ADR-0005, ADR-0006).

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
| 2026-10-02 | WhatsApp from the candidate conversation: click-to-chat now, Business API and replies when configured (MSG-21…MSG-25, ADR-0008) |
| 2026-10-01 | Client chat per shared candidate (`CLIENT` channel, CLA-10); the Request documents template points candidates to upload in their page |
| 2026-09-29 | First release: candidate and team chat, email from the sender's Gmail with templates, stage-change prompts, Messages inbox (MSG-09…MSG-17, ADR-0006) |
