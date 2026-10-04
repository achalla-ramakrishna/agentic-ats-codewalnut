# ADR-0020: Live coding rooms

- **Status**: accepted
- **Date**: 2026-10-04
- **Builds on**: ADR-0016 (coding tests in a Judge0 sandbox), ADR-0017 (interview feedback), ADR-0019 (interview kits)

## Context

Coding interviews ran on Google Meet with the candidate sharing their screen
in whatever editor they had. Interviewers couldn't run the code, couldn't see
it after the call, and every candidate had a different setup. We already run
candidates' code safely in a self-hosted Judge0 for coding tests.

## Decision

- **One room per interview** (`coding_room`), opened from the interview's
  feedback page by the panel or a recruiter. The problem is a built-in
  `CodingBank` problem (the opening's kit problems are offered first) or one
  the interviewer types; switching problem keeps the previous one, its code
  and its last run as history.
- **The candidate signs in** with the email on their application (the same
  Google or email sign-in as tests) and opens a private link with a long
  random token. A wrong link and someone else's link get the same 404.
  The link alone is not enough, so pasting it in the Meet chat is safe.
- **Watching by polling**, not websockets: the candidate's page saves code
  about a second after each change and the panel polls every two seconds.
  That's close enough to live for an interview, needs no new infrastructure
  and works through the same API, session and CSRF rules as everything else.
- **Runs use the existing sandbox** on the problem's samples only, capped at
  200 per room; staff can run the current code too. If the runner isn't
  configured the room still works for writing and watching.
- **Meet stays the video channel**: the room doesn't do video, audio or
  screen sharing. Candidates are still asked to share their screen.
- **Staff only data**: notes and earlier problems never reach the candidate
  API; candidates and client contacts can't see rooms from the staff side.
  The room is a record for the panel; it scores nothing and never moves a
  candidate.

## Consequences

- Updates arrive within a couple of seconds, not keystroke by keystroke, and
  the interviewer can't type into the candidate's code (no pair editing).
- One room per interview; a second coding round needs its own interview.
- Polling adds a small request every two to four seconds per open room, fine
  at our volume.
