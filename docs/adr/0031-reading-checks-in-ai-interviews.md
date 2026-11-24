# ADR-0031: Reading checks in AI interviews — hidden questions, one retake, caught while running

- **Status**: accepted
- **Date**: 2026-11-15
- **Relates to**: docs/features/ai-interviews.md (AINT-24), ADR-0030 (AI interviews), ADR-0016 (advisory activity signals)

## Context

Candidates were reading their answers out instead of answering. Every spoken question in a phase was on
screen before Record, with unlimited time and unlimited re-recordings, so a candidate could paste the
question into an AI tool and read the answer to the camera. That defeats the interview: the answer shows the
tool, not the candidate. The team also only found out after the whole interview (up to two hours) was
submitted and watched.

## Decision

- **A spoken question is hidden until the candidate opens it.** The API sends an empty prompt for an unopened
  VIDEO question; *Show question and start* (`POST …/questions/{id}/reveal`) records when it was first seen and
  returns the text. Opening again doesn't restart anything, even after a reload.
- **Short thinking time, then recording starts on its own** (`ats.interview.think-seconds`, default 30 s). Long
  enough to gather thoughts, too short to generate and rehearse an answer comfortably.
- **One retake** (`ats.interview.takes`, default 2 recordings per question). Retakes start straight away.
- **Signals per answer, worked out by the server where possible**: seconds from seeing the question to recording
  (server clock), recordings made, and times the window or tab lost focus while recording (reported by the page).
- **A reading check per answer, as soon as it uploads**: the interview agent reads the transcript and says
  LIKELY / POSSIBLE / UNLIKELY that it was read out, with a reason (structured output; a short call, two at a
  time in the background). The prompt names the signs (written-register sentences, "firstly… in conclusion",
  stock phrases, generic coverage where their own work was asked) and the cautions (speech-to-text drops
  fillers; formal English, fluency and accent are not evidence). Answers under 25 words aren't judged.
- **The team is told while the interview is still running**: on the first LIKELY answer
  (`ats.interview.reading-alert-after`) a note goes to the candidate's team chat, the opening's AI interview tab
  shows a banner and a badge, and staff can watch the answers so far. The candidate is warned before their next
  question to answer in their own words.
- **A person ends it, not the AI.** *End interview now* (recruiters, `MANAGE_JOBS`) submits it with the answers so
  far; it is reviewed as usual, audited, and noted in the team chat. AGENTS.md: no code path rejects or moves a
  candidate from LLM output alone, and the check can be wrong.
- The final review gets the signals with each answer and says in `delivery` when several answers look read; it
  shouldn't credit polish that may not be the candidate's.

## Alternatives considered

- **Ending the interview automatically on a LIKELY verdict**: faster, but it would reject on the AI's word, against
  AGENTS.md, and fluent candidates would be hurt by false positives.
- **Gaze tracking in the browser** (face landmarks, eyes moving across a line of text): catches reading from a
  second screen or phone, which focus signals can't. It needs a face-landmark model downloaded to the
  candidate's browser and calibration; worth a later step if hidden questions and the reading check aren't
  enough.
- **Live follow-up questions from the AI**: hardest to game, but answers stop being comparable and each needs a
  model call mid-interview (ADR-0030 rejected it for the same reasons).

## Consequences

- Candidates can't see the next questions ahead, so they can't prepare them in another tab.
- One extra small model call per spoken answer (~20 per interview) while it runs.
- Interviews already in progress keep working: a question they already recorded counts as opened with one take.
- Reading from a second device can still get past focus signals; the transcript check and the videos remain the
  way to catch it, and gaze tracking is the next option.
