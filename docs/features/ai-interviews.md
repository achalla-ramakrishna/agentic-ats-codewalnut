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
| AINT-09 | **Senior Java set**: for a Java opening whose title or description says senior, lead, architect, principal or staff, the interview uses a hand-written set instead of a drafted one, written for the Senior Java Developer job description (Java 17, Spring Boot, REST/gRPC, MongoDB, Redis/Caffeine caching, Spring Security with JWT, JUnit 5/Mockito/Spock, CI/CD with SonarQube; airline shopping and booking): production scenarios (a service owned end to end, an incident, slow under load), JWT security, fare-search caching and booking design, a code review on video, code-reading and MongoDB multiple choice (checked by hand), fill in the blanks, testing and CI, working from vague requirements and with global teams, and readiness (notice up to 60 days, hybrid Bengaluru). Each spoken question carries **what a strong answer covers**: shown to staff next to the answer ("Look for"), given to the AI review as the team's expectations (other correct approaches still get credit), never shown to candidates. Drafted questions also carry these notes. | Done |
| AINT-10 | **Let them finish**: staff (`MANAGE_JOBS`) reopen a submitted AI interview for 10–240 minutes; saved answers are kept, the candidate uses the same link, and the review runs again on submit. Audited, with a team-chat note. A recording that fails to upload is kept on the candidate's screen and Next/Submit wait until it is uploaded or recorded again. | Done |
| AINT-11 | **Four question sets (A–D)** for senior Java openings, each covering the same skills with different questions (no question repeats across sets apart from the introduction and the notice/salary/location facts). On the opening, 🎥 AI interview → Question set → Set A–D or All four sets → Create; each is a draft titled "… — Set X" to check, Mark ready and send, so different candidates can get different papers. | Done |
| AINT-12 | **The team's questions in every set**: each senior Java set has the senior architect's 16 questions (ownership and constraints, a regretted decision, OOM, deadlocks, GC tuning, exception handling, a slow join query, caching failures, anti-patterns, monitoring, a notification system, the last item in stock, a screen loading a million records, and two coding questions: character frequency, and a money transfer with tests) plus 5–6 of the team's concept questions (HashMap vs ConcurrentHashMap, JVM memory, locks, CompletableFuture, Spring Boot startup, Actuator, graceful shutdown, configuration, secrets, filters vs interceptors, PUT vs PATCH, query vs path parameters, payment idempotency, correlation ID vs idempotency key, securing REST APIs, MongoDB embedding, compound indexes, collection scans and slow queries, authentication vs authorization, transactions), all 21 spread across the four sets; global exception handling is merged into the architect's question. Coding questions are typed in a code box (Tab indents; pastes are noted) and reviewed, not run. Time limit 120 minutes. **Preview questions** on the opening shows a set by phase, with answers and what to look for, before creating it. | Done |
| AINT-13 | **"AI interview: will you take it? (reply YES)"** email template: what the interview involves and what candidates need (about 2 hours, Chrome or Edge with camera and microphone, a quiet place, a Google account for their email); they reply YES and staff send the link with Send to candidates. Sent emails show as "AI interview asked" in the Workflow, and the candidate's outreach step becomes *AI interview asked*, so Today's emails no longer offers them a booking link. | Done |
| AINT-14 | An AI interview remembers the opening it was created for: **Send to candidates** opens with that opening already chosen (it can still be changed); Duplicate keeps it; interviews created earlier are linked by their title (V29). | Done |
| AINT-15 | **Simpler opening page**: tabs (Candidates · AI interview · Interview slots · Opening details) so each part opens at the top; one **+ Add candidates** menu (add one, spreadsheet, Naukri, résumés); a **Connect Gmail** banner before selecting; selecting people shows a bar that stays in view with **Send AI interview**, Email and WhatsApp; these open over the page; **Send AI interview** picks the opening's ready interview (set), days to finish, and email or copy-the-links; connecting Gmail keeps the selection and the open dialog. | Done |
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
| 2026-11-13 | Simpler opening page: tabs, add menu, Gmail banner, selection bar, Send AI interview from the list (AINT-15) |
| 2026-11-13 | Send to candidates starts at the interview's opening (AINT-14) |
| 2026-11-13 | "Will you take an AI interview?" email template; Workflow step (AINT-13) |
| 2026-11-13 | Architect's and team's questions in every set; Preview questions; code answers (AINT-12) |
| 2026-11-12 | Four senior Java question sets, A–D (AINT-11) |
| 2026-11-12 | Let them finish (reopen with saved answers); failed uploads hold Next (AINT-10) |
| 2026-11-12 | Senior Java question set, written for the Senior Java Developer job description; reviewer notes guide the AI review (AINT-09) |
| 2026-11-12 | First release: create from the opening, phased video interview, agent review and ranking (AINT-01…07, ADR-0030) |

Tests: `AiInterviewFlowTest`, `InterviewReviewsTest` (backend); `TakeTestPage.test.tsx`, `AiInterviewPanel.test.tsx` (frontend).
