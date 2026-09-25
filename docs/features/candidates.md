# Candidates

| | |
| --- | --- |
| **ID prefix** | CAND |
| **Status** | Ready |
| **Chunk** | 2 (talent pool v1) |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md), [ai-assistance.md](ai-assistance.md), [privacy-and-retention.md](privacy-and-retention.md) |
| **Last updated** | 2026-09-25 |

## Summary

One profile per person, reused across every job they apply to or are sourced
for. The profile holds contact details, CVs, links, experience and expectations,
and shows their full history across jobs and clients.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Add/source candidates, keep profiles current, search |
| Hiring Manager / Account Manager | Review candidates on their jobs |
| Interviewer | See the profile of candidates they interview (no contact details) |

## User stories

- **CAND-S1** As a Recruiter, I want to upload a CV and have the fields filled in
  so that adding a sourced candidate takes under a minute.
- **CAND-S2** As a Recruiter, I want to be warned when a candidate already exists
  so that we don't have two profiles for one person.
- **CAND-S3** As a Recruiter, I want to search past candidates by skill and
  experience so that I can fill new roles from people we already know.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| CAND-01 | Profile: name, email, phone, location, current company/title, total experience, skills, links (LinkedIn, GitHub, portfolio), current CTC, expected CTC, notice period, source, tags, owner recruiter. | MVP |
| CAND-02 | Multiple CV versions per candidate; latest is default; each application records which version was used. | MVP |
| CAND-03 | CV parsing (PDF/DOCX) pre-fills profile fields; every parsed field is editable and marked as parsed until confirmed. | MVP |
| CAND-04 | Duplicate detection on create, apply and import: exact email or phone match, or fuzzy name + current company match. Shows the existing profile and offers merge. | MVP |
| CAND-05 | Merge: combine two profiles, keeping all applications, documents and history; audited. | MVP |
| CAND-06 | Activity timeline on the profile: applications, stage moves, emails, interviews, submissions, notes — across all jobs. | MVP |
| CAND-07 | Private notes with @mentions of staff. | MVP |
| CAND-08 | Manually add a sourced candidate to one or more jobs. | MVP |
| CAND-09 | Search by name, email, skills and CV text (full-text), with filters for experience, location, notice period, tags. | MVP |
| CAND-10 | Contact details and CTC are hidden from roles not allowed to see them (e.g. Interviewers). | MVP |
| CAND-11 | Talent pool: candidates who opted in stay searchable after rejection; saved searches; bulk re-engagement email. | v1 |
| CAND-12 | Bulk import from spreadsheet (see [admin-and-settings.md](admin-and-settings.md)). | MVP |

## Business rules

- Email is unique per candidate; phone numbers are normalised to E.164.
- Viewing CTC is audited.

## Edge cases & failure states

- Parsing fails or is low-confidence → CV stored, fields left for manual entry,
  profile flagged "check details".
- Virus found in upload → file rejected and not stored.
- Merge conflict on a field → the Recruiter picks which value wins.

## Acceptance criteria

- **CAND-AC1** Given an application whose email matches an existing candidate,
  then no second profile is created and the application attaches to the
  existing one.
- **CAND-AC2** Given a phone match with a different email, then the Recruiter is
  shown a merge prompt.
- **CAND-AC3** Given an Interviewer opens an assigned candidate, then email,
  phone and CTC are not in the response.
- **CAND-AC4** Given a search for "React" with ≥ 100k candidates, then results
  return in < 500 ms (p95).

## Data

`Candidate`, `Document` (CVs, parsed JSON), `CandidateNote`.

## API (planned)

`GET/POST /candidates`, `GET/PATCH /candidates/{id}`,
`POST /candidates/{id}/documents`, `POST /candidates/{id}/merge`,
`GET /candidates/search`.

## Open questions

- [ ] Existing candidate data to migrate, and how many records?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
