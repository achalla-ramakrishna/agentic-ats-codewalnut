# Offers & placements

| | |
| --- | --- |
| **ID prefix** | OFR |
| **Status** | Draft |
| **Chunk** | 7 (placements export v1) |
| **Owner** | TBD |
| **Related** | [requisitions.md](requisitions.md), [client-submissions.md](client-submissions.md) |
| **Last updated** | 2026-09-25 |

## Summary

Close the hire. When CodeWalnut is the employer (internal and client-deployed
hires), the ATS produces the offer, runs approvals and e-signature. For direct
placements the client makes the offer and the ATS tracks it through to joining
and the guarantee period.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Draft and send offers; track acceptance and joining |
| Approver | Approve offers above thresholds |
| Account Manager | Track client offers for direct placements |
| Candidate | Review, e-sign or decline |

## User stories

- **OFR-S1** As a Recruiter, I want to generate an offer letter from a template
  with the agreed CTC so that offers go out quickly and error-free.
- **OFR-S2** As an Approver, I want to approve offers above the band so that
  compensation stays in policy.
- **OFR-S3** As an Account Manager, I want to track a direct placement to the
  candidate's start date so that we know when to invoice.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| OFR-01 | Offer (CodeWalnut as employer): template with merge fields, CTC breakdown, joining date, expiry date; for client-deployed hires also bill rate and margin (internal only). | MVP |
| OFR-02 | Approval chain by thresholds (vs. requisition budget band, level); approvers notified. | MVP |
| OFR-03 | E-sign via provider; statuses: draft → pending approval → approved → sent → accepted / declined / expired / withdrawn. | MVP |
| OFR-04 | Expiry reminders to candidate and Recruiter; revise-and-resend creates a new version. | MVP |
| OFR-05 | Direct placement: record client offer status (made, accepted, declined), agreed start date; no CodeWalnut letter. | MVP |
| OFR-06 | On acceptance / joining: application → Hired / Placed, requisition headcount decremented, hand-off to HRMS (v1). | MVP |
| OFR-07 | Placement record: start date, guarantee end date, replacement flag if the hire leaves within guarantee. | v1 |
| OFR-08 | Export placements for invoicing. | v1 |
| OFR-09 | Invoicing inside the ATS. | Later |

## Business rules

- Offer compensation is visible only to Admin, the owning Recruiter, the
  Account Manager for that client and Approvers.
- Viewing or changing offer compensation is audited.

## Edge cases & failure states

- E-sign provider down → offer stays "approved, not sent" with retry.
- Candidate accepts after expiry → Recruiter must re-issue.

## Acceptance criteria

- **OFR-AC1** Given an offer above threshold, then it cannot be sent until all
  approvers approve.
- **OFR-AC2** Given a direct-placement job, when the client selects a
  candidate, then no CodeWalnut offer letter can be generated and client-offer
  tracking starts.

## Data

`Offer`, `OfferApproval`, `Placement` (v1).

## API (planned)

`POST /applications/{id}/offers`, `POST /offers/{id}/approve|send|withdraw`,
`POST /webhooks/esign`.

## Open questions

- [ ] Offer approval thresholds?
- [ ] Existing HRMS and e-sign provider?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
