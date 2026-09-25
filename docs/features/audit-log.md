# Audit log

| | |
| --- | --- |
| **ID prefix** | AUDIT |
| **Status** | In progress |
| **Chunk** | 0 (then every feature adds its events) |
| **Owner** | TBD |
| **Related** | [auth-and-users.md](auth-and-users.md), [privacy-and-retention.md](privacy-and-retention.md) |
| **Last updated** | 2026-09-25 |

## Summary

An append-only record of who did what and when, so hiring decisions, access to
personal data and permission changes can be reviewed later.

## Users

Admin reads it. Every feature writes to it.

## User stories

- **AUDIT-S1** As an Admin, I want to see who changed a user's roles so that
  permission changes are accountable.
- **AUDIT-S2** As an Admin, I want to see denied access attempts so that I can
  spot misuse or misconfigured roles.
- **AUDIT-S3** As an Admin, I want to filter the log by person, action or record
  so that I can answer "who touched this candidate?".

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| AUDIT-01 | Entries are append-only: no API, repository method or UI can edit or delete them (except anonymisation by the retention job, itself audited). | MVP |
| AUDIT-02 | Each entry records actor (id + email), action, entity type + id, small JSON details, timestamp. | MVP |
| AUDIT-03 | Audited from chunk 0: sign-in, rejected sign-in, user created/updated, access denied. | MVP |
| AUDIT-04 | Each later feature audits its own events: stage moves, submissions sent, offers approved/sent, compensation viewed, exports, deletions, client link access. | MVP (per feature) |
| AUDIT-05 | Entries are written in their own transaction, so denials are kept even when the request fails. | MVP |
| AUDIT-06 | Admin can page through the log newest first (max 100 per page). | MVP |
| AUDIT-07 | Filter by actor, action, entity and date range. | MVP |
| AUDIT-08 | Export filtered entries as CSV (export itself audited). | v1 |

## Business rules

- Details never contain CV text, contact details or compensation values — only
  ids, field names and non-sensitive values.
- Retention: kept at least 3 years (to confirm, see open questions).

## Edge cases & failure states

- Audit write fails → the originating action fails too (no un-audited changes),
  except for denial/rejection records, which are best-effort and never mask the
  original `401`/`403`.

## Acceptance criteria

- **AUDIT-AC1** Given any user role change, then a `USER_UPDATED` entry shows
  from → to roles.
- **AUDIT-AC2** Given a non-admin calls an admin endpoint, then an
  `ACCESS_DENIED` entry names the missing capability.
- **AUDIT-AC3** Given `size=1000` on the list endpoint, then 100 entries are
  returned.

## Data

`AuditLog` (actor_id, actor_email, action, entity_type, entity_id, details,
created_at).

## API

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/v1/audit-log?page&size` | Admin |

## Open questions

- [ ] Retention period for audit entries (proposed 3 years)?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created; AUDIT-01, 02, 03, 05, 06 implemented on the chunk-0 branch |
