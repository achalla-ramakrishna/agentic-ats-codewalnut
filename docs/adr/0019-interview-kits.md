# ADR-0019: Interview kits generated from the job description

- **Status**: accepted
- **Date**: 2026-10-04
- **Builds on**: ADR-0015 (role tests), ADR-0016 (coding problems), ADR-0017 (interview feedback), INT-21 (interview guide)

## Context

Each opening needs the same preparation: which test to send, what each
interviewer should ask, which coding problem to give, and how to score. Doing
it by hand is slow and uneven between interviewers. The job description
already says what the role needs.

## Decision

- **One kit per opening**, stored as JSON (`interview_kit`) with a hash of the
  title and description it came from. A changed description marks the kit as
  out of date; regenerating replaces it and picks fresh questions.
- **Rule-based reading of the job description**, not AI: a keyword list maps
  skills to bank areas and interview-guide categories; lines with "must",
  "required", "strong" or "hands-on" (and the title) make a skill a must-have,
  lines with "nice to have", "preferred" or "a plus" make it optional; years of
  experience or words like intern, senior and lead set the level; the role
  test whose areas best match the skills is chosen. The same input and seed
  always give the same kit, it costs nothing, and it works offline. Recruiters
  can override the level and role. The kit states what it assumed.
- **Built only from reviewed content**: the role test preset, the interview
  guide (strong answers and red flags), the built-in coding problems (with
  their expected approach) and the feedback competencies, plus a few questions
  written from the job's must-haves.
- **Suggests, doesn't decide**: interviewers can ask other questions; the panel
  decides. The feedback form adds the kit's must-have skills to its six areas.
- **Staff only**: hiring staff see any kit; interviewers only for openings where
  they are on an interview panel; candidates and clients never.

## Consequences

- Skills missing from the keyword list aren't detected; the list is easy to
  extend in `InterviewKitService`.
- Kits can't be edited question by question yet; regenerating, overriding the
  level or role, or asking other questions covers most needs. AI-written
  job-specific questions could be added later as an advisory extra.
