# ADR-0004: Candidates sign in with any Google account

- **Status**: accepted
- **Date**: 2026-09-25
- **Amends**: ADR-0001 decision 3 (candidates were to use magic links only)

## Context

Chunk 0 restricted Google sign-in to provisioned `codewalnut.com` staff.
Candidates will mostly use personal Gmail accounts, and the team wants them to
sign in with Google too, with an email magic link also available.

## Decision

- **One "Continue with Google" button for everyone.** `SignInService` routes by
  email after Google verifies it:
  - staff domain (`ATS_ALLOWED_DOMAINS`, default `codewalnut.com`) →
    must be a provisioned, active `AppUser`; otherwise refused. A staff-domain
    email is never turned into a candidate.
  - any other verified Google account → `CandidateAccount`, created on first
    sign-in, Google subject pinned.
- **Sessions carry their type** as an authority (`ROLE_STAFF` /
  `ROLE_CANDIDATE`). Staff APIs refuse candidate sessions (`403`); candidate
  APIs (`/api/v1/candidate/**`) refuse staff sessions.
- **Magic link stays** as the option for candidates without Google, built
  with outgoing email (chunk 3).
- The Google OAuth consent screen must be **External** and published, since
  candidates are outside the Workspace.

## Consequences

- Anyone with a Google account can create a candidate account, so candidate
  endpoints must only ever expose that candidate's own data.
- Staff access is unchanged in strength: it still requires Admin
  provisioning, now enforced in the app rather than also by an Internal
  consent screen.
