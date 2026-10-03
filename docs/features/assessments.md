# Tests and coding assessments

| | |
| --- | --- |
| **ID prefix** | ASMT |
| **Status** | Built-in tests done (first release); provider integration later |
| **Chunk** | 5 |
| **Owner** | TBD |
| **Related** | [pipeline.md](pipeline.md) |
| **Last updated** | 2026-10-03 |

## Summary

A coding assessment is CodeWalnut's main screening signal, so it is a built-in
stage: send a test from a provider or a take-home assignment, pull back the
score, and advance or flag automatically.

## Users

Recruiters send and review; Hiring Managers and interviewers read results.

## User stories

- **ASMT-S1** As a Recruiter, I want to send the right test for the role in one
  click so that screening doesn't stall.
- **ASMT-S2** As a Recruiter, I want candidates above the threshold advanced
  automatically so that good people move fast.

## Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| ASMT-01 | Per job: assessment type (provider test or take-home), test id or assignment brief, pass threshold, deadline (days). | MVP |
| ASMT-02 | Provider integration (one of HackerRank / Codility / HackerEarth): send invite, receive score and report link via signed webhook. | MVP |
| ASMT-03 | Take-home: send brief and a submission link; candidate submits a GitHub repo URL; reviewer scores against a rubric. | MVP |
| ASMT-04 | Reminders at 24 h and 48 h before deadline; expired invites flagged. | MVP |
| ASMT-05 | Score ≥ threshold → auto-advance (configurable) or flag for review; below → flag, never auto-reject. | MVP |
| ASMT-06 | Result (score, max, percentile if given, report link) shown on the application and in the debrief. | MVP |
| ASMT-07 | Plagiarism / proctoring flags from the provider shown if available. | v1 |

### Built-in tests (shipped 2026-10-03, ADR-0011)

