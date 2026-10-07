# ADR-0024: Seven pipeline stages

- **Status**: accepted
- **Date**: 2026-10-07
- **Relates to**: docs/features/pipeline.md (PIPE-15), ADR-0018 (admin updates), ADR-0022 (workflow view)

## Context

Thirteen stages (Applied, Screening, Interviewed, Shortlisted, Submitted to
client, Client interview, Selected, Offer sent, Offer accepted, Joined, On hold,
Rejected, Withdrawn) mixed statuses with actions and were too many to read at a
glance. Management wants a simple status per candidate: where they stand, not
what was last done (the workflow view and history already show actions).

## Decision

- **Seven stages in use**: Applied / Sourced, Interviewed, Shortlisted, Offer
  sent, Joined, On hold, Rejected.
- **Retired, not deleted**: the other six stay in the `Stage` enum so older
  history (`application_event.from_stage/to_stage`) still reads correctly.
  `Stage.current()` maps each retired stage to its replacement; every place a
  stage comes in (moves, adds, imports, search filters, both assistants, Ask
  ATS actions) uses it, so old spreadsheets, links and words like "selected"
  still work. `/stages`, stage counts and AI tool schemas list only the seven.
- **Data migrated in place** (V23): Screening → Applied / Sourced; Submitted to
  client and Client interview → Shortlisted; Selected and Offer accepted →
  Offer sent; Withdrawn → Rejected. History rows are untouched.
- **Detail lives elsewhere**: "shared with the client", "client interview",
  "offer accepted" are visible from client shares, interviews, notes and the
  workflow view's next steps rather than as stages.
- **Admin updates** default to Shortlisted, Offer sent and Joined.

## Consequences

- Fewer, clearer stages on openings, the dashboard, the workflow view and
  reports; counts per stage read like a simple sheet.
- Some distinctions are no longer reportable by stage alone (e.g. offers sent
  vs accepted); they can return as stages later if needed, since the enum
  values still exist.
