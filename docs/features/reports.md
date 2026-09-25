# Reports

| | |
| --- | --- |
| **ID prefix** | RPT |
| **Status** | Draft |
| **Chunk** | v1 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md) (StageEvent is the data source) |
| **Last updated** | 2026-09-25 |

## Summary

Answer "how is hiring going?" without exporting to Excel — per job, recruiter
and client.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| RPT-01 | Funnel per job / period: counts and conversion per stage, drop-off reasons. | v1 |
| RPT-02 | Time in stage and time-to-hire (median, p75), by job, recruiter, client. | v1 |
| RPT-03 | Source effectiveness: applications → hires by source. | v1 |
| RPT-04 | Interviewer load and feedback SLA (share of scorecards within 24 h). | v1 |
| RPT-05 | Per client: open roles, time-to-submit, submissions, submission → interview → offer ratios, client feedback turnaround, placements. | v1 |
| RPT-06 | Offer acceptance rate by role, source and client. | v1 |
| RPT-07 | Filters (date, job, client, recruiter) and CSV export; exports audited. | v1 |
| RPT-08 | Diversity reporting: opt-in, aggregated only, minimum group size. | Later |

## Business rules

- Report access follows row scoping: Account Managers see their clients,
  Hiring Managers their jobs, Admin everything.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
