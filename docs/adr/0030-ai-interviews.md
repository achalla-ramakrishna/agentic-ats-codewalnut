# ADR-0030: AI interviews — recorded answers, an agent's review, the app's score

- **Status**: accepted
- **Date**: 2026-11-12
- **Relates to**: docs/features/ai-interviews.md (AINT), ADR-0011 (built-in tests), ADR-0010 (advisory AI scores), ADR-0028 (self-booking)

## Context

300 people applied for one senior Java opening. The team can't interview them all live, but a résumé and a
test don't show how someone explains their work, designs, communicates, or when they can join. We want a
first interview that runs by itself and tells us whom to talk to.

## Decision

- **Build on tests, don't fork them.** An AI interview is a test of category `INTERVIEW`: same library, Mark
  ready, Send to candidates, Google sign-in, timer, reminders and results. Phases are the question's
  `section`; two new kinds, `VIDEO` and `WRITTEN`, are reviewed rather than marked (0 points).
- **The same questions for everyone**, drafted by the agent from the job description and edited by a person,
  so candidates can be compared. No live follow-ups (they would make answers incomparable and need a model
  call mid-interview). Multiple choice comes from the hand-written bank, not the model.
- **Transcripts in the browser.** Claude can't watch video, so it reads transcripts. Chrome/Edge's speech
  recognition (en-IN) writes them while the candidate records: free, no new vendor, no candidate audio sent to
  another service by us. Accuracy is good, not perfect; the prompt says so and judges meaning, and staff watch
  the video. A transcription service can replace it later without changing the review.
- **Videos on disk** under `ATS_MEDIA_DIR` (a Railway volume in production), keyed by the app; MySQL keeps only
  the metadata (`interview_video`). ~350 kbps WebM keeps 300 interviews around 10 GB. Staff stream them through
  an authorised endpoint with byte ranges.
- **The agent rates; the app scores.** Claude returns a 1–5 rating per area with a reason and quote (structured
  output); the app turns them into 0–100 with fixed weights (technical 30, architecture 20, communication 20,
  experience 15, readiness 15) plus a fifth for marked answers. Without a key, a basic rules review (amount said,
  job keywords, marked answers) is used and labelled "basic, not AI".
- **Advisory only**: nothing moves a candidate; shortlisting is a person's click. Fairness rules in the prompt
  (no personal attributes, no marking down accent), answers treated as data.
- Reviews run after the submit commits on a small worker pool, retried every 5 minutes (10 attempts) and after
  a restart, like code grading (ADR-0016).

## Consequences

- One link, one ranking: the team watches the top 10–20 and books live calls with the existing slots (ADR-0028).
- Candidates need Chrome or Edge, a camera and a microphone; others can still record but get no transcript.
- Videos are personal data on a volume: they must be deleted with the candidate's data, and a retention rule for
  videos is still to do (AINT-08). If `ATS_MEDIA_DIR` isn't on a volume, videos are lost on redeploy.
