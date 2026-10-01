# Client access (client contacts, sharing, client chat)

| | |
| --- | --- |
| **ID prefix** | CLA |
| **Status** | Done (first release) |
| **Chunk** | 1-lite (ahead of chunk 6) |
| **Owner** | TBD |
| **Related** | [client-submissions.md](client-submissions.md), [candidate-profile-and-bgv.md](candidate-profile-and-bgv.md), [communication.md](communication.md), ADR-0002, ADR-0007 |
| **Last updated** | 2026-10-01 |

## Summary

People at a client (e.g. Blend's hiring manager) sign in with Google and
see only the candidates CodeWalnut shared with their company, and only the
parts that were ticked: contact details, profile, and particular documents
(e.g. masked Aadhaar and CodeWalnut résumé for background verification).
They can message CodeWalnut about each candidate.

## Users

| Who | Can |
| --- | --- |
| Admin, Account Manager | Add and remove client contacts |
| Admin, Recruiter, Account Manager | Share a candidate with the client, change or stop sharing |
| Hiring Manager, others who view candidates | See whether and what is shared |
| Client contact | See shared candidates, view/download shared documents, message CodeWalnut |

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| CLA-01 | Clients page lists each client's contacts (email, name, last sign-in); Admins and Account Managers add and remove them. Staff-domain and candidate emails are refused. | Done |
| CLA-02 | A client contact signs in with Google (any account with that email) and gets a client session; removed contacts lose access on their next request. | Done |
| CLA-03 | Client sessions can't use staff or candidate APIs (403); staff and candidates can't use client APIs (403). | Done |
| CLA-04 | Staff share a candidate in a client opening from the candidate panel: always name, opening, stage; optionally contact details, profile, and the newest version of each chosen document; with an optional note. | Done |
| CLA-05 | Sharing can be changed or stopped at any time; staff see who shared, when, and when the client last viewed. | Done |
| CLA-06 | Sharing government IDs requires `VIEW_ID_DOCUMENTS`. Only documents of that candidate can be shared. Internal openings can't be shared. | Done |
| CLA-07 | The client page shows shared candidates with the ticked details and View/Download links; nothing else (no internal notes, stages history, other documents). | Done |
| CLA-08 | A client contact never sees another client's candidates, documents or messages (404). | Done |
| CLA-09 | Every client document download is audited (who, which candidate, which document). Share changes and stops are in the history and audit log. | Done |
| CLA-10 | Client chat per shared candidate between the client's contacts and staff; never shown to the candidate. Staff see it in a "Chat with {client}" tab and in Messages; client messages count as waiting for a reply. | Done |
| CLA-11 | Client feedback and decisions on submitted candidates (review link, structured feedback). | Chunk 6 (SUB) |
| CLA-12 | Email notification to the client contact when something new is shared. | v1 |

## Acceptance criteria

- **CLA-AC1** Given contact details not ticked, then the client response has
  no email or phone.
- **CLA-AC2** Given a contact of client B, then client A's shared candidate,
  documents and messages are not found.
- **CLA-AC3** Given sharing is stopped or the contact removed, then access
  ends immediately.

Tests: `ClientAccessFlowTest` (backend); `ClientHomePage.test.tsx`,
`ShareWithClient.test.tsx` (frontend).

## Change log

| Date | Change |
| --- | --- |
| 2026-10-01 | Created and shipped (CLA-01…CLA-10); ADR-0007 |
