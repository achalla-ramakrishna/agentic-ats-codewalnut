# Workflow: contacts, what happened, next steps

| | |
| --- | --- |
| **ID prefix** | WF |
| **Status** | Done |
| **Chunk** | v1 |
| **Owner** | TBD |
| **Related** | [communication.md](communication.md), [pipeline.md](pipeline.md), [interviews-and-scorecards.md](interviews-and-scorecards.md), [ai-assistance.md](ai-assistance.md) (Ask ATS), ADR-0022 |
| **Last updated** | 2026-10-06 |

## Summary

One page that shows, for every candidate, who contacted them, how and when,
whether they wrote and are waiting on us, where their test, interview and client
share stand, and a suggested next step, with the full history a click away.
Recruiters no longer have to open each candidate to know whom they've spoken to.

## Users

Hiring staff who see candidates (Admin, Recruiter, Hiring Manager, Account
Manager, Delivery Lead). Logging contact needs MESSAGE_CANDIDATES. Interviewers,
approvers, candidates and client contacts don't get it.

## User stories

- **WF-S1** As a recruiter, I want to see whom I haven't contacted yet, so nobody is forgotten.
- **WF-S2** As a recruiter, I want to see who wrote to us and is waiting for a reply.
- **WF-S3** As a recruiter, I want a suggested next step per candidate, so I know what to do today.
- **WF-S4** As a recruiter, I want one history of everything that happened with a candidate.
- **WF-S5** As a recruiter, I want to record calls and WhatsApps made outside the app.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| WF-01 | **Workflow** in the menu (and a Workflow button on each opening): one row per candidate per opening, for all open openings or one opening, with search by name or opening. | Done |
| WF-02 | Per row: stage and days in it; **last contact** (when, how, by whom) and how many contacts; "They wrote … ago" when the candidate's last message is unanswered; latest test (status, score); interview (upcoming time, or held with feedback n of panel, or cancelled); client share (when, viewed or not); documents awaited. Contacts counted: emails, WhatsApps and candidate-page messages sent from the app, tests sent and reminded, interview invites and reschedules, document requests, and logged contacts. | Done |
| WF-03 | Tabs with counts: Everyone in progress, Needs action, Waiting for our reply, Never contacted (Applied/Screening with no contact; later stages show "No contact recorded" instead), No contact for 7+ days, Joined / rejected / withdrawn. Sorted with people waiting on us first, then urgent steps, then longest since contact. | Done |
| WF-04 | **Next step** per candidate, from simple rules in this order: reply to their message; interview coming up; collect interview feedback; review a submitted test; remind about an unstarted test (2+ days or past due); chase requested documents; then by stage (get in touch, send a test or schedule an interview, decide, share with the client, follow up with the client, get the client's decision, send the offer, follow up on the offer, confirm joining). Marked urgent when someone is waiting on us or it has sat for a few days. Each step links to where to do it (the chat, tests, feedback form or the candidate). Suggestions only; nothing changes by itself. | Done |
| WF-05 | **History**: every event and message for the candidate in one list, newest first: added, stage moves with reasons, notes, emails/WhatsApps/messages sent, the candidate's replies, tests, interviews, client shares and client messages, document requests and uploads, team chat, logged contacts. | Done |
| WF-06 | **Log a call or message made outside the app** (call, WhatsApp, email, met in person, other, with an optional note). It counts as a contact, shows in the history and is audited. | Done |
| WF-07 | Ask ATS can answer the same questions ("who haven't we contacted?", "who is waiting for a reply?", "what should I do next for the Java openings?"). | Done |
| WF-09 | **WhatsApp** button on each row (people who can message candidates): opens WhatsApp in a new tab with a message suggested for the next step (first contact, test reminder, documents, offer, joining…), from the candidate's mobile number; the recruiter presses Send there. With the WhatsApp Business API on, the message is shown first and sent from CodeWalnut's number. Either way it is recorded like any WhatsApp from the chat, so it counts as contact. Disabled when no mobile number is on file. | Done |
| WF-08 | Reminders: a daily email to each recruiter with their urgent steps. | Later |

## Business rules

- Built only from what the app already records; no AI decides the next step.
- People see the board only if they can see candidates; the history follows the same rule.

## Edge cases & failure states

- Candidates imported at a later stage (e.g. Interviewed) with no recorded contact show "No contact recorded", not "Never contacted".
- Emails sent from personal Gmail outside the app aren't seen; log them (WF-06).
- At most 2,000 rows are shown at once (the most recently active, across all open openings); pick an opening to narrow it.

## Acceptance criteria

- **WF-AC1** Given a candidate wrote last, then they appear under "Waiting for our reply" with "Reply to their message", first in the list; replying removes them.
- **WF-AC2** Given a call is logged, then the last contact shows "Call" by the person who logged it and the history has it.

Tests: `WorkflowFlowTest`, `WorkflowRulesTest` (backend); `WorkflowPage.test.tsx` (frontend).

## Data

No new tables: `application_event` (new type `CONTACT_LOGGED`), `message`,
`assessment_invite`, `interview`, `interview_feedback`, `client_share`,
`document_request`.

## API

`GET /workflow?jobId=`, `GET /applications/{id}/timeline`, `POST /applications/{id}/contacts`.

## Change log

| Date | Change |
| --- | --- |
| 2026-10-06 | WhatsApp button with a suggested message per next step (WF-09) |
| 2026-10-06 | Created: workflow page, history, logged contacts, next steps (WF-01…WF-07, ADR-0022) |
