# Sign-in, users & roles

| | |
| --- | --- |
| **ID prefix** | AUTH |
| **Status** | In progress |
| **Chunk** | 0 |
| **Owner** | TBD |
| **Related** | [audit-log.md](audit-log.md), [admin-and-settings.md](admin-and-settings.md), ADR-0001 |
| **Last updated** | 2026-09-25 |

## Summary

CodeWalnut staff sign in with their Google Workspace account. Only people an
Admin has provisioned can get in, and what they see and can do is driven by
their role(s). External users (client reviewers, candidates) never get staff
accounts — they use magic links (see [client-submissions.md](client-submissions.md),
[candidate-portal.md](candidate-portal.md)).

## Users

| Role | Uses this feature to |
| --- | --- |
| Admin | Provision users, assign roles, deactivate leavers |
| All staff | Sign in, see their role-based navigation, sign out |

Staff roles: **Admin, Recruiter, Hiring Manager, Account Manager, Interviewer,
Approver**. A person may hold several roles; they get the union of their
permissions.

## User stories

- **AUTH-S1** As a staff member, I want to sign in with my CodeWalnut Google
  account so that I don't manage another password.
- **AUTH-S2** As an Admin, I want to add a colleague and give them roles so that
  they can start using the ATS.
- **AUTH-S3** As an Admin, I want to deactivate someone who left so that they lose
  access immediately.
- **AUTH-S4** As any user, I want to see only the sections relevant to my role so
  that the app stays simple.
- **AUTH-S5** As a developer, I want to sign in locally as any seeded role without
  Google credentials so that I can build and test role-based screens.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| AUTH-01 | Staff sign in with Google (OpenID Connect). No passwords are stored. | MVP |
| AUTH-02 | Sign-in requires a verified Google email in an allowed domain (default `codewalnut.com`, configurable). | MVP |
| AUTH-03 | Only provisioned, active users can sign in. Unknown or deactivated users are rejected. | MVP |
| AUTH-04 | Emails listed as bootstrap admins are provisioned as Admin on first sign-in, so a fresh install has an administrator. | MVP |
| AUTH-05 | The Google account (subject id) is pinned on first sign-in; a different Google account with the same email is rejected. | MVP |
| AUTH-06 | Every API call re-reads the user, so role changes and deactivation take effect on the next request. | MVP |
| AUTH-07 | `GET /me` returns the user, roles, capabilities and the navigation items they may see; the UI renders only that navigation. | MVP |
| AUTH-08 | Admins list users, create a user (email, name, ≥ 1 role), change roles, rename, and activate/deactivate. | MVP |
| AUTH-09 | Admins cannot remove their own Admin role or deactivate themselves. | MVP |
| AUTH-10 | Emails are stored lower-case and are unique. | MVP |
| AUTH-11 | Sessions use an HTTP-only, SameSite=Lax cookie (Secure outside dev), 8-hour timeout; sign-out ends the session. | MVP |
| AUTH-12 | State-changing requests require a CSRF token (cookie `XSRF-TOKEN` echoed as header `X-XSRF-TOKEN`). | MVP |
| AUTH-13 | Unauthenticated API calls get `401` (never a redirect); authenticated calls without permission get `403`. | MVP |
| AUTH-14 | A password-less dev login and fake seed users (one per role + a multi-role lead) exist only in the `dev` profile. | MVP |
| AUTH-15 | Role → capability matrix is defined in one place (`RolePermissions`) and enforced server-side by `AccessPolicy`. | MVP |
| AUTH-16 | Row-level scoping (own jobs, own clients, assigned interviews) is added by each feature on top of capabilities. | MVP (per feature) |
| AUTH-17 | Optional: Microsoft 365 sign-in, if CodeWalnut uses it for some staff. | Later |

## Business rules

Role → capability matrix (current; extended as features land):

| Capability | Admin | Recruiter | Hiring Mgr | Account Mgr | Interviewer | Approver |
| --- | --- | --- | --- | --- | --- | --- |
| Dashboard | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Jobs (view) | ✓ | ✓ | ✓ | ✓ | | |
| Jobs (manage) | ✓ | ✓ | | | | |
| Candidates (view) | ✓ | ✓ | ✓ | ✓ | | |
| Clients (view) | ✓ | ✓ | | ✓ | | |
| Clients (manage) | ✓ | | | ✓ | | |
| Interviews | ✓ | ✓ | ✓ | ✓ | ✓ | |
| Approvals | ✓ | | | | | ✓ |
| Reports | ✓ | ✓ | ✓ | ✓ | | |
| Users (manage) | ✓ | | | | | |
| Audit log | ✓ | | | | | |

## Edge cases & failure states

- Google email not verified, wrong domain, not provisioned, deactivated, or
  different Google account → sign-in rejected, reason audited (`LOGIN_REJECTED`),
  user returned to `/login?error`.
- Google credentials not configured → app still starts; the login page hides the
  Google button.
- User deactivated mid-session → next API call returns `401`.
- Duplicate email on create → `409`. Email outside allowed domains → `400`.
  Empty role list or unknown role → `400`.

## Acceptance criteria

- **AUTH-AC1** Given an unprovisioned `@codewalnut.com` user, when they sign in
  with Google, then they are rejected and a `LOGIN_REJECTED` audit entry exists.
- **AUTH-AC2** Given a provisioned Interviewer, when they call `GET /me`, then
  navigation is exactly Dashboard and Interviews.
- **AUTH-AC3** Given an Admin changes a user from Interviewer to Recruiter, when
  that user next calls `GET /me`, then they see Recruiter navigation.
- **AUTH-AC4** Given an Admin deactivates a user, when that user makes any API
  call, then it returns `401`.
- **AUTH-AC5** Given a Recruiter, when they call `GET /users`, then it returns
  `403` and an `ACCESS_DENIED` audit entry exists.
- **AUTH-AC6** Given a POST without the CSRF header, then it returns `403`.
- **AUTH-AC7** Given the app runs without the `dev` profile, then
  `POST /auth/dev-login` returns `404`.

## Data

`AppUser` (email, name, google_subject, active, last_login_at),
`app_user_role` (user_id, role).

## API

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/v1/auth/config` | Public — which sign-in options to show |
| GET | `/oauth2/authorization/google` | Public — starts Google sign-in |
| POST | `/api/v1/auth/dev-login` | Dev profile only |
| POST | `/api/v1/auth/logout` | Signed in |
| GET | `/api/v1/me` | Signed in |
| GET / POST | `/api/v1/users` | Admin |
| PATCH | `/api/v1/users/{id}` | Admin |

## Out of scope

Passwords, self-registration, staff MFA beyond what Google enforces.

## Open questions

- [ ] Google Workspace only, or do some staff use Microsoft 365? (AUTH-17)
- [ ] Who are the bootstrap admins for production?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md; AUTH-01…16 implemented on the chunk-0 branch |
| 2026-09-25 | AUTH-14: dev profile is never active by default (tested); UI for AUTH-07, AUTH-08 added |
