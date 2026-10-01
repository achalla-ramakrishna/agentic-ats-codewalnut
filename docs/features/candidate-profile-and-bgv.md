# Candidate profile & background verification

| | |
| --- | --- |
| **ID prefix** | BGV |
| **Status** | Done (first release) |
| **Chunk** | 1-lite |
| **Owner** | TBD |
| **Related** | [client-access.md](client-access.md), [candidates.md](candidates.md), [candidate-portal.md](candidate-portal.md), [privacy-and-retention.md](privacy-and-retention.md), ADR-0007 |
| **Last updated** | 2026-10-01 |

## Summary

A candidate's full profile and the documents a client needs for background
verification (BGV), collected without emailing identity documents: staff
request them, the candidate uploads them in their candidate page, and staff
share them with the client (see [client-access.md](client-access.md)).

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| BGV-01 | Profile: name, email, mobile, date of birth, current and permanent address, college, degree, graduation year, LinkedIn, emergency contact. Staff with `MANAGE_JOBS` edit it; anyone who can view candidates sees it. | Done |
| BGV-02 | Candidates edit their own profile in their candidate page (not their email, which is their sign-in). Each change adds a history note naming the fields. | Done |
| BGV-03 | Validation: date of birth yyyy-mm-dd and 14–100 years ago; mobile ≥ 10 digits; LinkedIn must be an http(s) link; email unique. | Done |
| BGV-04 | Document kinds: original résumé, CodeWalnut résumé, Aadhaar (masked), PAN, degree certificate / marksheet, passport photo, other. Résumés PDF/Word; the rest also JPG/PNG; ≤ 10 MB; checked by content. New uploads keep old versions. | Done |
| BGV-05 | Aadhaar and PAN are government IDs: listed, uploaded and downloaded only with `VIEW_ID_DOCUMENTS` (Admin, Recruiter, Account Manager). No Aadhaar/PAN number is stored as a field. | Done |
| BGV-06 | Staff request documents from the candidate (any kinds except the CodeWalnut résumé); the drawer then opens the candidate chat with the "Request documents" email template. | Done |
| BGV-07 | The candidate page lists requested documents first, with upload buttons and guidance to upload the masked Aadhaar; uploading fulfils the request. | Done |
| BGV-08 | Requests and candidate uploads appear in the application history; the audit log records which fields or document kinds changed, never values or file contents. | Done |
| BGV-09 | A "BGV complete" checklist per client (configurable required documents) and reminders. | v1 |

## Acceptance criteria

- **BGV-AC1** Given a Hiring Manager, then Aadhaar and PAN are not listed and
  downloading one returns 403.
- **BGV-AC2** Given a file named `.png` that isn't a PNG, then it is refused.
- **BGV-AC3** Given staff request Aadhaar and PAN and the candidate uploads
  Aadhaar, then only PAN is still requested.

Tests: `ProfileAndDocumentsFlowTest` (backend); `MyProfileCard.test.tsx`
(frontend).

## Change log

| Date | Change |
| --- | --- |
| 2026-10-01 | Created and shipped (BGV-01…BGV-08), driven by Blend's BGV request; ADR-0007 |
