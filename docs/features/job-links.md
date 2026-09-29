# Shareable job links

| | |
| --- | --- |
| **ID prefix** | LINK |
| **Status** | Done (first release) |
| **Chunk** | 1-lite |
| **Owner** | TBD |
| **Related** | [hiring-tracker.md](hiring-tracker.md), [candidate-portal.md](candidate-portal.md), [jobs-and-careers-page.md](jobs-and-careers-page.md) (the full careers page supersedes this later) |
| **Last updated** | 2026-09-29 |

## Summary

A recruiter writes up an opening (title, location, work mode, type, job
description), turns on its link and shares it (WhatsApp, email, LinkedIn).
Anyone with the link sees the job. To apply, a candidate signs in with any
Google account (registration is automatic on first sign-in), fills a short
form and uploads a résumé. The application lands in the opening's pipeline at
**Applied / Sourced** with the résumé attached as the candidate's original
résumé. Candidates see their own applications with a simple status.

## Users

| Role | Can |
| --- | --- |
| Admin, Recruiter | Edit job details, turn the link on/off, copy and preview the link |
| Anyone with the link | Read the job (no sign-in) |
| Candidate (signed in with Google) | Apply once per job, see their own applications and a coarse status |

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| LINK-01 | Job details on an opening: title shown to candidates, location, work mode (office / hybrid / remote), employment type (free text, e.g. "Internship · 6 months"), description (plain text, ≤ 20 000 characters; blank lines = paragraphs, lines starting `-`, `*` or `•` = bullets). | Done |
| LINK-02 | Each opening has an unguessable link `/apply/{slug}` (12 random characters). The link is off by default; staff turn it on or off. A link that is off returns "not found". | Done |
| LINK-03 | The public job page shows the title, "CodeWalnut", location, work mode, type and description. It never shows the client's name, hiring type, headcount or any candidate data. | Done |
| LINK-04 | Only openings with status Open accept applications; on hold or closed shows "no longer accepting applications". | Done |
| LINK-05 | Signed-out visitors click **Apply — continue with Google**; after sign-in they return to the same job page. Any Google account works (candidate session, ADR-0004). | Done |
| LINK-06 | Apply form: full name (prefilled from Google), phone (≥ 10 digits), résumé (PDF or Word ≤ 10 MB, checked by content), optional note, and consent to store their details. Consent and résumé are required. | Done |
| LINK-07 | On apply: the candidate record is found or created by their Google email; the application is created at Applied / Sourced with source "job link", consent time recorded, and the note in its history; the résumé is stored as the original résumé; the event is audited (`CANDIDATE_APPLIED`). | Done |
| LINK-08 | A candidate can apply to a job only once; a repeat is refused with "You have already applied for this role". | Done |
| LINK-09 | Candidates see only their own applications with a coarse status: Applied, Under review, Selected, Not progressing, Withdrawn — never internal stages, notes or reasons. | Done |
| LINK-10 | Staff accounts cannot apply (403); signed-out calls to apply return 401. | Done |
| LINK-11 | Rate limiting and bot protection on the public page and apply. | v1 |
| LINK-12 | Email confirmation to the candidate on apply. | v1 (needs MSG) |

## Business rules

- The description is candidate-facing: write the client's name in it only if
  the client is happy to be named.
- Turning the link off hides the job immediately; existing applications stay.
- Candidate-supplied text (name, note, résumé) is data, never instructions.

## Acceptance criteria

- **LINK-AC1** Given a client opening with its link on, when a signed-out
  visitor opens it, then the client's name appears nowhere in the response.
- **LINK-AC2** Given a link that is off, then the public page returns 404.
- **LINK-AC3** Given a closed opening, then applying is refused.
- **LINK-AC4** Given a candidate applies with a résumé, then staff see them at
  Applied / Sourced with the original résumé, and the candidate sees "Applied".
- **LINK-AC5** Given the same candidate applies twice, then the second is 409.
- **LINK-AC6** Given no consent or no résumé, then the apply is refused.

Tests: `JobLinkFlowTest` (backend), `PublicJobPage.test.tsx` (frontend).

## Change log

| Date | Change |
| --- | --- |
| 2026-09-29 | Created and shipped: job details, share link on/off, public job page, Google sign-in and apply with résumé, My applications |
