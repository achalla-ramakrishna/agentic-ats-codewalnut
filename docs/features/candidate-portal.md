# Candidate portal

| | |
| --- | --- |
| **ID prefix** | PORTAL |
| **Status** | Draft |
| **Chunk** | Sign-in: 0 (done). Portal features: v1 (apply form is in [jobs-and-careers-page.md](jobs-and-careers-page.md)) |
| **Owner** | TBD |
| **Last updated** | 2026-09-25 |

## Summary

A light, password-less space where candidates see their application status,
book interviews, upload documents and manage their data.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| PORTAL-01 | Sign in with any Google account, including personal Gmail (AUTH-18). | MVP (done) |
| PORTAL-08 | Alternative for candidates without Google: magic-link sign-in by email; single-use, expires in 30 minutes. | MVP (with email, chunk 3) |
| PORTAL-02 | Application status per job in candidate-friendly wording (no internal stage names or reasons). | v1 |
| PORTAL-03 | Interview slot booking and rescheduling (with INT-02). | v1 |
| PORTAL-04 | Document upload requested by the Recruiter (e.g. ID, payslips for offer). | v1 |
| PORTAL-05 | Update contact details and preferences; talent-pool opt-in/out. | v1 |
| PORTAL-06 | Data export and deletion requests (PRIV-08). | v1 |
| PORTAL-07 | Accessible to WCAG 2.1 AA and mobile-first. | v1 |

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
| 2026-09-25 | PORTAL-01 is now Google sign-in (any account); magic link moved to PORTAL-08; a basic candidate area exists |
