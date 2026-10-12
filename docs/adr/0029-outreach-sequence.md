# ADR-0029: An outreach sequence the system works out, sent in one click a day

- **Status**: accepted
- **Date**: 2026-10-12
- **Relates to**: docs/features/pipeline.md (PIPE-25), ADR-0006 (email from the sender's Gmail), ADR-0028 (self-booking)

## Context

With 350 applicants the recruiter can't track, per person, whether we asked for the résumé, whether it
came, whether they got the booking link, booked, were invited. The steps are the same for everyone,
so the system should work them out and do the sending.

## Decision

- **The step is derived, not stored**: from emails sent (their template, PIPE-23), whether an original
  résumé is on file, the booked slot and interviews: NEW → RESUME_ASKED → RESUME_RECEIVED →
  INVITED_TO_BOOK → BOOKED → SCHEDULED → SCREENED. Any earlier email without a template counts as
  having asked (and V27 labels past résumé requests), so nobody gets the first email twice.
- **What is due**: Applied candidates — résumé request (new), booking link (résumé received), one
  follow-up 3 days after the résumé request, one reminder 3 days after the booking link; any stage —
  calendar invite for a booked slot. Nothing is due for closed candidates, those without an email, or
  anyone who wrote to us (a person answers them).
- **"Today's emails"** on the Workflow page lists what is due with the email text, and sends it in one
  click from the recruiter's Gmail, warmest first, one email per person, within a daily limit (150 by
  default, per browser). Later stages (shortlisted, offer) stay a person's decision.
- **Not unattended**: sending uses the recruiter's Google session (no stored tokens, ADR-0005/0006). Fully
  automatic sending would need a dedicated sending account (Workspace service account or an email
  provider) and is left for a later decision.

## Consequences

- One click a day keeps 350 people moving; the Workflow shows each person's step (Applied) and step chips.
- Reminders go once; candidates stop getting emails as soon as they reply, send a résumé or book.
