# Hiring tracker (first release)

| | |
| --- | --- |
| **ID prefix** | TRK |
| **Status** | Done (first release) |
| **Chunk** | 1-lite |
| **Owner** | TBD |
| **Related** | Simplified first cut of [clients.md](clients.md), [jobs-and-careers-page.md](jobs-and-careers-page.md), [candidates.md](candidates.md), [pipeline.md](pipeline.md) |
| **Last updated** | 2026-09-29 |

## Summary

A simple, working hiring workflow to start with, driven by the first real
need: Blend asked CodeWalnut to hire interns who will be on CodeWalnut's
payroll and work for Blend. Recruiters track every candidate's stage, keep
notes and résumés in one place, and admins (e.g. management) follow progress
on the dashboard. It grows into the fuller features linked above.

## Users

| Role | Can |
| --- | --- |
| Admin, Recruiter | Create clients and openings, add/import candidates, change stages, add notes, upload résumés |
| Hiring Manager, Account Manager | View openings, candidates, notes, résumés |
| Interviewer, Approver | Not yet (dashboard only) |

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| TRK-01 | Clients: name (unique), optional notes. | Done |
| TRK-02 | Openings: title, hiring type (internal / client on CodeWalnut payroll / client direct placement), client (required for client types), number needed, status (open / on hold / closed). | Done |
| TRK-03 | Add a candidate to an opening by hand: name, email, phone, stage. | Done |
| TRK-04 | Import candidates by pasting a table from Excel / Google Sheets (header row with name, email, phone; extra columns such as "slno" ignored); preview first, nothing saved until Import. | Done |
| TRK-05 | Import cleans data and flags problems per row: text in the email column is left blank with a warning, stray characters (e.g. trailing `\|`) and spaces removed, missing phone flagged. | Done |
| TRK-06 | No duplicates: same email (or phone when no email) reuses the person; a person already in the opening is skipped; duplicate rows in one paste are skipped; name-only rows match by name within the opening. | Done |
| TRK-07 | Fixed stages: Applied / Sourced → Screening → Interviewed → Shortlisted → Submitted to client → Client interview → Selected → Offer sent → Offer accepted → Joined; exits On hold, Rejected, Withdrawn. | Done |
| TRK-08 | Change a stage from the pipeline table; Rejected and Withdrawn need a reason. | Done |
| TRK-09 | Notes per candidate; append-only history of every add, stage change and note with who and when. | Done |
| TRK-10 | Two résumés per candidate — original and CodeWalnut-formatted — PDF or Word, ≤ 10 MB, checked by content; new uploads keep old versions. | Done |
| TRK-11 | Uploads and downloads of résumés are recorded in the audit log. | Done |
| TRK-12 | Dashboard: open openings with count per stage and progress bar; the 25 most recent activities. | Done |
| TRK-13 | Candidates page: search by name, email or phone across openings; filter by stage. | Done |
| TRK-14 | Per-opening stage templates, scorecards, client review links, emails — see the full feature files. | Later |

## Business rules

- Candidate personal data is entered through the app only — never committed to
  the repository or seeded from code.
- Résumés are stored in MySQL for now (fine for hundreds of files); move to
  object storage when volume grows. Railway database backups therefore cover
  résumés too.

## Acceptance criteria

- **TRK-AC1** Given a pasted table where one email cell holds a name, when
  previewed, then that row shows "Email doesn't look valid … left blank" and
  can still be imported.
- **TRK-AC2** Given the same paste imported twice, then the second import
  adds nobody.
- **TRK-AC3** Given a move to Rejected without a reason, then it is refused.
- **TRK-AC4** Given a file renamed to `.pdf` that is not a PDF, then the
  upload is refused.
- **TRK-AC5** Given an Interviewer, then openings, candidates and résumés
  return `403`.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-28 | Created and shipped: clients, openings, import, pipeline stages, notes, history, two résumés per candidate, dashboard |
| 2026-09-29 | First stage renamed "Applied / Sourced"; openings gain job details and a share link ([job-links.md](job-links.md)) |
