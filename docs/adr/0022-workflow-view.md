# ADR-0022: Workflow view built from existing records, with rule-based next steps

- **Status**: accepted
- **Date**: 2026-10-06
- **Relates to**: docs/features/workflow.md (WF-01…WF-08), ADR-0021 (Ask ATS)

## Context

Recruiters lose track of whom they've contacted, who replied and is waiting,
and what each candidate needs next. The facts are already in the app (messages,
events, tests, interviews, client shares, document requests) but spread over
each candidate's panel.

## Decision

- **Derive, don't duplicate**: the board is computed on request from the
  existing tables in a few batched queries (one per table for all rows). No new
  status columns that could drift from what really happened.
- **"Contact" is defined once** in `WorkflowService`: messages sent to the
  candidate (email, WhatsApp, candidate page), tests sent and reminders,
  interview invites and reschedules, document requests, and logged contacts.
- **Calls outside the app are logged** as an `application_event` of type
  `CONTACT_LOGGED`, so they appear in history, count as contact and are audited.
- **Next steps are deterministic rules**, ordered by what blocks progress
  (someone waiting on us first), shown as suggestions with a link to act. No AI:
  the same data always gives the same suggestion, and it costs nothing.
- **"Never contacted" only before interviews**: candidates imported at later
  stages show "No contact recorded" so the list stays meaningful.
- **Ask ATS reuses it** through a `follow_ups` tool rather than its own logic.

## Consequences

- Emails sent outside the app aren't visible unless logged.
- The rules are simple and easy to change in one place
  (`WorkflowService.nextStep`); per-client or per-role rules would need more.
- Very large boards are capped at 2,000 rows; filtering by opening keeps it fast.
