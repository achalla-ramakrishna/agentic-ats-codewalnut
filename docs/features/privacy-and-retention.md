# Privacy, consent & retention

| | |
| --- | --- |
| **ID prefix** | PRIV |
| **Status** | Ready |
| **Chunk** | 8 (consent capture from chunk 1) |
| **Owner** | TBD |
| **Related** | [candidates.md](candidates.md), [audit-log.md](audit-log.md), [client-submissions.md](client-submissions.md) |
| **Last updated** | 2026-09-25 |

## Summary

Candidate data is personal data. The ATS records consent, honours export and
deletion requests, and anonymises data it no longer needs. Baseline: India's
DPDP Act 2023, plus GDPR for EU applicants.

## Users

Admin (requests, retention settings); Candidates (consent, requests).

## User stories

- **PRIV-S1** As a candidate, I want to know how my data is used and ask for it
  to be deleted so that I stay in control.
- **PRIV-S2** As an Admin, I want rejected candidates anonymised automatically
  after the retention period so that we don't keep data we don't need.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| PRIV-01 | Consent captured at apply/import with timestamp, privacy-notice version and purpose (this job; talent pool opt-in separately). | MVP |
| PRIV-02 | Consent to submission to a named client recorded before submitting (see SUB-05). | MVP |
| PRIV-03 | Data export request: Admin generates a machine-readable export of a candidate's data within 30 days; audited. | MVP |
| PRIV-04 | Deletion request: personal data and documents erased within 30 days; aggregate `StageEvent` data kept anonymised for reporting; audited. | MVP |
| PRIV-05 | Retention: rejected/withdrawn candidates who did not opt into the talent pool are anonymised after a configurable period (default 12 months) by a nightly job. | MVP |
| PRIV-06 | Consent withdrawal stops processing; pending client submissions are withdrawn. | MVP |
| PRIV-07 | Documents stored encrypted in private object storage; access by signed URLs expiring in ≤ 5 minutes. | MVP |
| PRIV-08 | Candidate self-service export/delete requests from the portal. | v1 |
| PRIV-09 | Per-client retention and data rules (see CLI-09). | v1 |

## Business rules

- Anonymisation replaces name, email, phone, links, CTC, notes and documents;
  keeps job, stages, dates and source for statistics.
- Legal hold flag on a candidate pauses retention jobs.

## Edge cases & failure states

- Deletion requested while an offer is open → Admin must resolve the offer
  first; the request stays open and visible.

## Acceptance criteria

- **PRIV-AC1** Given a rejected candidate past retention without opt-in, when the
  nightly job runs, then their personal fields and CV are gone and the funnel
  counts are unchanged.
- **PRIV-AC2** Given an application without a consent timestamp, then it cannot
  be saved.

## Data

`Candidate.consent_*`, `ConsentRecord`, `DataRequest`.

## Open questions

- [ ] Confirm the retention period (12 months) with legal.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
