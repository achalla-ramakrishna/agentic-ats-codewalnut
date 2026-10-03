# ADR-0013: "View as" — admins see the app as someone else, read-only

- **Status**: accepted
- **Date**: 2026-10-03
- **Relates to**: ADR-0004 (candidate sign-in), ADR-0007 (client login); was ADM-13 (parked)

## Context

Sign-in is Google-only, so an admin can't sign in as a candidate, a client's
hiring manager or a recruiter to check what they will see before inviting
them.

## Decision

- **The admin stays signed in as themselves.** "View as" is a session
  attribute (`ats.viewAs`: kind, target, label, admin, expiry). The
  `CurrentUserService` / `CurrentCandidateService` / `CurrentClientService`
  resolvers return the viewed identity while it is set; the session endpoint
  reports that kind of session plus a `viewAs` block for the banner.
- **Three kinds**: a candidate (any candidate with an email — even one who
  never signed in), an active client contact, or a staff role (the admin's
  own account with only that role, so menus and permissions match).
- **Read-only, enforced on the server**: `ViewAsReadOnlyFilter` refuses
  every non-GET API call except leaving and signing out; GET side effects
  (e.g. marking a candidate's messages as read) are skipped.
- **Admins only** (`MANAGE_USERS`, checked against the real user), one at a
  time, **30 minutes**, start and stop **audited** with the target.
- A fixed banner shows who is being viewed with **Back to admin**; "Sign out"
  while viewing also just goes back.

## Consequences

- Admins can check every audience's screens with real data before inviting
  people; nothing can be done in anyone's name.
- Because it is read-only, flows that write (applying, taking a test,
  sending a message) can be seen but not completed; use a test candidate
  account for those.
