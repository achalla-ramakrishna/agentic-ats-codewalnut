# Jobs & careers page

| | |
| --- | --- |
| **ID prefix** | JOB |
| **Status** | Ready |
| **Chunk** | 1 (job boards v1) |
| **Owner** | TBD |
| **Related** | [requisitions.md](requisitions.md), [pipeline.md](pipeline.md), [privacy-and-retention.md](privacy-and-retention.md) |
| **Last updated** | 2026-09-25 |

## Summary

A job is the open position candidates apply to. Jobs are published on
CodeWalnut's careers page (server-rendered for search engines) and, from v1,
pushed to job boards. Confidential client jobs hide the client's name.

## Users

| Role | Uses this feature to |
| --- | --- |
| Recruiter | Create, publish, pause and close jobs; set the hiring team |
| Hiring Manager / Account Manager | Draft JDs; view their jobs |
| Candidate | Find jobs and apply |

## User stories

- **JOB-S1** As a Recruiter, I want to open a job from an approved requisition
  with a JD and pipeline so that I can start sourcing.
- **JOB-S2** As a candidate, I want to browse open roles and apply in a few
  minutes so that I don't give up halfway.
- **JOB-S3** As an Account Manager, I want a client's job advertised without the
  client's name so that confidentiality is kept.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| JOB-01 | Create a job from an approved requisition or a template; fields: title, JD (rich text), skills, experience range, location, work mode, employment type, hiring type and client (from requisition). | MVP |
| JOB-02 | Hiring team: owning Recruiter (required), Hiring Manager or Account Manager, interviewers. | MVP |
| JOB-03 | Pipeline stages cloned from the client's or the default template, editable per job (see [pipeline.md](pipeline.md)). | MVP |
| JOB-04 | Statuses: draft → open → on hold → closed (reason: filled / cancelled). Only open jobs accept applications. | MVP |
| JOB-05 | Careers page: job list with search and filters (location, work mode, skill), job detail, apply form — server-rendered, indexable, mobile-friendly. | MVP |
| JOB-06 | Confidential jobs show the public employer label, never the client's name, anywhere public (page, meta tags, structured data, job-board posts). | MVP |
| JOB-07 | Apply form: name, email, phone, CV upload (PDF/DOCX ≤ 10 MB), LinkedIn/GitHub links, current/expected CTC, notice period, consent checkbox with privacy notice link. | MVP |
| JOB-08 | Shareable job link with source tracking (UTM `source`), recorded on the application. | MVP |
| JOB-09 | Apply is rate-limited and bot-protected (e.g. invisible CAPTCHA). | MVP |
| JOB-10 | Google for Jobs structured data on job detail pages. | MVP |
| JOB-11 | Push jobs to LinkedIn, Naukri, Indeed and import applicants back. | v1 |
| JOB-12 | AI-assisted JD drafting from the requisition (see [ai-assistance.md](ai-assistance.md)). | v1 |

## Business rules

- A client JD under NDA is never published (flag from [clients.md](clients.md) CLI-09, v1).
- Closing a job asks what to do with active applications (reject with reason,
  or move to another job).

## Edge cases & failure states

- CV upload fails virus scan → application refused with a clear message.
- Candidate applies twice to the same job → one application kept, the second
  attaches the new CV version (see [candidates.md](candidates.md)).
- Job closed while someone is filling the form → submit shows "this job is no
  longer open" and suggests similar open jobs.

## Acceptance criteria

- **JOB-AC1** Given an approved requisition, when a Recruiter publishes a job,
  then it appears on the careers page and a public application creates a
  Candidate and Application in the first stage with a consent timestamp.
- **JOB-AC2** Given a confidential client job, then the careers page HTML, meta
  tags and structured data never contain the client's name.
- **JOB-AC3** Given a job on hold, then the apply form is not shown and the
  apply endpoint refuses submissions.

## Data

`Job`, `JobHiringTeam`, `PipelineStage` (per job).

## API (planned)

`GET/POST /jobs`, `GET/PATCH /jobs/{id}`, `POST /jobs/{id}/publish|hold|close`,
public: `GET /careers`, `GET /careers/{slug}`, `POST /public/apply`.

## Open questions

- [ ] Careers page on its own subdomain (e.g. `careers.codewalnut.com`)?
- [ ] Which job boards matter most for v1?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
