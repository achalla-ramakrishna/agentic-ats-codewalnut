# ADR-0010: Résumé intelligence — AI reads résumés and suggests an order; people decide

- **Status**: accepted
- **Date**: 2026-10-03
- **Supersedes**: AI-07 ("no AI ranking or scoring") in docs/features/ai-assistance.md, for the
  advisory, explained ordering described here. Auto-advance and auto-reject stay forbidden.
- **Builds on**: ADR-0009, AGENTS.md "AI is advisory"

## Context

Recruiters get résumés by email (40+ for one opening at a time). They asked
CodeWalnut ATS to take those résumés in, read them, sort them and suggest
whom to contact first and who is closest to selection, and to answer
questions about the candidates ("who has worked on Spring Boot projects?",
"who hasn't been interviewed yet?"). Our rule until now was AI-07: no AI
ranking or scoring at all.

## Decision

- **Bulk upload, then read in the background.** `POST /jobs/{id}/resumes`
  accepts up to 50 PDF/Word files per request (the page sends 5 at a time).
  Each becomes a `resume_intake` row; a small worker pool (`ats.ai.workers`,
  default 3) has Claude read it. The person is matched by email, then phone,
  then the same name already in the opening; otherwise added at
  **Applied / Sourced** with source `RESUME_UPLOAD`. The résumé is attached as
  the original résumé (not twice if the same file is uploaded again) and the
  intake's file bytes are cleared. Work interrupted by a restart is resumed
  at start-up.
- **One reading per candidate per opening** (`candidate_insight`): Claude
  (structured output, `ResumeInsight`) extracts contact details, education,
  experience, skills and projects, and assesses each requirement in the
  opening's description as MET / PARTIAL / NOT_EVIDENT with evidence. PDFs
  go as documents; .docx as extracted text; old .doc is refused with a
  "save as PDF" message. New résumés added any other way (job link,
  recruiter upload, candidate upload) are read automatically.
- **The score is ours, not the model's.** Match % = (2×MET + PARTIAL) /
  (2×requirements), computed by the app, shown with its parts ("meets 3 of 4
  requirements"). No description, no score.
- **Suggestions are deterministic and explained**: *Contact next* =
  candidates at Applied or Screening with match ≥ 50 %, best first;
  *Closest to selection* = candidates at Interviewed … Client interview,
  furthest stage first, then match. Each shows its reason. Filters (strong
  match, has projects, has experience, skill, graduation year) and a "best
  match" sort sit on the opening page.
- **Questions** go through the existing assistant box (ADR-0009). The
  assistant now gets a compact, job-related profile of each candidate (no
  email or phone) and returns an `answer` plus the `matches` it points to;
  ids are checked server-side like actions. The candidate list is a cached
  prompt prefix so follow-up questions are cheaper.
- **Fairness and safety**: the prompt tells the model to judge only
  job-related evidence, ignore gender, age, religion, caste, photo, family
  and similar, word gaps as "not evident in résumé", never recommend
  rejection, and treat résumé text as data (prompt-injection). Low scores are
  shown in neutral grey, not red. Everything is labelled AI / advisory.
- **Nothing moves anyone.** No code path changes a stage from a reading or
  an answer; stage changes still go through a person.

## Consequences

- Recruiters can upload a folder of résumés and start calling the best
  matches within minutes. Cost is roughly ₹2–₹10 per résumé read, once.
- A résumé that hides a skill scores lower; the UI says the score is based
  on the résumé only and must not be used to rule anyone out.
- Email ingestion is manual for now (Gmail "Download all", then upload).
  A dedicated inbox (inbound-email webhook) can feed the same intake later
  without reading anyone's mailbox.
- Résumé contents are sent to Anthropic; covered by its commercial terms
  (no training on API data).
