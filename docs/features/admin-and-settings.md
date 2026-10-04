# Admin & settings

| | |
| --- | --- |
| **ID prefix** | ADM |
| **Status** | Ready |
| **Chunk** | 0–8 (each setting lands with its feature) |
| **Owner** | TBD |
| **Related** | [auth-and-users.md](auth-and-users.md), [pipeline.md](pipeline.md) |
| **Last updated** | 2026-10-04 |

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
| ADM-13 | **View as** (menu → View as, Admins only): see the app as a candidate (any with an email), an active client contact (e.g. Blend's hiring manager) or a staff role (own account with only that role). Fixed banner "Viewing as … — Back to admin"; read-only on the server (every write refused); 30 minutes; start and stop audited (ADR-0013). | Done |
| ADM-14 | **Admin updates** (ADR-0018): when a panel member first submits interview feedback, admins get a short summary: candidate, opening (and client), stage, email, phone, education, latest test scores, the interview, that person's recommendation, ratings, strengths, concerns and notes, and how many of the panel have given feedback. Edits don't send another. | Done |
| ADM-15 | The same summary when a candidate is moved to one of a few **key stages**: Shortlisted, Selected, Offer accepted, Joined (set with `ATS_ADMIN_UPDATE_STAGES`). It says who moved them, from which stage, the note, and the feedback so far. Routine stages (screening, interviewed, on hold, rejected…) send nothing. | Done |
| ADM-16 | Every update is kept on **Admin updates** (menu, admins only), newest first, filterable by feedback or stage, with links to the candidate and the feedback. | Done |
| ADM-17 | After the change is saved, the update is also emailed to every other active admin from the acting person's Gmail (the app has no mailbox of its own, ADR-0006). If they haven't connected Gmail, or sending fails, the change still stands and the page shows "not emailed". Candidates and clients never see updates. | Done |

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
| 2026-10-02 | "What's new" page with release notes per feature, a menu badge and a dashboard note for unseen updates; entries live in `frontend/src/whatsNew.ts` (AGENTS.md) |
| 2026-10-03 | ADM-13 "View as" for admins added as parked, to take up later |
| 2026-10-03 | ADM-13 View as shipped (ADR-0013) |
| 2026-10-04 | ADM-14 to ADM-17 admin updates on feedback and key stages (ADR-0018) |
