# Feature specs

One file per feature. `docs/SPEC.md` holds what cuts across features (goals,
roles, pipeline overview, non-functional requirements, build plan); each file
here holds the detailed, testable requirements for one feature.

## How to maintain these

- **Start from [`_TEMPLATE.md`](_TEMPLATE.md)** for a new feature, and add it to
  the index below and in `docs/SPEC.md`.
- **Requirement IDs are permanent.** `CAND-07` always means the same thing. If a
  requirement is dropped, mark it ~~struck through~~ with a note — never reuse
  the ID. New requirements take the next free number.
- **Code and tests reference IDs** (e.g. a test named for `AUTH-04`) so a change
  to a requirement shows what code it affects.
- **Change the spec in the same PR as the code** that implements or changes the
  behaviour, and add a line to the file's *Change log*.
- **Status** in the header is one of: `Draft` → `Ready` (agreed, can be built) →
  `In progress` → `Done` (all MVP requirements shipped) → `Deferred`.
- **Priority** per requirement: `MVP`, `v1`, or `Later`.
- Open questions stay in the feature file until answered; the answer moves into
  the requirements and the question is ticked with the decision.

## Index

| ID prefix | Feature | Chunk | Status |
| --- | --- | --- | --- |
| AUTH | [Sign-in, users & roles](auth-and-users.md) | 0 | In progress |
| AUDIT | [Audit log](audit-log.md) | 0 | In progress |
| CLI | [Clients](clients.md) | 1 | Ready |
| REQ | [Requisitions](requisitions.md) | 1 | Ready |
| JOB | [Jobs & careers page](jobs-and-careers-page.md) | 1 | Ready |
| CAND | [Candidates](candidates.md) | 2 | Ready |
| PIPE | [Pipeline](pipeline.md) | 2 | Ready |
| MSG | [Email & communication](communication.md) | 3 | Ready |
| INT | [Interviews & scorecards](interviews-and-scorecards.md) | 4 | Ready |
| ASMT | [Coding assessments](assessments.md) | 5 | Draft |
| SUB | [Client submissions & review](client-submissions.md) | 6 | Ready |
| OFR | [Offers & placements](offers-and-placements.md) | 7 | Draft |
| PRIV | [Privacy, consent & retention](privacy-and-retention.md) | 8 (+ throughout) | Ready |
| ADM | [Admin & settings](admin-and-settings.md) | 0–8 | Ready |
| AI | [AI assistance](ai-assistance.md) | 2, v1 | Draft |
| PORTAL | [Candidate portal](candidate-portal.md) | v1 | Draft |
| RPT | [Reports](reports.md) | v1 | Draft |
