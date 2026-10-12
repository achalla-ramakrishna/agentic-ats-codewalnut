# ADR-0028: Candidates book their own interview slot; the recruiter sends the invites in one click

- **Status**: accepted
- **Date**: 2026-10-12
- **Relates to**: docs/features/pipeline.md (PIPE-24), ADR-0005 (interviews on the organiser's Google Calendar), ADR-0026 (Naukri imports)

## Context

One posting brought 350+ applicants; 100+ sent their résumé. Each now needs a 30-minute call. Agreeing a
time by email one person at a time doesn't scale, and the ATS keeps a recruiter's Google access only
for their signed-in session (no stored refresh tokens), so it can't create calendar events on its own
when a candidate picks a time at night.

## Decision

- **Slots per opening** (`interview_slot`): the recruiter offers days, daily windows (e.g. 10:00–13:00,
  15:00–18:00), a length (30 minutes) and interviewers. Duplicates and past times are skipped; at most a
  month and 500 slots per request. Free slots can be removed; booked ones stay.
- **A personal booking link per application** (`booking_link`, 36 random characters), created when an
  email uses `{bookingLink}` (template *Book a 30-minute call*). The link opens `/book/{token}` with no
  sign-in: the candidate's first name, the role and the free slots (from two hours ahead) in their own
  time zone. Booking is atomic (`update … where application_id is null`), one slot per application,
  and can be changed until the invite is sent. The booking is recorded in the history (SLOT_BOOKED).
- **Invites in one click**: *Send invites for N booked* (on the opening's *Interview slots* panel and on
  the Workflow page) calls the normal interview scheduling for each booked slot, creating the Google
  Calendar event with a Meet link and inviting the candidate and interviewers, while the recruiter is
  connected to Google. Failures are listed per person; a missing Google connection asks to connect.
- **Workflow**: rows carry the booking; a pending one becomes the next step *Slot booked: send the
  interview invite* (after replying to the person, if they wrote); the Interview column shows *Booked …*.
  The *Waiting for our reply* tab suggests the booking email.

## Consequences

- Candidates confirm instantly on the page; the calendar invite follows when the recruiter clicks
  *Send invites* (best done once or twice a day).
- The token is the only key to the booking page; it reveals only the first name, role and free times.
- A candidate who wants a time that isn't offered replies by email as before.