| ID | Requirement | Priority |
| --- | --- | --- |
| ASMT-08 | **Tests** page (`MANAGE_JOBS`): create a test (title, kind: aptitude / Java / Python / JavaScript / SQL / other, time limit 5–180 min, pass mark), add questions: one right answer, several right answers, or short answer, each with optional code, points and an explanation. | Done |
| ASMT-09 | **Draft with AI**: topic, level and count; drafted questions are labelled "AI draft — check it" until edited; marking ready asks for confirmation while unchecked drafts remain. Dev/demo use a built-in starter bank. | Done |
| ASMT-10 | Draft → **Mark ready** locks the questions; **Back to draft** only while nobody was sent it; **Duplicate** makes an editable copy; **Archive** hides it. | Done |
| ASMT-11 | **Send test** from the candidate drawer (`MANAGE_JOBS` + `MESSAGE_CANDIDATES`): pick a ready test, due in 1–7 days, optional note, by email and/or WhatsApp; the message with the link lands in the candidate chat. Needs the candidate's email; one open copy of the same test per candidate. | Done |
| ASMT-12 | The candidate opens the link, signs in with Google (same email), sees the rules and starts; the timer (server-enforced, 30 s grace) runs even if they close the page; answers save every few seconds; at zero, saved answers are submitted. Candidates never receive correct answers or their score. | Done |
| ASMT-13 | Scoring: all-or-nothing per question; short answers ignore case, spaces and quotes. Result = score / max, %, passed if ≥ pass mark. History notes "Test sent" and "Test submitted — 80%". | Done |
| ASMT-14 | Results: drawer (status, score, per-question answers for staff), opening Test column and **Passed a test** filter, AI suggestions (closest to selection orders by stage, then test, then match) and the AI assistant. | Done |
| ASMT-15 | Tests not started after 2 days are highlighted with **Remind**; expired ones can be reminded with 2 more days; staff can **Withdraw** a test. | Done |
| ASMT-16 | Code-writing questions run in a sandbox. | v1 |
| ASMT-18 | **Question bank** (Tests → Question bank, `MANAGE_JOBS`): questions by area, section, topic and difficulty, with pictures; filter, search, show answers, add your own (with a PNG/JPEG picture), edit, archive (ADR-0014). | Done |
| ASMT-19 | Built-in aptitude bank: 173 questions across Numerical ability (incl. data interpretation charts/tables), Logical reasoning (incl. clocks, Venn diagrams and non-verbal picture puzzles with picture options) and Verbal ability; 2 easy / 2 medium / 2 hard per generated topic. | Done |
| ASMT-20 | **AI drafts for the bank** wait in "Waiting for review" and are never used until a person approves or edits them; chart data from the AI is drawn by the app. | Done |
| ASMT-21 | **Build from bank**: presets (Quick screening, TCS NQT, Wipro NLTH, Cognizant GenC, Infosys) or a custom easy/medium/hard count per section; order easy → hard, by section, or shuffled; least-used questions first; shortages explained. Creates a draft test to check, then Mark ready and send as usual. | Done |
| ASMT-22 | **Add from the question bank** into any draft test. | Done |
| ASMT-23 | Candidates see section headings, pictures and picture options; results show the score per section. | Done |
| ASMT-26 | **Delete** a test only while nobody has started or submitted it; invites sent but not started are removed (their links stop working, with a warning first). Once taken, archive instead so results stay. Audited. | Done |
| ASMT-27 | **Built-in aptitude bank v2**: 26 topics × 50 questions (17 easy, 17 medium, 16 hard), answers from generators and solvers; a **topic guide** says what each topic covers with an example; the old 173-question bank is archived on start-up. | Done |
| ASMT-28 | **Build by topics**: tick topics, questions per topic, a difficulty mix and an order (easy → hard, hard → easy, topic by topic, shuffled), then create; per-level shortages explained. | Done |
| ASMT-24 | Banks for Java, Python, React, Angular, SQL and coding, per role. | Next |
| ASMT-25 | Per-section timers and optional negative marking. | v1 |
| ASMT-17 | Automatic reminders (needs a sender that works without a staff session, e.g. WhatsApp Business API or a shared mailbox). | v1 |

## Business rules

- Auto-reject on score is not allowed; a person decides.
- Webhooks are idempotent by provider event id.

## Edge cases & failure states

- Webhook never arrives → poll the provider daily for pending invites.
- Candidate asks for an extension → Recruiter extends deadline; audited.

## Acceptance criteria

- **ASMT-AC1** Given a score above threshold with auto-advance on, then the
  application moves to the next stage with a `StageEvent` by "system".
- **ASMT-AC2** Given a replayed webhook, then no duplicate result is stored.

## Data

`Assessment`, `AssessmentConfig` (per job).

## API (planned)

`POST /applications/{id}/assessments`, `GET /assessments/{id}`,
`POST /webhooks/{provider}`.

## Open questions

- [ ] Which assessment tool is used today, if any?

## Change log

| Date | Change |
| --- | --- |
| 2026-09-25 | Created from SPEC.md |
| 2026-10-03 | Aptitude bank v2 (26 topics × 50) with topic guide; build a test by topics, difficulty mix and order (ASMT-27, ASMT-28) |
| 2026-10-03 | Delete a test nobody has taken (ASMT-26) |
| 2026-10-03 | Question bank with app-drawn pictures, built-in aptitude bank, paper builder with presets, section scores (ASMT-18…ASMT-23, ADR-0014) |
| 2026-10-03 | Built-in tests: library, AI drafting, send, timed taking, auto-score, results, reminders (ASMT-08…ASMT-15, ADR-0011) |

Tests: `AssessmentFlowTest`, `QuestionBankFlowTest`, `AptitudeBankTest`, `ClaudeAssessmentDrafterTest` (backend); `TestsPage.test.tsx`, `QuestionBank.test.tsx`,
`TakeTestPage.test.tsx`, `TestsSection.test.tsx` (frontend).
