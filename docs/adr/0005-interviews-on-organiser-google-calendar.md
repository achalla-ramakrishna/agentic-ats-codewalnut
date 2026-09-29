# ADR-0005: Interviews go on the organiser's own Google Calendar

- **Status**: accepted
- **Date**: 2026-09-29
- **Amends**: AGENTS.md "Integrations … runs as a BackgroundTask" for this one call

## Context

Recruiters want to schedule an interview from the ATS, get a Google Meet
link, and have the candidate emailed the invitation. CodeWalnut uses Google
Workspace; there is no email provider wired into the app yet (MSG is chunk 3).

Options considered:

1. **Service account with domain-wide delegation** — events on any staff
   calendar without per-user consent. Needs a Workspace super-admin to grant
   delegation, and a long-lived key that can act as anyone. Too much power
   and setup for the first cut.
2. **Each recruiter connects their own Google Calendar** (OAuth, scope
   `calendar.events` only) and events are created on their primary calendar.
3. **Send our own email** with a Meet link. Needs an email provider and
   still can't create Meet links without a calendar.

## Decision

- **Option 2.** A second OAuth client registration, `google-calendar`, on
  the same Google client as sign-in, asking only for
  `https://www.googleapis.com/auth/calendar.events`. Sign-in itself still asks
  only for `openid email profile`; candidates are never asked for calendar
  access (the connect endpoint is staff-only).
- **Google sends the emails.** Events are created with a Meet conference and
  `sendUpdates=all`, so Google emails the invitation (and any cancellation) to
  the candidate and interviewers, with the Meet link and "add to calendar".
  The ATS sends no email itself yet.
- **Tokens live in the session only** (`HttpSessionOAuth2AuthorizedClientRepository`):
  never written to the database, gone at sign-out or expiry (~1 hour). When
  it expires the UI asks the recruiter to connect again (Google skips the
  consent screen the second time). No refresh tokens are stored.
- **Synchronous call, not a BackgroundTask.** The recruiter needs the Meet
  link and a definite yes/no at once, and the call uses their live token. If
  Google fails, nothing is saved and the recruiter sees the error — nothing
  is shown as sent when it wasn't.
- **Only the organiser can cancel** through the ATS, because the event lives
  on their calendar.
- The dev and demo profiles use a fake calendar that sends nothing.

## Consequences

- Google shows an "unverified app" warning for the calendar scope (a
  *sensitive* scope) until the app passes Google's verification. Staff click
  through it once; acceptable for internal use. Candidates never see it.
- The Google Calendar API must be enabled in the Cloud project and the
  redirect URI `https://<domain>/oauth2/callback/google-calendar` registered.
- Interviews are tied to the organiser: if they leave, their events stay on
  their calendar. A shared recruiting calendar or delegation can replace this
  later without changing the interview records.
- Rescheduling is cancel + schedule for now.
