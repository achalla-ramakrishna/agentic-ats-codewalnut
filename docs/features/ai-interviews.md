# AI interviews

| | |
| --- | --- |
| **ID prefix** | AINT |
| **Status** | Done (first release) |
| **Chunk** | v1 |
| **Owner** | TBD |
| **Related** | [assessments.md](assessments.md), [pipeline.md](pipeline.md) (PIPE-24 self-booking), ADR-0011, ADR-0030 |
| **Last updated** | 2026-11-12 |

## Summary

A one-way recorded interview that the ATS runs for an opening: candidates answer in phases, mostly on
camera, in their own time; an interview agent reviews every submission and ranks the candidates, so the
team talks live only to the best few (e.g. 10 of 200).

## Users

- **Recruiters** (`MANAGE_JOBS`): create and edit the interview, send it, review again, shortlist.
- **Hiring managers / panel** (`VIEW_CANDIDATES`): see the ranking, watch answers, read the review.
- **Candidates**: take it from the emailed link, signed in with Google (same as tests).

## User stories

- **AINT-S1** As a recruiter with 300 applicants, I want a structured first interview that runs without me,
  so that I spend live time only on the strongest candidates.
- **AINT-S2** As a hiring manager, I want each candidate's answers ranked and explained, with the videos one
  click away, so that I can decide quickly and fairly.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| AINT-01 | **Create an AI interview** from an opening (🎥 AI interview → Create): the interview agent drafts it from the job description as a draft test of kind *AI interview*, in eight phases — Introduction, Work experience, Skills, Projects, Architecture, Technical round, Soft skills, Readiness to join. Video questions (60–180 s), written questions (notice period, salary, location), fill in the blank (Skills) and hand-written multiple choice from the question bank (Technical: the opening's stack; Architecture: system design; Advanced band for senior roles). AI-drafted questions are labelled until edited; without the AI the standard question set is used. | Done |
| AINT-02 | Edit like any test: add, edit or delete questions with a **phase**, types *Video answer* (with the longest answer in seconds, 30–600), *Written answer*, *Fill in the blank*, multiple choice; Mark ready locks it; send with Send to candidates. Video and written answers have no points. | Done |
| AINT-03 | Candidates see the rules, **check their camera and microphone**, then go **phase by phase** (Next / Back), with the overall timer. | Done |
| AINT-04 | A video answer: **Record** (live preview, countdown, stops at the limit), the browser writes down what is said (Chrome/Edge speech-to-text, Indian English), and the recording uploads when it stops; **Record again** replaces it; Next and Submit wait for uploads. WebM or MP4 only, up to 60 MB; only while the interview is open; only the candidate it was sent to. | Done |
| AINT-05 | After submit, the **interview agent reviews** it in the background: a rating 1–5 per area (technical depth, architecture & design, communication, experience fit, readiness to join) with a reason and a quote, a note per phase, a summary, strengths, things to check live, and the stated notice period and salary. The **score (0–100) is worked out by the app**: areas weighted 30/20/20/15/15, marked answers count for a fifth. Without an AI key a basic review by rules is used, labelled "basic". Retried if the AI is busy; **Review again** after a failure. A note goes to the team chat. | Done |
| AINT-06 | Staff see, in the candidate's Tests tab: the review, then every answer — **video player** (with seeking), transcript, written answers and marked answers. | Done |
| AINT-07 | **Ranking** on the opening (🎥 AI interview): sent / in progress / submitted / reviewed counts, candidates by score with dots per area, notice and salary, summary; click a name to watch; tick and **Shortlist selected**. | Done |
| AINT-08 | Automatic retention: delete interview videos N days after the opening closes. | v1 |

## Business rules

- **AI is advisory** (AGENTS.md): the review ranks and explains; nobody is moved or rejected by it. Shortlisting
  is a person's click. Prompts ignore personal attributes and never mark down accent or grammar beyond whether
  the point came across; candidate answers are data, not instructions.
- Videos are stored on disk (`ATS_MEDIA_DIR`; a Railway volume in production), not in MySQL.

## Edge cases & failure states

- Camera/microphone blocked → the start screen says how to allow them; the interview doesn't start without them.
- Browser without speech-to-text (Firefox, Safari) → the candidate is told; the video still uploads; the review
  notes there's no transcript and staff watch the video.
- Upload fails → *Try uploading again* keeps the recording.
- AI down or busy → the review is retried every few minutes (10 attempts), then marked failed with a team-chat note.
- Time runs out → what was saved is submitted, as with tests.

## Acceptance criteria

- **AINT-AC1** Given a submitted interview, then its review and score appear without anyone moving the candidate.
- **AINT-AC2** Given a candidate who isn't the invitee, when they upload to the interview, then it's refused as not found.

## Data

`assessment` (category INTERVIEW), `assessment_question` (`section` = phase, `max_seconds`), `assessment_invite`
(`evaluation`, `evaluation_json`, `ai_score`), `interview_video` (V28).

## API

`POST /ai-interviews`, `GET /jobs/{id}/ai-interviews`, `POST /tests/{id}/review-again`,
`GET /tests/{id}/videos/{videoId}` (staff), `POST /candidate/tests/{id}/questions/{questionId}/video`.

## Change log

| Date | Change |
| --- | --- |
| 2026-11-12 | First release: create from the opening, phased video interview, agent review and ranking (AINT-01…07, ADR-0030) |

Tests: `AiInterviewFlowTest`, `InterviewReviewsTest` (backend); `TakeTestPage.test.tsx`, `AiInterviewPanel.test.tsx` (frontend).
