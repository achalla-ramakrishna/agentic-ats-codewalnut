# Admin & settings

| | |
| --- | --- |
| **ID prefix** | ADM |
| **Status** | Ready |
| **Chunk** | 0–8 (each setting lands with its feature) |
| **Owner** | TBD |
| **Related** | [auth-and-users.md](auth-and-users.md), [pipeline.md](pipeline.md) |
| **Last updated** | 2026-09-25 |

## Summary

The configuration Admins need so recruiters don't depend on developers:
templates, lists, custom fields, integrations and data import.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| ADM-01 | User management (see AUTH-08). | MVP |
| ADM-02 | Pipeline templates (default internal, default client, per client). | MVP |
| ADM-03 | Scorecard templates per stage type. | MVP |
| ADM-04 | Email templates (see MSG-01). | MVP |
| ADM-05 | Rejection and withdrawal reason lists. | MVP |
| ADM-06 | Custom fields on candidates and jobs (text, number, select, date). | MVP |
| ADM-07 | Approval thresholds for requisitions and offers. | MVP |
| ADM-08 | Integration settings: mail/calendar, assessment provider, e-sign, storage — secrets entered once, never shown again. | MVP |
| ADM-09 | Retention settings (see PRIV-05). | MVP |
| ADM-10 | Bulk import of candidates (and optionally open jobs) from CSV/XLSX with column mapping, dry-run preview, duplicate check and consent source. | MVP |
| ADM-11 | Careers page branding: logo, colours, intro text, privacy-notice link. | MVP |
| ADM-12 | Slack notifications settings. | v1 |

## Business rules

- Every settings change is audited with before/after values (secrets masked).

## Acceptance criteria

- **ADM-AC1** Given an import file with 3 rows matching existing candidates, then
  the dry run lists them as duplicates and they are not created.
- **ADM-AC2** Given an integration secret saved, then no API response ever
  returns it.

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
