# ADR-0007: Client contacts sign in and see only what is shared with them

- **Status**: accepted
- **Date**: 2026-10-01
- **Amends**: ADR-0002 (client login moves from v1 into the first release, in a
  narrow form) and the AGENTS.md "Client isolation" guardrail

## Context

Blend hired three interns that CodeWalnut put forward and asked for their
background-verification (BGV) details: masked Aadhaar card, CodeWalnut
résumé, email, mobile number and so on. Blend's hiring manager should be
able to sign in and get them, instead of CodeWalnut emailing identity
documents around.

ADR-0002 planned review links for clients in the MVP and a client portal in
v1, and the guardrail said a client user must never see a candidate's
contact details. For BGV, the client legitimately needs exactly those.

## Decision

- **Client contacts.** Admins and Account Managers add people at a client by
  email (Clients page). Each contact belongs to one client and is
  deactivated, not deleted, when removed.
- **Same Google sign-in.** `SignInService` routes: staff domain → staff;
  an active client contact → a `CLIENT` session; anyone else → candidate.
  The session is re-checked on every request, so removing a contact cuts
  access immediately. Client sessions can't reach staff or candidate APIs.
- **Explicit, per-candidate sharing (`ClientShare`).** Nothing reaches a
  client unless a Recruiter, Account Manager or Admin shares that candidate.
  A share always shows name, opening and stage, and only what is ticked:
  contact details (email, mobile); profile (date of birth, addresses,
  education, emergency contact); and specific document versions. A share can
  be changed or stopped at any time. Sharing, stopping, and every client
  download are written to the history and the audit log (which fields and
  documents, never the values). Staff see when the client last viewed it.
- **Isolation by construction.** The client portal only reads active shares
  whose client is the contact's own; anything else is "not found". Every
  client endpoint has a cross-client deny test.
- **Government IDs are restricted.** Aadhaar and PAN documents are visible
  only to roles with `VIEW_ID_DOCUMENTS` (Admin, Recruiter, Account
  Manager), not to Hiring Managers or Interviewers. Candidates are asked for
  the masked Aadhaar. No Aadhaar or PAN number is stored as a field.
- **Candidates upload their own documents** from their candidate page when
  staff request them, so identity documents are not sent by email.
- **Client chat** per shared candidate (`CLIENT` message channel): client
  contacts and staff only, never the candidate.

## Consequences

- The `SubmissionService` snapshot model in ADR-0002 is still the target
  for chunk 6 (immutable snapshots of what was submitted, client feedback);
  `ClientShare` is the live, revocable view for hired candidates and BGV and
  can sit alongside it.
- Contact details reach a client only when staff tick them for that
  candidate; the guardrail in AGENTS.md is updated to say so.
- Client contacts use any Google account. A contact's email can't also be
  a staff or candidate email.
- DPDP: sharing for BGV is for the purpose the candidate was hired for;
  the candidate is told in the document request email, and the audit log
  shows exactly what was shared with whom and when.
